#!/usr/bin/env bash

# Update repository to fetch latest OpenJDK
sudo add-apt-repository -y ppa:openjdk-r/ppa
sudo apt-get -y update

# Install required packages
sudo apt-get -y install openjdk-8-jdk maven

# Build the Gateway application
cd /vagrant/gateway
mvn clean package

# Updating hosts
echo "192.168.33.12  kafka.dev" >> /etc/hosts

# Add an Upstart job to run our script upon machine boot
chmod 777 /vagrant/gateway/config/spring-start.sh
cp /vagrant/gateway/config/gateway.conf /etc/init/gateway.conf

# Start the Spring application
cd /vagrant/gateway/config
./spring-start.sh
