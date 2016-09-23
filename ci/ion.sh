#!/bin/bash

# ignore errors for now
set +e

pushd `dirname $0`/.. > /dev/null
root=$(pwd -P)
popd > /dev/null

[ -z "$IONCHANNEL_SECRET_KEY" ] && { echo "IONCHANNEL_SECRET_KEY not set" >&2; exit 1; }
[ -z "$IONCHANNEL_ENDPOINT_URL" ] && IONCHANNEL_ENDPOINT_URL=https://api.private.ionchannel.io

os=$(uname -s | tr '[:upper:]' '[:lower:]')
pomfile=$root/pom.xml
archive=ion-connect-latest.tar.gz

mkdir -p $root/tmp/bin

# Install jq?
if ! type jq >/dev/null 2>&1; then

  if [ "$os" = "linux" ]; then
    uname -m | grep -q 64 \
      && curl -o $root/tmp/bin/jq -O https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 \
      || curl -o $root/tmp/bin/jq -O https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux32
  elif [ "$os" = "darwin" ]; then
    curl -o $root/tmp/bin/jq -O https://github.com/stedolan/jq/releases/download/jq-1.5/jq-osx-amd64
  else
    echo "jq install on $os not supported by this script; please visit https://stedolan.github.io/jq/download/" >&2
    exit 1
  fi

  chmod 700 $root/tmp/bin/jq
  jqcmd=$root/tmp/bin/jq
else
  jqcmd=jq
fi

# Install ion-connect?
if ! type ion-connect > /dev/null 2>&1; then
  curl -o $root/tmp/$archive -O https://s3.amazonaws.com/public.ionchannel.io/files/ion-connect/$archive
  tar -C $root/tmp -xzf $root/tmp/$archive
  ioncmd=$root/tmp/ion-connect/$os/bin/ion-connect
else
  ioncmd=ion-connect
fi

$ioncmd dependency resolve-dependencies-in-file --flatten --type maven $pomfile

#$ioncmd vulnerability get-vulnerabilities-for-list \
#  $($ioncmd dependency resolve-dependencies-in-file --flatten --type maven $pomfile \
#      | $jqcmd -c .dependencies)

rm -rf $root/tmp

# ignore errors for now
exit 0
