package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

/**
  * $assumptions
  */
class ChangePasswordAndPoliciesSpec extends FlatSpec with TestUtils with LazyLogging {
  val authenticatedConfig = ConfigFactory.parseString(s"""
    kadmin {
      perform-authentication = true

      realm = "EXAMPLE.COM"

      authenticating-principal = "kadmin/admin"
      authenticating-principal-password = "MITiys4K5"

      command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}"@"$${kadmin.realm}
    }""")

  val kerberos = new Kadmin(authenticatedConfig.resolve())
  import kerberos._

  //These tests make the following assumptions:
  //  · The realm EXAMPLE.COM exists.
  //  . Kerberos client is installed in the machine where the tests are being ran. And the configuration has the
  //     realm EXAMPLE.COM.
  //  · In EXAMPLE.COM KDC the kadm5.acl file has at least the following entries
  //     kadmin/admin@EXAMPLE.COM  *
  //     noPermissions@EXAMPLE.COM X
  //  · The password for these two principals is "MITiys4K5".
  //
  //These assumptions are valid in the Travis CI.
  //Look at the .travis.yml file and the kerberos-lxc directory to understand why.
  //To run these tests locally (assuming a Debian machine):
  //  · sudo ./kerberos-lxc/createContainer.sh

  /*"changePassword" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "changePasswordNoSuchPrincipal"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      changePassword(principal, "newPassword")
    }
  }
  it should "return PasswordTooShort when the password is too short" in {
    val principal = "changePasswordPasswordTooShort"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "p$1")
    }(Left(PasswordTooShort))
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password does not have enough character classes" in {
    val principal = "changePasswordNotEnoughClasses"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "super big password with only text")
    }(Left(PasswordTooShort))
  }
  it should "idempotently succeed" in {
    val principal = "changePassword"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    //This will fail if the principal policy does not allow password reuses
    val password = "big passwords have @least 20 characters"
    idempotent {
      changePassword(principal, password)
    }(Right(true))
    idempotent {
      checkPassword(principal, password)
    }(Right(true))
  }*/

  val expireDateTime = 2.hours.toAbsolute
  val utcExpireDateTime = new AbsoluteDateTime(expireDateTime.dateTime.withZone(DateTimeZone.forID("UTC")))
  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)

    idempotent {
      expirePrincipalPassword(principal, expireDateTime)
    }(Right(true))

    idempotent {
      getPasswordExpirationDate(principal)
    }(Right(utcExpireDateTime))
  }
}
