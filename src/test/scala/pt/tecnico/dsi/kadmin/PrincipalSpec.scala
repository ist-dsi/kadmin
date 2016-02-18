package pt.tecnico.dsi.kadmin

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PrincipalSpec extends FlatSpec with TestUtils with LazyLogging {
  import authenticatedKadmin._

  "addPrincipal" should "idempotently succeed" in {
    val principal = "add"
    //Ensure the principal does not exist
    deletePrincipal(principal).value shouldBe Right(true)

    //Create the principal
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    addPrincipal("-nokey", principal) shouldIdempotentlyReturn Right(true)

    //Ensure it was in fact created
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Principal: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == getFullPrincipalName(principal))
        }
    }.value shouldBe Right(true)
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "delete"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).value shouldBe Right(true)

    //Delete the principal
    //This also tests deleting a principal when there is no longer a principal
    deletePrincipal(principal) shouldIdempotentlyReturn Right(true)

    //Ensure the principal was in fact deleted
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "modifyNoSuchPrincipal"
    //Ensure the principal does not exist
    deletePrincipal(principal).value shouldBe Right(true)

    //Try to modify it
    testNoSuchPrincipal {
      modifyPrincipal("", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).value shouldBe Right(true)

    //Modify the principal
    //TODO: test with all the options, maybe property based testing is helpful for this
    modifyPrincipal("-allow_forwardable", principal) shouldIdempotentlyReturn Right(true)

    //Ensure it was in fact modified
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Attributes: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1).contains("DISALLOW_FORWARDABLE"))
        }
    }.value shouldBe Right(true)
  }

  "getPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "getNoSuchPrincipal"
    //Ensure the principal does not exist
    deletePrincipal(principal).value shouldBe Right(true)

    //Try to get it
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }
  it should "idempotently succeed" in {
    val principal = "get"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).value shouldBe Right(true)

    //Read it
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Principal: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == getFullPrincipalName(principal))
        }
    }.value shouldBe Right(true)
  }

  val expireDateTime = 2.hours.toAbsolute
  val utcExpireDateTime = new AbsoluteDateTime(expireDateTime.dateTime.withZone(DateTimeZone.forID("UTC")))
  "expirePrincipal and getExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege

    val principal = "expire"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).value shouldBe Right(true)

    //Expire it
    expirePrincipal(principal, expireDateTime) shouldIdempotentlyReturn Right(true)

    //Ensure the expiration date changed
    getExpirationDate(principal) shouldIdempotentlyReturn Right(utcExpireDateTime)
  }
  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"

    //Ensure the principal exists
    addPrincipal("-nokey", principal).value shouldBe Right(true)

    //Expire the principal password
    expirePrincipalPassword(principal, expireDateTime) shouldIdempotentlyReturn Right(true)

    //Ensure the password expiration date changed
    getPasswordExpirationDate(principal) shouldIdempotentlyReturn Right(utcExpireDateTime)
  }

  //ChangePassword has a dedicated suite since it interlaces with policies
}
