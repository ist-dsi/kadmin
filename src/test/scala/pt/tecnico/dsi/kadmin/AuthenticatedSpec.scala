package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class AuthenticatedSpec extends FlatSpec with TestUtils with LazyLogging {
  import authenticatedKadmin._

  //println(kerberos.settings)

  "addPrincipal" should "idempotently succeed" in {
    val principal = "add"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      addPrincipal("-nokey", principal)
    }(Right(true))
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "delete"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    //This also tests deleting a principal when there is no longer a principal
    idempotent {
      deletePrincipal(principal)
    }(Right(true))
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "modifyNoSuchPrincipal"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      modifyPrincipal("-nokey", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      modifyPrincipal("-allow_forwardable", principal)
    }(Right(true))

    runExpect(withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Attributes: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1).contains("DISALLOW_FORWARDABLE"))
        }
    }) shouldBe Right(true)
  }

  "getPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "get"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }

  val expireDateTime = 2.hours.toAbsolute
  val utcExpireDateTime = new AbsoluteDateTime(expireDateTime.dateTime.withZone(DateTimeZone.forID("UTC")))
  "expirePrincipal and getExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)

    idempotent {
      expirePrincipal(principal, expireDateTime)
    }(Right(true))

    idempotent {
      getExpirationDate(principal)
    }(Right(utcExpireDateTime))
  }
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
