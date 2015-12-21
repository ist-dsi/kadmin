#!/usr/bin/env bash

echo -e "\n\nInside chroot"

# The configuration used to install Kerberos is the one specified here:
# http://web.mit.edu/kerberos/krb5-1.13/doc/admin/install.html
# with very small modifications.

echo -e "\nInstalling Kerberos packages"
apt-get update
apt-get install -y apt-utils
apt-get install -y krb5-kdc krb5-admin-server krb5-user
apt-get clean

# The apt-get install will try to startup the kerberos services right away but it will fail since the services
# are not configured, so we stop them until we configure them.
systemctl stop krb5-kdc krb5-admin-server

echo -e "\nConfiguring Kerberos"

REALM="EXAMPLE.COM"
DOMAIN="example.com"

cat > /etc/krb5.conf <<EOF
[libdefaults]
	default_realm = $REALM
	dns_lookup_realm = false
	dns_lookup_kdc = false

[realms]
	$REALM = {
		kdc = localhost
		admin_server = localhost
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
    kdc_ports = 88,750

[realms]
	$REALM = {
		kadmind_port = 749
		max_life = 12h 0m 0s
		max_renewable_life = 7d 0h 0m 0s
		master_key_type = aes256-cts
		supported_enctypes = aes256-cts:normal aes128-cts:normal
	}

#[logging]
#	default = FILE:/var/log/krb5libs.log
#	kdc = FILE:/var/log/krb5kdc.log
#	admin_server = FILE:/var/log/kadmin.log
EOF

echo -e "\nFinal /etc/krb5kdc/kdc.conf:"
cat /etc/krb5kdc/kdc.conf

cat > /etc/krb5kdc/kadm5.acl <<EOF
admin/admin@$REALM  *
noPermissions@$REALM X
EOF

echo -e "\nFinal /etc/krb5kdc/kadm5.acl:"
cat /etc/krb5kdc/kadm5.acl

MASTER_PASSWORD=$(tr -cd '[:alnum:]' < /dev/random | fold -w30 | head -n1)
kdb5_util create -r $REALM -s <<EOF
$MASTER_PASSWORD
$MASTER_PASSWORD
EOF

ADMIN_PASSWORD=$(tr -cd '[:alnum:]' < /dev/random | fold -w30 | head -n1)
kadmin.local -q "addprinc admin/admin@$REALM" <<EOF
$ADMIN_PASSWORD
$ADMIN_PASSWORD
EOF

echo -e "\nEnabling the Kerberos Services"
systemctl enable krb5-kdc krb5-admin-server

echo -e "\nSleeping for 1 minute to allow the services to startup"
sleep 60

systemctl status krb5-kdc
systemctl status krb5-admin-server

echo -e "\nContainer fully configured\n\n"
exit
