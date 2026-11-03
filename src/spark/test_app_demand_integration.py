"""Integration fixture for the disposable GitHub Actions Cassandra and MinIO."""
import json
import math
import uuid
from datetime import datetime, timezone

import app_demand_forecast as forecast
from kpi_analytics import get_spark_session
from pyspark.sql import functions as F


def main():
    spark = get_spark_session(packages="")
    spark.conf.set("spark.sql.session.timeZone", "UTC")
    fixture_id = uuid.uuid4().hex
    forecast.CITY = f"forecast-test-{fixture_id}"
    forecast.MODEL_ROOT += f"/verification/{fixture_id}"
    cutoff = forecast.hour_number(datetime.now(timezone.utc))
    # Exactly weekly, so the seasonal baseline has zero error on the holdout.
    counts = {
        hour: {zone: (hour % forecast.WEEK + zone) % 11 for zone in forecast.ZONES}
        for hour in range(cutoff - forecast.HISTORY, cutoff)
    }

    def published():
        return forecast.read(spark, "zone_demand_forecast").where(
            F.col("city") == forecast.CITY
        ).collect()

    try:
        forecast.publish(spark, {}, cutoff)
        assert not published()
        snapshots = [
            (forecast.CITY, forecast.instant(target), forecast.instant(target - 1),
             forecast.FEATURE_VERSION,
             json.dumps([row[1:-1] for row in forecast.feature_grid(counts, target, target + 1)]))
            for target in range(cutoff - forecast.HISTORY + forecast.WEEK, cutoff)
        ]
        forecast.write(spark.createDataFrame(
            snapshots,
            "city string, target_hour timestamp, observed_at timestamp, feature_version string, features_json string",
        ), "demand_forecast_features")
        forecast.train(spark, counts, cutoff)
        active = forecast.read(spark, "demand_forecast_model").where(
            F.col("city") == forecast.CITY
        ).first()
        assert active.version == forecast.BASELINE_VERSION
        forecast.publish(spark, counts, cutoff)
        forecast.publish(spark, counts, cutoff)
        rows = published()
        assert len(rows) == 1
        row = rows[0]
        assert forecast.hour_number(row.target_hour) == cutoff + 1
        assert row.source == "seasonal_baseline"
        assert row.model_version == forecast.BASELINE_VERSION
        assert row.predicted_requests == counts[cutoff + 1 - forecast.WEEK]

        counts[cutoff + 1 - forecast.WEEK][1] = math.nan
        try:
            forecast.publish(spark, counts, cutoff)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid forecast passed validation")
        assert published() == rows
        print("Temporal evaluation, complete forecast publication, retry and failed-run preservation passed")
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
