# kadmin
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/kadmin_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/kadmin_2.11)
[![Build Status](https://travis-ci.org/ist-dsi/kadmin.svg?branch=master)](https://travis-ci.org/ist-dsi/kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/a5fead3a55db40cd96470ed7a8efe9c5)](https://www.codacy.com/app/IST-DSI/kadmin)
[![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)

A type-safe wrapper around the kadmin command for Scala.

In JVM it's possible to obtain Kerberos tickets, but to create or delete principals is outright impossible.
Kerberos only offers a C API, and interfacing with it via the Java Native Interface (JNI) can be a hard task to accomplish properly.

We solve the problem of Kerberos administration in JVM via the only other alternative: by launching the kadmin
command and write to its standard input and read from its standard output.
To simplify this process we use [scala-expect](https://github.com/Lasering/scala-expect).

[Latest scaladoc documentation](http://ist-dsi.github.io/kadmin/latest/api/)

## Install
Add the following dependency to your `build.sbt`:
```sbt
libraryDependencies += "pt.tecnico.dsi" %% "kadmin" % "2.0.0"
```
We use [semantic versioning](http://semver.org).

## Available kadmin commands
 - [Adding a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@addPrincipal(options:String,principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Modifying a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@modifyPrincipal(options:String,principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
   - [Expiring a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@expirePrincipal(principal:String,expirationDateTime:pt.tecnico.dsi.kadmin.ExpirationDateTime):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
   - [Expiring the principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@expirePrincipalPassword(principal:String,datetime:pt.tecnico.dsi.kadmin.ExpirationDateTime,force:Boolean):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Change the principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@changePassword(principal:String,newPassword:Option[String],randKey:Boolean,salt:Option[String]):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Deleting a principal](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@deletePrincipal(principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Reading principal attributes](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@withPrincipal[R](principal:String)(f:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]])
   - [Getting the principal expiration date](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getExpirationDate(principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,pt.tecnico.dsi.kadmin.ExpirationDateTime]])
   - [Getting the principal password expiration date](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getPasswordExpirationDate(principal:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,pt.tecnico.dsi.kadmin.ExpirationDateTime]])
 - [Checking a principal password](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@checkPassword(principal:String,password:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Adding a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@addPolicy(options:String,policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Modifying a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@modifyPolicy(options:String,policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Deleting a policy](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@deletePolicy(policy:String):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,Boolean]])
 - [Reading policy attributes](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@withPolicy[R](policy:String)(f:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]])

All of these commands can be made with authentication, i.e. using the **kadmin** command or without authentication
using the **kadmin.local** command. Whether or not to perform authentication can be defined in the configuration.

Every command is idempotent except when changing either a password, a salt or a key.

Besides these kadmin commands the following functions are also available:

 - [`getFullPrincipalName`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getFullPrincipalName(principal:String):String) - returns the principal name with the realm, eg: kadmin/admin@EXAMPLE.COM.
 - [`obtainTicketGrantingTicket`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@getFullPrincipalName(principal:String):String) - invokes `kinit` to obtain a ticket and returns the DateTime in which the ticket must be renewed.
 - [`withAuthentication`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@withAuthentication[R](f:work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]) - performs a kadmin command, but the details of handling the authentication
   and unknown errors in the initialization (such as the KDC not being available) have already been dealt with.
 - [`withoutAuthentication`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@withoutAuthentication[R](f:work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]) - performs a kadmin command, but the details of handling unknown errors
   in the initialization have already been dealt with.
 - [`doOperation`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@doOperation[R](f:work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]=>Unit):work.martins.simon.expect.fluent.Expect[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]) - performs a kadmin command which will be performed with authentication or not
   according to the configuration `perform-authentication`.
 - [`parseDateTime`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@parseDateTime(dateTimeString:String):pt.tecnico.dsi.kadmin.ExpirationDateTime) - parses a date time string returned by a kadmin command to a `DateTime`.
 - [`insufficientPermission`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@insufficientPermission[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.RegexWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]),
   [`principalDoesNotExist`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@principalDoesNotExist[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.StringWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]),
   [`policyDoesNotExist`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@policyDoesNotExist[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.StringWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]),
   [`passwordIncorrect`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@passwordIncorrect[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.StringWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]),
   [`passwordExpired`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@passwordExpired[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.StringWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]),
   [`unknownError`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@unknownError[R](expectBlock:work.martins.simon.expect.fluent.ExpectBlock[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):work.martins.simon.expect.fluent.RegexWhen[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]) -
   these functions match against common kadmin errors and return the appropriate `ErrorCase`.
 - [`preemptiveExit`](https://ist-dsi.github.io/kadmin/latest/api/index.html#pt.tecnico.dsi.kadmin.Kadmin@preemptiveExit[R](when:work.martins.simon.expect.fluent.When[Either[pt.tecnico.dsi.kadmin.ErrorCase,R]]):Unit) - allows you to gracefully terminate the kadmin cli and ensure the next `ExpectBlock`s in the current
   `Expect` do not get executed.


## Configurations
Kadmin uses [typesafe-config](https://github.com/typesafehub/config).

The [reference.conf](src/main/resources/reference.conf) file was the following keys:
```scala
kadmin {
  realm = "EXAMPLE.COM"

  perform-authentication = true

  //If perform-authentication is true then these are the credentials used to perform the authentication
  authenticating-principal = "kadmin/admin"
  authenticating-principal-password = ""

  //This is the command used to start kadmin when authentication is to be performed.
  //The string "$FULL_PRINCIPAL" will be replaced with s"$authenticating-principal@$realm"
  command-with-authentication = "kadmin -p $FULL_PRINCIPAL"
  //This is the command used to start kadmin when no authentication is necessary
  command-without-authentication = "kadmin.local"

  //The location to which keytabs will be generated to.
  //Make sure this location is NOT volatile, is not world readable, the user running the application has suficient
  //permissions to write and to read from it.
  keytabs-location = "/tmp"

  //Regex that matches against the kadmin command prompt
  prompt = "kadmin(.local)?: "
}
```

You will need to define at least the `realm` and the `authenticating-principal-password` in your `application.conf`.
If you don't require authentication you can simply set `perform-authentication` to false and define the `realm`.

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
