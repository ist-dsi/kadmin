#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt <<<"testOnly pt.tecnico.dsi.kadmin.KeytabSpec"
sbt clean coverage test coverageReport codacyCoverage
#sbt clean test

# If we are not in CI we chmod the current directory back to its original owner (instead of root).
if [[ -z $CI ]] || [[ "$CI" == "false" ]]; then
  chown -R $HOST_USER_ID:$HOST_USER_ID .
fi