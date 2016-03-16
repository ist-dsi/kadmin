#!/usr/bin/env bash

[[ -z $DOMAIN ]] && DOMAIN=$(hostname -d)
if [[ -z $DOMAIN ]]; then
  # If the domain is still empty then the user did not define a hostname
  # In these cases we use the example.com
  DOMAIN=example.com
  REALM=$(echo $DOMAIN | sed 's/.*/\U&/')
fi
[[ -z $REALM ]] && REALM=$(hostname -d | sed 's/.*/\U&/')
[[ -z $KDC_KADMIN_SERVER ]] && KDC_KADMIN_SERVER=$(hostname -f)
[[ -z $ADMIN_PASSWORD ]] && ADMIN_PASSWORD=MITiys4K5

echo "================================================================"
echo "==== /etc/krb5.conf ============================================"
echo "================================================================"
tee /etc/krb5.conf <<EOF
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
echo ""

echo "================================================================"
echo "==== /etc/krb5kdc/kdc.conf ====================================="
echo "================================================================"
tee /etc/krb5kdc/kdc.conf <<EOF
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
		supported_enctypes = aes256-cts-hmac-sha1-96:normal
		default_principal_flags = +preauth
	}

[logging]
	default = FILE:/tmp/krb5libs.log
	kdc = FILE:/tmp/kdc.log
	admin_server = FILE:/tmp/kadmin.log
EOF
echo ""

echo "================================================================"
echo "==== /etc/krb5kdc/kadm5.acl ===================================="
echo "================================================================"
tee /etc/krb5kdc/kadm5.acl <<EOF
kadmin/admin@$REALM  *
noPermissions@$REALM X
EOF
echo ""

echo "================================================================"
echo "==== Creating realm ============================================"
echo "================================================================"
MASTER_PASSWORD=$(tr -cd '[:alnum:]' < /dev/urandom | fold -w30 | head -n1)
# This command also starts the krb5-kdc and krb5-admin-server services
krb5_newrealm <<EOF
$MASTER_PASSWORD
$MASTER_PASSWORD
EOF
echo ""

echo "================================================================"
echo "==== Create the principals in the acl =========================="
echo "================================================================"
echo "Adding kadmin/admin principal"
# Something created the kadmin/admin but because we don't know what,
# we don't know its password. So we first delete it and then create it
# again with a password known to us.
kadmin.local -q "delete_principal -force kadmin/admin@$REALM"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD kadmin/admin@$REALM"
echo ""

echo "Adding noPermissions principal"
kadmin.local -q "addprinc -pw $ADMIN_PASSWORD noPermissions@$REALM"
echo ""

echo "================================================================"
echo "==== Run the services =========================================="
echo "================================================================"
# We want the container to keep running until we explicitly kill it.
# So the last command cannot immediately exit. See
#   https://docs.docker.com/engine/reference/run/#detached-vs-foreground
# for a better explanation.

krb5kdc
kadmind -nofork