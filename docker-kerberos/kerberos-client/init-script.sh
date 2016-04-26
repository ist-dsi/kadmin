#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin
sbt coverage test
sbt coverageReport
sbt coverageAggregate
sbt codacyCoverage

#echo "RunOnly"
#sbt <<<"testOnly pt.tecnico.dsi.kadmin.PrincipalSpec"
#echo "After run"