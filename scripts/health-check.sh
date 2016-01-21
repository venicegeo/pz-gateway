#!/bin/bash -ex

pushd `dirname $0` > /dev/null
base=$(pwd -P)
popd > /dev/null

# Gather some data about the repo
source $base/vars.sh

# Send a blank request to the Gateway. 
[ `curl -s -o /dev/null -w "%{http_code}" http://pz-gateway.cf.piazzageo.io/job --data {}` = 200 ]
