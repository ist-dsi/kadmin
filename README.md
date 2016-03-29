# kadmin
A type-safe, idempotent wrapper around the kadmin command for Scala.

The support for Kerberos administration in the JVM is inexistent. It is possible to obtain Kerberos
tickets in the Java world but to create or delete principals is outright impossible. The reason is that kerberos only
offers a C API and interfacing with C via the Java Native Interface is very hard to do correctly.

We solve this problem via the only other alternative which is to launch the kadmin command and write to its standard input
and read from its standard output. To simplify this process we use [scala-expect](https://github.com/Lasering/scala-expect).

