#!/usr/bin/env bash

lxc-create -t download -n kerberos -- --dist debian --release jessie --arch amd64

cat /var/lib/lxc/kerberos/config

chroot /var/lib/lxc/kerberos/rootfs /bin/bash --login

echo "Inside chroot"

cat /etc/hostname

ip a

ping 8.8.8.8

echo "Going to install Kerberos packages"

apt-get update
apt-get install -y krb5-user krb5-kdc krb5-admin-server krb5-kdc-ldap
apt-get clean

service krb5-kdc enable
service krb5-admin-server enable

lxc-start -n kerberos