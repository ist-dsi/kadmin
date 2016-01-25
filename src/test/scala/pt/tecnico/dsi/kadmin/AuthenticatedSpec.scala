package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

class AuthenticatedSpec extends FlatSpec with TestUtils with LazyLogging {
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

  //println(kerberos.settings)

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
  //  · Install LXC on your machine.
  //  . sudo ./kerberos-lxc/createContainer.sh

/*
  "addPrincipal" should "idempotently succeed" in {
    val principal = "test"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      addPrincipal("-randkey", principal)
    }(Right(true))
  }
*/

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    //This also tests deleting a principal when there is no longer a principal
    idempotent {
      deletePrincipal(principal)
    }(Right(true))
  }

/*
  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      modifyPrincipal("-randkey", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "test"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      modifyPrincipal("-randkey", principal)
    }(Right(true))
  }

  "changePassword" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      changePassword(principal, "newPassword")
    }
  }
  it should "return PasswordTooShort when the password is too short" in {
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "p$1")
    }(Left(PasswordTooShort))
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password does not have enough character classes" in {
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "super big password with only text")
    }(Left(PasswordTooShort))
  }
  it should "idempotently succeed" in {
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    //This will fail if the principal policy does not allow password reuses
    val password = "big passwords have @least 20 characters"
    idempotent {
      changePassword(principal, password)
    }(Right(true))
    idempotent {
      checkPassword(principal, password)
    }(Right(true))
  }

  "getPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }

  "expirePrincipal and getExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipal(principal, expireDateTime)
    }(Right(true))
    idempotent {
      getExpirationDate(principal)
    }(Right(expireDateTime))
  }

  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "test"
    runExpect(addPrincipal("-randkey", principal)) shouldBe Right(true)
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipalPassword (principal, expireDateTime)
    }(Right(true))
    idempotent {
      getPasswordExpirationDate(principal)
    }(Right(expireDateTime))
  }
*/
}
