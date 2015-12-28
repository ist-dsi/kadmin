#!/usr/bin/env bash

echo -e "\n\nInside chroot"

# The configuration used to install Kerberos is the one specified here:
# http://web.mit.edu/kerberos/krb5-1.13/doc/admin/install.html
# with very small modifications.

echo -e "\nInstalling Kerberos packages"
apt-get update
apt-get install -y apt-utils
apt-get install -y krb5-admin-server krb5-kdc
apt-get clean

service krb5-admin-server stop
service krb5-kdc stop

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
		kdc = localhost:88
		admin_server = localhost:749
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
	kdc_ports = 88

[realms]
	$REALM = {
		kadmind_port = 749
		max_life = 12h 0m 0s
		max_renewable_life = 7d 0h 0m 0s
		master_key_type = aes256-cts
		supported_enctypes = aes256-cts:normal aes128-cts:normal
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

# Super mega hack to bypass the lack of entropy inside the LXC/Travis environment
rm -f /dev/random
mknod /dev/random c 1 9

echo -e "\nCreating realm"
kdb5_util create -r $REALM -s -P $(tr -cd '[:alnum:]' < /dev/urandom | fold -w30 | head -n1)

echo -e "\nAdding kadmin/admin principal"
ADMIN_PASSWORD="MITiys4K5!"
kadmin.local -q "addprinc kadmin/admin@$REALM" <<EOF
$ADMIN_PASSWORD
$ADMIN_PASSWORD
EOF

echo -e "\nAdding noPermissions principal"
kadmin.local -q "addprinc noPermissions@$REALM" <<EOF
$ADMIN_PASSWORD
$ADMIN_PASSWORD
EOF

echo -e "\nEnable services at startup"
invoke-rc.d krb5-admin-server restart
invoke-rc.d krb5-kdc restart
service krb5-admin-server status

tail -f var/log/kadmin.log &
TAIL_PID=$!
sleep 60
kill -2 $TAIL_PID

#systemctl restart krb5-admin-server krb5-kdc
#systemctl status krb5-admin-server

echo -e "\nContainer fully configured\n\n"
exit
