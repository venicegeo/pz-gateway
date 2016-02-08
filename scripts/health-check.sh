#!/bin/bash -ex

pushd `dirname $0` > /dev/null
base=$(pwd -P)
popd > /dev/null

# Gather some data about the repo
source $base/vars.sh

# Send a blank request to the Gateway. 400 is correct, here, because we're not posting any data. This
# just verifies that Spring is up and running, and is able to send the appropriate error response from
# the service.
[ `curl -s -o /dev/null -w "%{http_code}" http://pz-gateway.cf.piazzageo.io/job --data {}` = 400 ]
