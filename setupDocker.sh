#!/bin/bash

# Install the latest version of docker-compose
curl -L https://github.com/docker/compose/releases/download/1.6.2/docker-compose-linux-x86_64 > docker-compose
chmod +x docker-compose
mv docker-compose /usr/local/bin

# Install the latest version of docker
apt-get -y purge docker
apt-get -y install linux-image-extra-`uname -r` apparmor libsystemd-journal0
curl -L https://apt.dockerproject.org/repo/pool/main/d/docker-engine/docker-engine_1.10.3-0~trusty_amd64.deb > docker-engine.deb
cat /etc/default/docker
dpkg --force-confold -i docker-engine.deb
service docker start
docker run hello-world