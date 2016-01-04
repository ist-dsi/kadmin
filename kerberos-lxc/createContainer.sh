#!/usr/bin/env bash

apt-get update
apt-get install -y -o Dpkg::Options::="--force-confold" lxc liblxc1 lxc-templates

echo -e "\n\nCreating the Kerberos container"
lxc-create --template download --name kerberos -- --dist debian --release jessie --arch amd64

echo -e "\n\nStarting the container and waiting 30s to allow it to boot"
lxc-start --name kerberos --daemon
sleep 30

echo -e "\n\Configuring the container"
REALM="EXAMPLE.COM"
DOMAIN="example.com"
CONTAINER_IP=$(lxc-info -n kerberos -iH)
ADMIN_PASSWORD="MITiys4K5"

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp/
lxc-attach -n kerberos /tmp/configureContainer.sh -- $REALM $DOMAIN $CONTAINER_IP $ADMIN_PASSWORD

echo -e "\nConfiguring krb5-user on the local machine"
# We must configure kerberos on the local machine so we can use kadmin and kinit commands
apt-get install -y krb5-user

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

echo -e "\nTrying kinit admin/admin@EXAMPLE.TEST (should fail)"
kinit admin/admin@EXAMPLE.TEST <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\nTrying kinit admin/admin@$REALM (should work)"
kinit admin/admin@$REALM <<EOF
$ADMIN_PASSWORD
EOF

echo -e "\nKlist"
klist && echo -e "\nKerberos fully operational"

echo -e "\nKadmin"
kadmin -p admin/admin@$REALM <<EOF
$ADMIN_PASSWORD
get_principal admin/admin@$REALM
EOF