#!/usr/bin/env bash

lxc-create --template download --name kerberos -- --dist debian --release jessie --arch amd64

echo "\n\nLXC Kerberos Configuration:"
cat /var/lib/lxc/kerberos/config
echo "\n\n"

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp
chroot /var/lib/lxc/kerberos/rootfs /tmp/configureContainer.sh

echo "\n\nStating the Kerberos container"
lxc-start --name kerberos