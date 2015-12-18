#!/usr/bin/env bash

echo "Inside chroot"

cat /etc/hostname

ip a

ping -c 4 8.8.8.8

echo "Going to install Kerberos packages"

apt-get update
apt-get install -y krb5-user krb5-kdc krb5-admin-server krb5-kdc-ldap
apt-get clean

service krb5-kdc enable
service krb5-admin-server enable

exit
