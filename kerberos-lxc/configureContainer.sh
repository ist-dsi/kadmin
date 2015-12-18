#!/usr/bin/env bash

echo "\n\nInside chroot"

echo "Installing Kerberos packages"
apt-get update
apt-get install -y apt-utils krb5-user krb5-kdc krb5-admin-server krb5-kdc-ldap krb5-locales
apt-get clean

echo "\nEnabling the Kerberos Services"
systemctl enable krb5-kdc krb5-admin-server

echo "\nContainer fully configured\n\n"
exit
