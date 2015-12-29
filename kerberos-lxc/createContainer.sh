#!/usr/bin/env bash

lxc-create --template download --name kerberos -- --dist debian --release wheezy --arch amd64

echo -e "\n\nStarting the Kerberos container and waiting 30s to allow it to boot"
lxc-start --name kerberos --daemon
sleep 30

REALM="EXAMPLE.COM"
DOMAIN="example.com"
CONTAINER_IP=$(lxc-info -n kerberos -iH)
ADMIN_PASSWORD="MITiys4K5!"

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp/
#chroot /var/lib/lxc/kerberos/rootfs /tmp/configureContainer.sh
lxc-attach -n kerberos /tmp/configureContainer.sh $REALM $DOMAIN $CONTAINER_IP $ADMIN_PASSWORD


# We must configure kerberos on the local machine so we can use kadmin and kinit commands
echo -e "\nConfiguring krb5-user on the local machine"
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

echo -e "\nTrying kinit kadmin/admin@EXAMPLE.TEST (should fail)"
kinit kadmin/admin@EXAMPLE.TEST <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\nTrying kinit kadmin/admin@EXAMPLE.COM (should work)"
kinit kadmin/admin@EXAMPLE.COM <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\n"
klist && echo -e "\nKerberos fully operational"



kadmin -p kadmin/admin@EXAMPLE.COM -q "getprincipal kadmin/admin@EXAMPLE.COM" <<EOF
MITiys4K5!
EOF