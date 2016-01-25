#!/usr/bin/env bash

echo -e "\n\nInside lxc-attach"

REALM=$1
DOMAIN=$2
CONTAINER_IP=$3
ADMIN_PASSWORD=$4
echo "REALM: $REALM"
echo "DOMAIN: $DOMAIN"
echo "Container IP: $CONTAINER_IP"
echo "Admin Password: $ADMIN_PASSWORD"

# The configuration used to install Kerberos is the one specified here:
# http://web.mit.edu/kerberos/krb5-1.13/doc/admin/install.html
# with very small modifications.

echo -e "\nInstalling Kerberos packages"
apt-get update
apt-get install -y apt-utils krb5-admin-server krb5-kdc
apt-get clean

systemctl stop krb5-admin-server krb5-kdc

echo -e "\nConfiguring Kerberos"

cat > /etc/krb5.conf <<EOF
[libdefaults]
	default_realm = $REALM
	dns_lookup_realm = false
	dns_lookup_kdc = false

[realms]
	$REALM = {
		kdc = $CONTAINER_IP:750
		admin_server = $CONTAINER_IP:749
		default_domain = $DOMAIN
	}
[domain_realm]
	.$DOMAIN = $REALM
	$DOMAIN = $REALM
EOF
echo -e "\nFinal /etc/krb5.conf:"
cat /etc/krb5.conf

cat > /etc/krb5kdc/kdc.conf<<EOF
[kdcdefaults]
	kdc_ports = 750,88

[realms]
	$REALM = {
		kadmind_port = 749
		max_life = 12h 0m 0s
		max_renewable_life = 7d 0h 0m 0s
		master_key_type = aes256-cts
		supported_enctypes = aes256-cts:normal
	}

[logging]
	default = FILE:/var/log/krb5libs.log
	kdc = FILE:/var/log/krb5kdc.log
	admin_server = FILE:/var/log/kadmin.log
EOF
echo -e "\nFinal /etc/krb5kdc/kdc.conf:"
cat /etc/krb5kdc/kdc.conf

cat > /etc/krb5kdc/kadm5.acl <<EOF
kadmin/admin@$REALM  *
noPermissions@$REALM X
EOF
echo -e "\nFinal /etc/krb5kdc/kadm5.acl:"
cat /etc/krb5kdc/kadm5.acl

echo -e "\nCreating realm"
# Super mega hack to bypass the lack of entropy inside the LXC/Travis environment
rm -f /dev/random
mknod /dev/random c 1 9
MASTER_PASSWORD=$(tr -cd '[:alnum:]' < /dev/urandom | fold -w30 | head -n1)
krb5_newrealm <<EOF
$MASTER_PASSWORD
$MASTER_PASSWORD
EOF

echo -e "\nAdding kadmin/admin principal"
# Something created the kadmin/admin but because we don't know what we don't know its password.
# So we first delete it and then create it again with a password known to us.
kadmin.local -q "delete_principal -force kadmin/admin@$REALM"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD kadmin/admin@$REALM"

echo -e "\nAdding noPermissions principal"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD noPermissions@$REALM"

echo -e "\nEnable services at startup"
systemctl restart krb5-admin-server krb5-kdc

journalctl -xn

echo -e "\nStatus"
systemctl status krb5-admin-server krb5-kdc

echo -e "\nContainer fully configured\n\n"
exit
