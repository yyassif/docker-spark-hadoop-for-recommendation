DOCKER_NETWORK = docker-spark-hadoop_default
JAR_FILEPATH = /data/Recommender-assembly-1.0.0.jar
CLASS_TO_RUN = org.recommender.Recommender
DATA_CLASSES = all_beauty amazon_fashion automotive cell_phones_and_accessories electronics movies_and_tv sports_and_outdoors
ALL_DATA_CLASSES := all_beauty amazon_fashion automotive cell_phones_and_accessories electronics movies_and_tv sports_and_outdoors

prepare-raw-dataset:
	python raw-dataset/encode-generate-dataset.py "$(CURDIR)"

ingest-hdfs:
	docker exec -it namenode hadoop fs -mkdir -p /input/
	docker exec -it namenode hadoop fs -mkdir -p /output/
	for CLASS in $(ALL_DATA_CLASSES); do \
		docker exec -it namenode hadoop fs -copyFromLocal -f /data/$$CLASS /input/; \
	done
	docker exec -it namenode hadoop fs -ls /input

jar:
	cd RecommendationApp && sbt assembly && cp target/scala-2.11/Recommender-assembly-1.0.0.jar ../jarfile

prediction:
	docker run --rm -it --network ${DOCKER_NETWORK} --env-file ./hadoop.env --ulimit nofile=65536:65536 -e SPARK_MASTER=spark://spark-master:7077 --volume $(shell pwd)/jarfile:/data bde2020/spark-base:2.1.0-hadoop2.8-hive-java8 /spark/bin/spark-submit --executor-memory 8G --driver-memory 8G --master spark://spark-master:7077 ${JAR_FILEPATH} ${DATA_CLASSES}

prediction-result:
	mkdir -p result
	for CLASS in $(ALL_DATA_CLASSES); do \
		docker exec -it namenode hadoop fs -cat "/output/$$CLASS/*" > "result/$$CLASS.txt"; \
	done

clean-output:
	docker exec -it namenode hadoop fs -rm -r /output/*

clean-input:
	docker exec -it namenode hadoop fs -rm -r /input/*