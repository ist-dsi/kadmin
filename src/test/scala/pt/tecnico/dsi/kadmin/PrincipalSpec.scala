package pt.tecnico.dsi.kadmin

import org.scalatest.FlatSpec
import org.joda.time.{DateTime, DateTimeZone}
import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PrincipalSpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPrincipal" should "idempotently succeed" in {
    val principal = "add"
    //Ensure the principal does not exist
    deletePrincipal(principal) shouldReturn Right(Unit)

    //Create the principal
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    addPrincipal("-nokey", principal) shouldIdempotentlyReturn Right(Unit)

    //Ensure it was in fact created
    withPrincipal[Unit](principal){ expectBlock =>
      expectBlock.when("""Principal: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == getFullPrincipalName(principal))
        }
    } shouldReturn Right(Unit)
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "delete"
    //Ensure the principal exists
    addPrincipal("-nokey", principal) shouldReturn Right(Unit)

    //Delete the principal
    //This also tests deleting a principal when there is no longer a principal
    deletePrincipal(principal) shouldIdempotentlyReturn Right(Unit)

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
    deletePrincipal(principal) shouldReturn Right(Unit)

    //Try to modify it
    testNoSuchPrincipal {
      modifyPrincipal("", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    //Ensure the principal exists
    addPrincipal("-nokey", principal) shouldReturn Right(Unit)

    //Modify the principal
    //TODO: test with all the options, maybe property based testing is helpful for this
    modifyPrincipal("-allow_forwardable", principal) shouldIdempotentlyReturn Right(Unit)

    //Ensure it was in fact modified
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Attributes: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1).contains("DISALLOW_FORWARDABLE"))
        }
    } shouldReturn Right(true)
  }

  "getPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "getNoSuchPrincipal"
    //Ensure the principal does not exist
    deletePrincipal(principal) shouldReturn Right(Unit)

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
    addPrincipal("-nokey", principal) shouldReturn Right(Unit)

    //Read it
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Principal: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == getFullPrincipalName(principal))
        }
    } shouldReturn Right(true)
  }

  //TODO: test with different time zones (the time zone will be ignored)

  val expireDateTime = new DateTime(DateTimeZone.forID("UTC")).plusHours(2)
  "expirePrincipal and getExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"

    //Ensure the principal exists
    addPrincipal("-nokey", principal) shouldReturn Right(Unit)

    //Expire it
    expirePrincipal(principal, expireDateTime) shouldIdempotentlyReturn Right(Unit)

    //Ensure the expiration date changed
    getExpirationDate(principal) shouldIdempotentlyReturn Right(expireDateTime)
  }
  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expirePassword"

    //Ensure the principal exists
    addPrincipal("-nokey", principal) shouldReturn Right(Unit)

    //Expire the principal password
    expirePrincipalPassword(principal, expireDateTime) shouldIdempotentlyReturn Right(Unit)

    //Ensure the password expiration date changed
    getPasswordExpirationDate(principal) shouldIdempotentlyReturn Right(expireDateTime)
  }

  //ChangePassword has a dedicated suite since it interlaces with policies
}
