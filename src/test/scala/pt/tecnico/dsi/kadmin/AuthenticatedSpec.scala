package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.fluent.Expect
import squants.time.TimeConversions._
import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticatedSpec extends FlatSpec with Matchers with ScalaFutures {
  /**
    * Tests that the operation in `test` is idempotent by repeating `test` three times.
    * If `test` fails on the second or third time a `TestFailedException` exception will
    * be thrown stating `test` is not idempotent.
    *
    * @example {{{
    *  val initialSet = Set(1, 2, 3)
    *  idempotent {
    *   (initialSet + 4) shouldBe Set(1, 2, 3, 4)
    *  }
    * }}}
    */
  def idempotent(test: => Unit): Unit = {
    //If this fails we do not want to catch its exception, because failing in the first attempt means
    //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
    //the failure with a "Operation is not idempotent".
    test

    //This code will only be executed if the previous test succeed.
    //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
    try {
      test
      test
    } catch {
      case e: TestFailedException =>
        throw new TestFailedException("Operation is not idempotent", e, e.failedCodeStackDepth + 1)
    }
  }

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]]) = idempotent {
    e.run().futureValue shouldBe Left(NoSuchPrincipal)
  }

  val defaultPatience = PatienceConfig(
    timeout = Span(1, Seconds),
    interval = Span(2, Seconds)
  )

  val authenticatedConfig = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"

      perform-authentication = true

      authenticating-principal = ""
      authenticating-principal-password = ""

      command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}
      command-without-authentication = "kadmin.local"
    }""")

  val kerberos = new Kadmin(authenticatedConfig)
  import kerberos._

  //In kadm5.acl have an entry
  //noPermissions@IST.UTL.PT X *
  //This means the principal noPermissions@IST.UTL.PT has no permissions for every principal

  "addPrincipal" should "idempotently succeed" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    }
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    //This also tests deleting a principal when there is no longer a principal
    idempotent {
      deletePrincipal(principal).run().futureValue shouldBe Right(true)
    }
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "test"
    deletePrincipal(principal).run().futureValue shouldBe Right(true)
    testNoSuchPrincipal {
      modifyPrincipal("-nokey", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      modifyPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
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
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    idempotent {
      changePassword(principal, "p$1").run().futureValue shouldBe Left(PasswordTooShort)
    }
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password does not have enougth character classes" in {
    val principal = "test"
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    idempotent {
      changePassword(principal, "superbigpasswordwithonlytext").run().futureValue shouldBe Left(PasswordTooShort)
    }
  }
  it should "idempotently succeed" in {
    val principal = "test"
    addPrincipal("-nokey", principal).run().futureValue shouldBe Right(true)
    //This will fail if the principal policy does not allow password reuses
    val password = "bigpasswordshave@least20characters"
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
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipal(principal, expireDateTime).run().futureValue shouldBe Right(true)
      getExpirationDate(principal).run().futureValue shouldBe Right(expireDateTime)
    }
  }

  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "test"
    val expireDateTime: AbsoluteDateTime = 2.hours.toAbsolute
    idempotent {
      expirePrincipalPassword(principal, expireDateTime).run().futureValue shouldBe Right(true)
      getPasswordExpirationDate(principal).run().futureValue shouldBe Right(expireDateTime)
    }
  }
}
