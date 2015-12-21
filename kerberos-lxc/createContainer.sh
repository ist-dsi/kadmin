#!/usr/bin/env bash

lxc-create --template download --name kerberos -- --dist debian --release jessie --arch amd64

echo -e "\n\nLXC Kerberos Configuration:"
cat /var/lib/lxc/kerberos/config
echo -e "\n\n"

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp/
chroot /var/lib/lxc/kerberos/rootfs /tmp/configureContainer.sh

echo -e "\n\nStarting the Kerberos container"
lxc-start --name kerberos --daemon

echo -e "\nSleeping for 1 minute to allow the container to start"
sleep 60

tail /var/lib/lxc/kerberos/rootfs/var/log/krb5kdc.log
tail /var/lib/lxc/kerberos/rootfs/var/log/kadmin.log

echo -e "\nKerberos fully operational"