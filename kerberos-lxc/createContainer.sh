#!/usr/bin/env bash

lxc-create --template download --name kerberos -- --dist debian --release jessie --arch amd64

echo -e "\n\nLXC Kerberos Configuration:"
cat /var/lib/lxc/kerberos/config
echo -e "\n\n"

cp kerberos-lxc/configureContainer.sh /var/lib/lxc/kerberos/rootfs/tmp/
chroot /var/lib/lxc/kerberos/rootfs /tmp/configureContainer.sh

echo -e "\n\nStarting the Kerberos container"
lxc-start --name kerberos --daemon

echo -e "\nSleeping for 30s to allow the container to start"
sleep 30

tail /var/lib/lxc/kerberos/rootfs/var/log/kadmin.log
tail /var/lib/lxc/kerberos/rootfs/var/log/krb5kdc.log

# We must configure kerberos on the local machine so we can use kadmin and kinit commands

REALM="EXAMPLE.EXAMPLE"
DOMAIN="example.example"
CONTAINER_IP=$(lxc-info -n kerberos -iH)

echo -e "\nContainer IP: $CONTAINER_IP"

cat > /etc/krb5.conf <<EOF
[libdefaults]
	default_realm = $REALM

[realms]
	$REALM = {
		kdc = $CONTAINER_IP
		admin_server = $CONTAINER_IP
		default_domain = $DOMAIN
	}
[domain_realm]
	.$DOMAIN = $REALM
	$DOMAIN = $REALM
EOF

echo -e "\nTrying kinit admin/admin@EXAMPLE.COM"
kinit admin/admin@EXAMPLE.COM <<EOF
MITiys4K5!
EOF


echo -e "\nTrying kinit kadmin/admin@EXAMPLE.COM"
kinit kadmin/admin@EXAMPLE.COM <<EOF
MITiys4K5!
EOF

klist && echo -e "\nKerberos fully operational"

kadmin -p kadmin/admin@EXAMPLE.COM -q "getprincipal admin/admin@EXAMPLE.COM" <<EOF
MITiys4K5!
EOF