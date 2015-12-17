#!/usr/bin/env bash

sudo add-apt-repository ppa:openjdk-r/ppa
sudo apt-get update
sudo apt-get install openjdk-8-jdk
sudo apt-get install maven
cd /vagrant/gateway
mvn clean spring-boot:run