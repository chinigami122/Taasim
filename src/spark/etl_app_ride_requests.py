"""Rebuild absolute hourly booking counts from the archived app event stream."""
import argparse
from datetime import datetime, timedelta, timezone

from pyspark.sql import Window, functions as F, types as T
from kpi_analytics import get_spark_session

ARCHIVE = "s3a://kafka-archive/raw.trips/"
CURATED = "s3a://curated/app-ride-requests"
SCHEMA = T.StructType([
    T.StructField("trip_id", T.StringType()),
    T.StructField("rider_id", T.StringType()),
    T.StructField("requested_at", T.LongType()),
    T.StructField("origin_zone", T.IntegerType()),
])
UUID = "(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$"


def run(spark, start, end):
    snapshot = f"{start:%Y%m%d%H}-{end:%Y%m%d%H}"
    # Object keys contain ingestion time. Scan across those partitions so a
    # late arrival can still update the hour in which the ride was requested.
    raw = spark.read.option("recursiveFileLookup", "true").text(ARCHIVE)
    parsed = raw.select("value", F.from_json("value", SCHEMA).alias("event")).select("value", "event.*")
    valid = F.coalesce(
        F.col("trip_id").rlike(UUID)
        & F.col("requested_at").between(946684800000, 4102444800000),
        F.lit(False),
    )
    # Quarantine fingerprints rather than copying raw rider identifiers.
    parsed.where(~valid).select(F.sha2("value", 256).alias("event_hash")).distinct().write.mode(
        "overwrite"
    ).parquet(f"{CURATED}-quarantine/{snapshot}")

    # App riders are auth UUIDs. Legacy simulators use IDs such as rider_1234.
    events = parsed.where(valid & F.col("rider_id").rlike(UUID)).select(
        F.lower("trip_id").alias("trip_id"),
        F.timestamp_millis("requested_at").alias("requested_at"),
        F.when(F.col("origin_zone").between(1, 16), F.col("origin_zone")).otherwise(0).alias("zone"),
    )
    # Choose the earliest request, with a stable tie-break for conflicting copies.
    order = Window.partitionBy("trip_id").orderBy("requested_at", "zone")
    events = events.withColumn("rank", F.row_number().over(order)).where("rank = 1").drop("rank")
    events = events.where((F.col("requested_at") >= start) & (F.col("requested_at") < end)).withColumn(
        "hour", F.date_trunc("hour", "requested_at")
    )
    # Each bounded run gets its own curated snapshot so overlapping backfills
    # do not erase partitions belonging to a different requested range.
    events.withColumn("day", F.to_date("hour")).write.mode("overwrite").partitionBy("day").parquet(
        f"{CURATED}/runs/{snapshot}"
    )
    counts = events.groupBy("hour", "zone").count()
    hours = int((end - start).total_seconds() // 3600)
    grid = spark.range(hours).select(
        F.timestamp_seconds(F.lit(int(start.timestamp())) + F.col("id") * 3600).alias("hour")
    ).crossJoin(spark.range(17).select(F.col("id").cast("int").alias("zone")))
    rows = grid.join(counts, ["hour", "zone"], "left").fillna(0, ["count"]).select(
        F.lit("casablanca").alias("city"), F.to_date("hour").alias("day"), "hour", "zone",
        "count", F.lit(datetime.now(timezone.utc)).alias("processed_at"),
    )
    rows.write.format("org.apache.spark.sql.cassandra").options(
        keyspace="taasim", table="app_ride_requests_hourly",
    ).mode("append").save()
    print(f"Published ride request KPI for {start.isoformat()} through {end.isoformat()}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from", dest="start", type=parse_timestamp)
    parser.add_argument("--to", dest="end", type=parse_timestamp)
    args = parser.parse_args()
    if (args.start is None) != (args.end is None):
        parser.error("pass both --from and --to")
    end = args.end or datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start = args.start or end - timedelta(hours=48)
    if start.tzinfo is None or end.tzinfo is None:
        parser.error("use timezone-aware timestamps")
    start, end = start.astimezone(timezone.utc), end.astimezone(timezone.utc)
    if any(value.minute or value.second or value.microsecond for value in (start, end)):
        parser.error("use whole UTC hour boundaries")
    if not timedelta(hours=1) <= end - start <= timedelta(days=31):
        parser.error("use a range between 1 hour and 31 days")
    if end > datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0) + timedelta(hours=1):
        parser.error("the range cannot include future hours")
    spark = get_spark_session(packages="")
    spark.conf.set("spark.sql.session.timeZone", "UTC")
    try:
        run(spark, start, end)
    finally:
        spark.stop()


def parse_timestamp(value):
    # The Spark image uses Python 3.8, which requires an explicit UTC offset.
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)


if __name__ == "__main__":
    main()
