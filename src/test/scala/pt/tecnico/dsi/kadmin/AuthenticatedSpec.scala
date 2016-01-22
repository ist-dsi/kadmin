package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import squants.time.TimeConversions._
import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticatedSpec extends FlatSpec with Matchers with ScalaFutures with TestUtils with LazyLogging {
  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(2, Seconds)
  )

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

  "addPrincipal" should "idempotently succeed" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      whenReady(addPrincipal("-randkey", principal).run()) { r =>
        logger.info(s"Got $r")
        r shouldBe Right(true)
      }
    }
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    //This also tests deleting a principal when there is no longer a principal
    idempotent {
      whenReady(deletePrincipal(principal).run()) { r =>
        logger.info(s"Got $r")
        r shouldBe Right(true)
      }
    }
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    testNoSuchPrincipal {
      modifyPrincipal("-randkey", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      modifyPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    }
  }

  "changePassword" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    testNoSuchPrincipal {
      changePassword(principal, "newPassword")
    }
  }
  it should "return PasswordTooShort when the password is too short" in {
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    idempotent {
      changePassword(principal, "p$1").run().futureValue shouldBe Left(PasswordTooShort)
    }
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password does not have enough character classes" in {
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    idempotent {
      changePassword(principal, "super big password with only text").run().futureValue shouldBe Left(PasswordTooShort)
    }
  }
  it should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    //This will fail if the principal policy does not allow password reuses
    val password = "big passwords have @least 20 characters"
    idempotent {
      changePassword(principal, password).run().futureValue shouldBe Right(true)
      checkPassword(principal, password).run().futureValue shouldBe Right(true)
    }
  }

  "getPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }

  "expirePrincipal and getExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipal(principal, expireDateTime).run().futureValue shouldBe Right(true)
      getExpirationDate(principal).run().futureValue shouldBe Right(expireDateTime)
    }
  }

  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "test"
    addPrincipal("-randkey", principal).run().futureValue shouldBe Right(true)
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipalPassword(principal, expireDateTime).run().futureValue shouldBe Right(true)
      getPasswordExpirationDate(principal).run().futureValue shouldBe Right(expireDateTime)
    }
  }
}
