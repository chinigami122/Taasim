"""Train and publish demand forecasts from observed TaaSim app bookings."""
import argparse
import json
import math
import uuid
from datetime import datetime, timedelta, timezone

CITY = "casablanca"
ZONES = range(1, 17)
DAY = 24
WEEK = DAY * 7
HISTORY = DAY * 42
MIN_COVERAGE = 0.8
MIN_IMPROVEMENT = 0.05
BASELINE_VERSION = "weekly-lag-v1"
FEATURE_VERSION = "app-zone-hour-v1"
MODEL_ROOT = "s3a://ml-store/models/app-demand"
FEATURES = ["zone", "hour_of_day", "day_of_week", "lag_day", "lag_week", "recent_mean"]


def hour_number(timestamp):
    return math.floor((timestamp if timestamp.tzinfo else timestamp.replace(tzinfo=timezone.utc)).timestamp() / 3600)


def instant(hour):
    return datetime.fromtimestamp(hour * 3600, timezone.utc)


def observed_counts(rows):
    # Slice 26 emits zeros for scanned hours even before the first app ride.
    # Require a complete published hour and anchor coverage at observed traffic.
    hourly = {}
    for row in rows:
        hourly.setdefault(hour_number(row.hour), {})[row.zone] = (row["count"], row.processed_at)
    complete = {
        hour: {zone: count for zone, (count, _) in values.items()}
        for hour, values in hourly.items()
        if set(values) == set(ZONES)
        and len({stamp for _, stamp in values.values()}) == 1
        and all(count >= 0 for count, _ in values.values())
    }
    first = min((hour for hour, values in complete.items() if any(values.values())), default=None)
    return {hour: values for hour, values in complete.items() if first is not None and hour >= first}


def feature_row(counts, target, zone):
    # At the start of hour T-1, T-2 is the latest completed hour.
    day = counts.get(target - DAY, {}).get(zone)
    week = counts.get(target - WEEK, {}).get(zone)
    recent = [
        counts[hour][zone] for hour in range(target - DAY - 1, target - 1)
        if hour in counts
    ]
    if day is None or week is None or len(recent) < math.ceil(DAY * MIN_COVERAGE):
        return None
    time = instant(target)
    return (zone, time.hour, time.weekday(), float(day), float(week), sum(recent) / len(recent))


def feature_grid(counts, start, end):
    for target in range(start, end):
        for zone in ZONES:
            features = feature_row(counts, target, zone)
            if features is not None:
                yield (target, *features, None)


def read(spark, table):
    return spark.read.format("org.apache.spark.sql.cassandra").options(
        keyspace="taasim", table=table,
    ).load()


def write(frame, table):
    frame.write.format("org.apache.spark.sql.cassandra").options(
        keyspace="taasim", table=table,
    ).mode("append").save()


def frame_for(spark, rows):
    schema = (
        "target long, zone int, hour_of_day int, day_of_week int, "
        "lag_day double, lag_week double, recent_mean double, demand double"
    )
    return spark.createDataFrame(list(rows), schema)


def seasonal_baseline(counts, target):
    coverage = sum(hour in counts for hour in range(target - WEEK, target - 1)) / (WEEK - 1)
    return counts.get(target - WEEK) if coverage >= MIN_COVERAGE else None


def labeled_snapshots(snapshots, counts, start, end):
    for snapshot in snapshots:
        target = hour_number(snapshot.target_hour)
        if (start <= target < end and hour_number(snapshot.observed_at) == target - 1
                and snapshot.feature_version == FEATURE_VERSION and target in counts):
            for features in json.loads(snapshot.features_json):
                yield (target, *features, float(counts[target][features[0]]))


