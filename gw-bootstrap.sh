#!/usr/bin/env bash

# Update repository to fetch latest OpenJDK
sudo add-apt-repository -y ppa:openjdk-r/ppa
sudo apt-get -y update

# Install required packages
sudo apt-get -y install openjdk-8-jdk maven

# Build the Gateway application
cd /vagrant/gateway
mvn clean package

# Run the Gateway application
java -jar target/piazza-gateway*.jar