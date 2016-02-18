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
apt-get -qq update
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
		database_name = /var/lib/krb5kdc/principal
		admin_keytab = FILE:/etc/krb5kdc/kadm5.keytab
		acl_file = /etc/krb5kdc/kadm5.acl
		key_stash_file = /etc/krb5kdc/stash
		kdc_ports = 750,88
		max_life = 10h 0m 0s
		max_renewable_life = 7d 0h 0m 0s
		master_key_type = des3-hmac-sha1
		supported_enctypes = aes256-cts:normal arcfour-hmac:normal des3-hmac-sha1:normal des-cbc-crc:normal des:normal des:v4 des:norealm des:onlyrealm des:afs3
		default_principal_flags = +preauth
	}

[logging]
	default = FILE:/tmp/krb5libs.log
	kdc = FILE:/tmp/kdc.log
	admin_server = FILE:/tmp/kadmin.log
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
# Because of debian this command also starts the krb5-kdc and krb5-admin-server services
krb5_newrealm <<EOF
$MASTER_PASSWORD
$MASTER_PASSWORD
EOF

echo -e "\nTail /tmp/kdc.log"
tail -f /tmp/kdc.log
TAIL_PID=$!
sleep 30
kill -2 $TAIL_PID

echo -e "\nService status"
systemctl status krb5-kdc krb5-admin-server

echo -e "\nAdding kadmin/admin principal"
# Something created the kadmin/admin but because we don't know what we don't know its password.
# So we first delete it and then create it again with a password known to us.
kadmin.local -q "delete_principal -force kadmin/admin@$REALM"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD kadmin/admin@$REALM"

echo -e "\nAdding noPermissions principal"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD noPermissions@$REALM"

echo -e "\nContainer fully configured\n\n"
exit
