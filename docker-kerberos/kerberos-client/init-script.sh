#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt <<<"testOnly pt.tecnico.dsi.kadmin.TicketsSpec"
sbt coverage test
sbt coverageReport
sbt coverageAggregate
sbt codacyCoverage