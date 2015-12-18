#!/usr/bin/env bash

lxc-create --template download --name kerberos -- --dist debian --release jessie --arch amd64

cat /var/lib/lxc/kerberos/config

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp

ls /var/lib/lxc/kerberos/rootfs/tmp

chroot /var/lib/lxc/kerberos/rootfs /tmp/configureContainer.sh

lxc-start --name kerberos