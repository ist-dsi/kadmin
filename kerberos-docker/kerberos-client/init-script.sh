#!/usr/bin/env bash

[[ -z $DOMAIN ]] && DOMAIN=$(hostname -d)
if [[ -z $DOMAIN ]]; then
  # If the domain is still empty then the user did not define a hostname
  # In these cases we use the example.com
  DOMAIN=example.com
  REALM=$(echo $DOMAIN | sed 's/.*/\U&/')
fi
[[ -z $REALM ]] && REALM=$(hostname -d | sed 's/.*/\U&/')
[[ -z $KDC_KADMIN_SERVER ]] && KDC_KADMIN_SERVER=kdc-kadmin
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
echo "==== Preliminar testing ========================================"
echo "================================================================"
echo "kadmin kadmin/admin@EXAMPLE.TEST (should fail)"
kadmin -p kadmin/admin@EXAMPLE.TEST -w $ADMIN_PASSWORD -q "get_principal kadmin/admin@EXAMPLE.TEST"
echo ""

echo "kadmin kadmin/admin@$REALM (should work)"
kadmin -p kadmin/admin@$REALM -w $ADMIN_PASSWORD -q "get_principal kadmin/admin@$REALM"
echo ""

echo "================================================================"
echo "==== Run tests ================================================="
echo "================================================================"
cd /tmp/kadmin
#sbt test