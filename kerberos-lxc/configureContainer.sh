#!/usr/bin/env bash

cat /var/lib/lxc/kerberos/config

chroot /var/lib/lxc/kerberos/rootfs /bin/bash --login

ip a

apt-get update
apt-get install -y krb5-user krb5-kdc krb5-admin-server krb5-kdc-ldap
apt-get clean

service krb5-kdc enable
service krb5-admin-server enable