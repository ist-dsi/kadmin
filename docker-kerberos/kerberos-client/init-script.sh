#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt <<<"testOnly pt.tecnico.dsi.kadmin.KeytabSpec"
sbt clean coverage test coverageReport codacyCoverage
#sbt clean test