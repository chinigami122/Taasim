#!/bin/sh
set -eu

exec /opt/spark/bin/spark-submit \
  --master 'local[2]' \
  --conf spark.jars.ivy=/tmp/taasim-ivy \
  --conf spark.jars.ivySettings=/jobs/ivysettings.xml \
  --packages org.apache.hadoop:hadoop-aws:3.3.4,com.datastax.spark:spark-cassandra-connector_2.12:3.5.1 \
  /jobs/etl_app_ride_requests.py "$@"
