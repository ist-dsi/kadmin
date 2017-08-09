# kadmin [![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)
[![Scaladoc](http://javadoc-badge.appspot.com/pt.tecnico.dsi/kadmin_2.12.svg?label=scaladoc&style=plastic&maxAge=604800)](https://ist-dsi.github.io/kadmin/latest/api/pt/tecnico/dsi/kadmin/index.html)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/kadmin_2.12/badge.svg?maxAge=604800)](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/kadmin_2.12)
[![Dependency Status](https://www.versioneye.com/user/projects/5717b800fcd19a004544172f/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/5717b800fcd19a004544172f)
[![Reference Status](https://www.versioneye.com/java/pt.tecnico.dsi:kadmin_2.12/reference_badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/java/pt.tecnico.dsi:kadmin_2.12/references)


[![Build Status](https://travis-ci.org/ist-dsi/kadmin.svg?branch=master&style=plastic&maxAge=604800)](https://travis-ci.org/ist-dsi/kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/a5fead3a55db40cd96470ed7a8efe9c5)](https://www.codacy.com/app/IST-DSI/kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/a5fead3a55db40cd96470ed7a8efe9c5)](https://www.codacy.com/app/IST-DSI/kadmin)
[![BCH compliance](https://bettercodehub.com/edge/badge/ist-dsi/kadmin)](https://bettercodehub.com/)

A type-safe wrapper around the kadmin command for Scala.

In the JVM there are no libraries to create or delete kerberos principals. This is due to the fact that Kerberos only offers
a C API, and interfacing with it via the Java Native Interface (JNI) can be a hard task to accomplish properly.

We solve the problem of Kerberos administration in JVM via the only other alternative: by launching the kadmin
command and write to its standard input and read from its standard output.
To simplify this process we use [scala-expect](https://github.com/Lasering/scala-expect).

[Latest scaladoc documentation](https://ist-dsi.github.io/kadmin/latest/api/pt/tecnico/dsi/kadmin/index.html)

## Install
Add the following dependency to your `build.sbt`:
```sbt
libraryDependencies += "pt.tecnico.dsi" %% "kadmin" % "7.0.0"
```
We use [semantic versioning](http://semver.org).

## Available kadmin commands
 - [Adding a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@addPrincipal(options:String,principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Modifying a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@modifyPrincipal(options:String,principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
   - [Expiring a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@expirePrincipal(principal:String,expirationDateTime:pt.tecnico.dsi.kadmin.ExpirationDateTime):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
   - [Expiring the principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@expirePrincipalPassword(principal:String,datetime:pt.tecnico.dsi.kadmin.ExpirationDateTime,force:Boolean):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Change the principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@changePassword(principal:String,newPassword:Option[String],randKey:Boolean,salt:Option[String]):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Deleting a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@deletePrincipal(principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Getting a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getPrincipal(principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,pt.tecnico.dsi.kadmin.Principal]])
 - [Checking a principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@checkPassword(principal:String,password:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Adding a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@addPolicy(options:String,policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Modifying a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@modifyPolicy(options:String,policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Deleting a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@deletePolicy(policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Getting a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getPolicy(policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,pt.tecnico.dsi.kadmin.Policy]])

Every command is idempotent except when changing either a password, a salt or a key.

Besides the above kadmin commands the following functions are also available:

 - [`getFullPrincipalName`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getFullPrincipalName(principal:String):String) - returns the principal name with the realm, eg: kadmin/admin@EXAMPLE.COM.
 - [`doOperation`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@doOperation[R](f:work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]) - performs a kadmin command which will use password authentication or not according to the configuration, see below.
 - [`obtainTGT`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.KadminUtils$@obtainTGT(options:String,principal:String,password:Option[String],keytab:Option[File]):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Unit]]) - invokes `kinit` to obtain a ticket for a given principal. Authentication is either performed with a password or with a keytab.
 - [`listTickets`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.KadminUtils$@listTickets(options:String):work.martins.simon.expect.fluent.Expect[Seq[pt.tecnico.dsi.kadmin.Ticket]]) - invokes `klist` to obtain the cached tickets.
 - [`destroyTickets`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.KadminUtils$@destroyTickets(options:String):work.martins.simon.expect.fluent.Expect[Unit]) - invokes `kdestroy` to destroy the ticket cache.

## Configurations
Kadmin uses [typesafe-config](https://github.com/typesafehub/config).

The [reference.conf](src/main/resources/reference.conf) file has the following keys:
```scala
kadmin {
  realm = "EXAMPLE.COM"

  principal = "kadmin/admin"
  // If keytab is not empty "command-keytab" will be used.
  // If password is not empty "command-password" will be used.
  // If both keytab and password are not empty "command-keytab" will be used.
  keytab = ""
  password = ""
  

  // This is the command used to start kadmin.
  // The literal string "$FULL_PRINCIPAL" will be replaced with s"$principal@$realm"
  // The literal string "$KEYTAB" will be replaced with s"$keytab"
  command-keytab = ${kadmin.command-password}" -kt $KEYTAB"
  command-password = "kadmin -p $FULL_PRINCIPAL"

  //The location to which keytabs will be generated to. Make sure this location:
  // · is NOT volatile
  // · is not world readable
  // · the user running the application has permission to write and to read from it.
  keytabs-location = "/tmp"

  //Regex that matches against the kadmin command prompt
  prompt = "kadmin(.local)?: "

  # Kadmin will use as settings for scala-expect library those defined:
  # 1) Here, directly under the path kadmin (these have precedence over the next ones).
  # 2) On the same level as kadmin.
  # IMPORTANT: if you set the log level of scala-expect to be info or higher the passwords of the principals will appear in the logs.
  # be sure to set the log level to WARN in production.
}
```

Alternatively you can pass your Config object to the kadmin constructor, or subclass the
[Settings](https://ist-dsi.github.io/kadmin/latest/api/#pt.tecnico.dsi.kadmin.Settings) class for a mixed approach.
The scaladoc of the Settings class has examples explaining the different options.

## How to test kadmin
In the project root run `./test.sh`. This script will run `docker-compose up` inside the docker-kerberos folder.
Be sure to have [docker](https://docs.docker.com/engine/installation/) and [docker-compose](https://docs.docker.com/compose/install/) installed on your computer.

## Note on the docker-kerberos folder
This folder is a [git fake submodule](http://debuggable.com/posts/git-fake-submodules:4b563ee4-f3cc-4061-967e-0e48cbdd56cb)
to the [docker-kerberos repository](https://github.com/ist-dsi/docker-kerberos).

## License
kadmin is open source and available under the [MIT license](LICENSE).