def train(spark, counts, cutoff):
    from pyspark.ml import Pipeline
    from pyspark.ml.feature import VectorAssembler
    from pyspark.ml.regression import GBTRegressor
    from pyspark.sql import functions as F
    from ml_demand_forecasting_week6 import GBT_MAX_DEPTH, GBT_MAX_ITER, evaluate_model

    split = cutoff - WEEK
    start = cutoff - HISTORY + WEEK
    snapshots = read(spark, "demand_forecast_features").where(
        (F.col("city") == CITY)
        & (F.col("target_hour") >= instant(start))
        & (F.col("target_hour") < instant(cutoff))
    ).collect()
    rows = list(labeled_snapshots(snapshots, counts, start, cutoff))
    training = [row for row in rows if row[0] < split]
    holdout = [row for row in rows if row[0] >= split]
    coverage = {
        str(zone): {
            "train": sum(row[1] == zone for row in training) / (split - start),
            "holdout": sum(row[1] == zone for row in holdout) / WEEK,
        }
        for zone in ZONES
    }
    version = f"{instant(cutoff):%Y%m%dT%H}-{uuid.uuid4().hex}"
    report = {
        "version": version, "source": "live_feature_snapshots_and_app_ride_requests_hourly",
        "features": FEATURE_VERSION,
        "label_start": instant(start).isoformat(), "holdout_start": instant(split).isoformat(),
        "label_end": instant(cutoff).isoformat(), "coverage": coverage,
        "minimum_coverage": MIN_COVERAGE, "minimum_rmse_improvement": MIN_IMPROVEMENT,
        "promoted": False,
    }
    if all(min(zone.values()) >= MIN_COVERAGE for zone in coverage.values()):
        pipeline = Pipeline(stages=[
            VectorAssembler(inputCols=FEATURES, outputCol="features"),
            GBTRegressor(labelCol="demand", maxDepth=GBT_MAX_DEPTH, maxIter=GBT_MAX_ITER, seed=27),
        ])
        training_frame = frame_for(spark, training)
        test = frame_for(spark, holdout)
        # Keep the exact features and labels used for evaluation even if the
        # upstream KPI table is later corrected by a backfill.
        training_frame.unionByName(test).write.parquet(f"{MODEL_ROOT}/{version}/features")
        model = pipeline.fit(training_frame)
        predictions = model.transform(test).withColumn("prediction", F.greatest(F.lit(0.0), F.col("prediction")))
        rmse, mae = evaluate_model(predictions)
        baseline_rmse, baseline_mae = evaluate_model(test, pred_col="lag_week")
        report.update(rmse=rmse, mae=mae, baseline_rmse=baseline_rmse, baseline_mae=baseline_mae)
        report["promoted"] = (
            baseline_rmse > 0 and rmse <= baseline_rmse * (1 - MIN_IMPROVEMENT)
            and mae <= baseline_mae
        )
        report["status"] = "promoted" if report["promoted"] else "baseline"
        model.write().save(f"{MODEL_ROOT}/{version}/model")
    else:
        report["status"] = "insufficient_history"

    spark.createDataFrame([(json.dumps(report),)], "value string").write.text(
        f"{MODEL_ROOT}/{version}/evaluation"
    )
    if report["status"] != "insufficient_history":
        # A completed evaluation chooses the model or baseline for future runs.
        # An insufficient retraining attempt leaves the existing choice intact.
        write(spark.createDataFrame(
            [(CITY, version if report["promoted"] else BASELINE_VERSION, instant(cutoff))],
            "city string, version string, trained_through timestamp",
        ), "demand_forecast_model")
    print(json.dumps(report), flush=True)


def publish(spark, counts, cutoff):
    from pyspark.ml import PipelineModel
    from pyspark.sql import functions as F

    target = cutoff + 1
    # Require a mostly observed week and a seasonal value for every zone.
    baseline = seasonal_baseline(counts, target)
    if baseline is None:
        print(f"Forecast unavailable for {instant(target).isoformat()}: insufficient app history", flush=True)
        return
    version = BASELINE_VERSION
    source = "seasonal_baseline"
    predictions = {zone: float(baseline[zone]) for zone in ZONES}
    features = list(feature_grid(counts, target, target + 1))
    observed_at = datetime.now(timezone.utc)
    if len(features) == len(ZONES) and hour_number(observed_at) == cutoff:
        write(spark.createDataFrame(
            [(CITY, instant(target), observed_at, FEATURE_VERSION,
              json.dumps([row[1:-1] for row in features]))],
            "city string, target_hour timestamp, observed_at timestamp, feature_version string, features_json string",
        ), "demand_forecast_features")
    active = read(spark, "demand_forecast_model").where(
        (F.col("city") == CITY) & (F.col("trained_through") <= instant(cutoff))
    ).orderBy(F.col("trained_through").desc()).first()
    if active and active.version != BASELINE_VERSION and len(features) == len(ZONES):
        model = PipelineModel.load(f"{MODEL_ROOT}/{active.version}/model")
        predictions = {
            row.zone: max(0.0, row.prediction) if math.isfinite(row.prediction) else row.prediction
            for row in model.transform(frame_for(spark, features)).select("zone", "prediction").collect()
        }
        source, version = "gbt", active.version

    if set(predictions) != set(ZONES) or not all(math.isfinite(value) and value >= 0 for value in predictions.values()):
        raise ValueError("Forecast must contain finite nonnegative predictions for all zones")
    # A frozen map stores all zones in one cell. The complete run is one row
    # mutation and no partial write can become visible.
    write(spark.createDataFrame(
        [(CITY, instant(target), datetime.now(timezone.utc), source, version, predictions)],
        "city string, target_hour timestamp, generated_at timestamp, source string, "
        "model_version string, predicted_requests map<int,double>",
    ), "zone_demand_forecast")
    print(f"Published {source} forecast for {instant(target).isoformat()}", flush=True)


def main():
    from pyspark.sql import functions as F
    from kpi_analytics import get_spark_session

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["train", "forecast"])
    args = parser.parse_args()
    cutoff = hour_number(datetime.now(timezone.utc))
    spark = get_spark_session(packages="")
    spark.conf.set("spark.sql.session.timeZone", "UTC")
    try:
        rows = read(spark, "app_ride_requests_hourly").where(
            (F.col("city") == CITY)
            & F.col("day").between(instant(cutoff - HISTORY).date(), instant(cutoff - 1).date())
            & (F.col("hour") >= instant(cutoff - HISTORY))
            & (F.col("hour") < instant(cutoff))
            & F.col("zone").between(1, 16)
        ).select("hour", "zone", "count", "processed_at").collect()
        counts = observed_counts(rows)
        (train if args.mode == "train" else publish)(spark, counts, cutoff)
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
