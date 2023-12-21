# How to use HDFS/Spark

## Installation

### Base Images

```sh
docker pull bde2020/hadoop-namenode:1.1.0-hadoop2.8-java8
docker pull bde2020/hadoop-datanode:1.1.0-hadoop2.8-java8
docker pull bde2020/spark-base:2.1.0-hadoop2.8-hive-java8
docker pull bde2020/spark-master:2.1.0-hadoop2.8-hive-java8
docker pull bde2020/spark-worker:2.1.0-hadoop2.8-hive-java8
docker pull bde2020/spark-notebook:2.1.0-hadoop2.8-hive
docker pull bde2020/hdfs-filebrowser:3.11
```

### Docker Compose File

To start an HDFS/Spark Workbench, run:

```sh
docker-compose up -d
```

## Interfaces

- Namenode: http://localhost:50070
- Datanode: http://localhost:50075
- Spark-master: http://localhost:8080
- Spark-notebook: http://localhost:9001
- Hue (HDFS Filebrowser): http://localhost:8088/home

# Recommendation Spark Application

Jar file is packaged under the dataset directory.

- Compiled with scala 2.11.11
- For Spark 2.1.0

## How to run (using Makefile)

### Start the ingestion

```
make ingest
```

### Create the Fat-JAR File

```
make jar
```

### Start the prediction

```
make prediction
```

### Start Cleaning the output

```
make clean
```
