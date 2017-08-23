#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt "testOnly *Settings*"
#sbt "testOnly *Privileges*"
#sbt "testOnly *Policy*"
#sbt "testOnly *Principal*"
#sbt "testOnly *Privileges* *Policy* *Principal*"
#sbt "testOnly *Tickets*"
#sbt "testOnly *Password*"
#sbt "testOnly *Keytab*"
sbt clean coverage test coverageReport

TEST_EXIT_CODE=$?

if [[ -z $CI ]] || [[ "$CI" == "false" ]]; then
  # If we are not in CI we chown the current directory back to its original owner (instead of root).
  chown -R $HOST_USER_ID:$HOST_USER_ID . /root/.sbt /root/.ivy2 /root/.coursier
#else
  #sbt codacyCoverage
fi

exit $TEST_EXIT_CODE