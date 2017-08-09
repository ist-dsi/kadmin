#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt <<<"testOnly *Privileges*"
#sbt <<<"testOnly *Principal*"
#sbt <<<"testOnly *Policy*"
#sbt <<<"testOnly *Tickets*"
#sbt <<<"testOnly *Password*"
sbt clean coverage test coverageReport

if [[ -z $CI ]] || [[ "$CI" == "false" ]]; then
  # If we are not in CI we chown the current directory back to its original owner (instead of root).
  chown -R $HOST_USER_ID:$HOST_USER_ID . /root/.sbt /root/.ivy2 /root/.coursier
else
  sbt codacyCoverage
fi