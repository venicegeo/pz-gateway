#!/usr/bin/env bash
cd /usr/bin
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic Request-Job-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic Create-Job-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic Abort-Job-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic Update-Job-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic repeat-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic access-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic execute-service-$SPACE
./kafka-topics.sh --zookeeper $ZOOKEEPER --create --topic ingest-$SPACE
