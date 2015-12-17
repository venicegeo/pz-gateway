#!/usr/bin/env bash

# Update repository to fetch latest OpenJDK
sudo add-apt-repository -y ppa:openjdk-r/ppa
sudo apt-get -y update

# Install required packages
sudo apt-get -y install openjdk-8-jdk maven tomcat7

# Ensure Tomcat starts with each bootup
sudo update-rc.d tomcat7 defaults

# Build the Gateway application
cd /vagrant/gateway
mvn package

# Copy the Gateway WAR file to the Tomcat /webapps directory
cp target/piazza-gateway*.war /var/lib/tomcat7/webapps/gateway.war

# Start the Tomcat server
sudo service tomcat7 start
