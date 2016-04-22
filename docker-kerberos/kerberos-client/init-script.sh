#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin
sbt test

#echo "RunOnly"
#sbt <<<"testOnly pt.tecnico.dsi.kadmin.PrincipalSpec"
#echo "After run"