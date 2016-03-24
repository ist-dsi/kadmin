#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/kadmin
sbt test