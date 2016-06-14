#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin

#sbt <<<"testOnly pt.tecnico.dsi.kadmin.ChangePasswordSpec"
sbt clean coverage test coverageReport codacyCoverage