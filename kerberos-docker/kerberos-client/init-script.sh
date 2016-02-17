#!/usr/bin/env bash

cat > /etc/krb5.conf <<EOF
[libdefaults]
	default_realm = $REALM
	dns_lookup_realm = false
	dns_lookup_kdc = false

[realms]
	$REALM = {
		kdc = $KDC_KADMIN_SERVER:750
		admin_server = $KDC_KADMIN_SERVER:749
		default_domain = $DOMAIN
	}
[domain_realm]
	.$DOMAIN = $REALM
	$DOMAIN = $REALM
EOF

echo -e "\nTrying kinit kadmin/admin@EXAMPLE.TEST (should fail)"
kinit kadmin/admin@EXAMPLE.TEST <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\nTrying kinit kadmin/admin@$REALM (should work)"
kinit kadmin/admin@$REALM <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\nKlist"
klist && echo -e "\nKerberos fully operational"

echo -e "\nKadmin"
kadmin -p kadmin/admin@$REALM <<EOF
$ADMIN_PASSWORD
get_principal kadmin/admin@$REALM
EOF

cd /tmp/kadmin
sbt test