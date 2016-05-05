package pt.tecnico.dsi.kadmin

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.FlatSpec

import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PrincipalSpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPrincipal" should "idempotently succeed" in {
    val principal = "add"
    //Ensure the principal does not exist
    deletePrincipal(principal).rightValueShouldBeUnit()

    //Create the principal
    //This also tests adding a principal when a principal already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    addPrincipal("-nokey", principal).rightValueShouldIdempotentlyBeUnit()

    //Ensure it was in fact created
    getPrincipal(principal).rightValue.name shouldBe getFullPrincipalName(principal)
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "delete"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Delete the principal
    //This also tests deleting a principal when there is no longer a principal
    deletePrincipal(principal).rightValueShouldIdempotentlyBeUnit()

    //Ensure the principal was in fact deleted
    testNoSuchPrincipal {
      getPrincipal(principal)
    }
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "modifyNoSuchPrincipal"
    //Ensure the principal does not exist
    deletePrincipal(principal).rightValueShouldBeUnit()

    //Try to modify it
    testNoSuchPrincipal {
      modifyPrincipal("", principal)
    }
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Modify the principal
    //TODO: test with all the options, maybe property based testing is helpful for this
    modifyPrincipal("-allow_forwardable", principal).rightValueShouldIdempotentlyBeUnit()

    //Ensure it was in fact modified
    getPrincipal(principal).rightValue.attributes should contain ("DISALLOW_FORWARDABLE")
  }

  "withPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "withPrincipalNoSuchPrincipal"
    //Ensure the principal does not exist
    deletePrincipal(principal).rightValueShouldBeUnit()

    //Try to get it
    testNoSuchPrincipal {
      withPrincipal[Boolean](principal) { e =>
        //Purposefully left empty
      }
    }
  }
  it should "idempotently succeed" in {
    val principal = "withPrincipal"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Read it
    withPrincipal[Boolean](principal){ expectBlock =>
      expectBlock.when("""Principal: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == getFullPrincipalName(principal))
        }
    } rightValueShouldIdempotentlyBe true
  }

  "getPrincipal" should "work when the principal has no keys" in {
    val principal = "getPrincipalNoKeys"
    //Ensure the principal does not exist
    deletePrincipal(principal).rightValueShouldBeUnit()

    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Get it
    getPrincipal(principal) idempotentRightValue (_.keys shouldBe Set.empty)
  }
  it should "work when the principal has no policy" in {
    val principal = "getPrincipalNoPolicy"
    //Ensure the principal does not exist
    deletePrincipal(principal).rightValueShouldBeUnit()

    //Ensure the principal exists
    addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()

    //Get it
    getPrincipal(principal) idempotentRightValue (_.policy shouldBe None)
  }
  it should "idempotently succeed" in {
    val principal = "get"
    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Get it
    getPrincipal(principal) idempotentRightValue (_ shouldBe a [Principal])
  }

  "listPrincipals" should "idempotently succeed" in {
    listPrincipals("*") idempotentRightValue (_ should contain allOf ("noPermissions@EXAMPLE.COM", "kadmin/admin@EXAMPLE.COM"))
  }

  val expireDateTime = new DateTime(DateTimeZone.forID("UTC")).plusHours(2)
  "expirePrincipal" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"

    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Expire it
    expirePrincipal(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()

    //Ensure the expiration date changed
    getPrincipal(principal).rightValue.expirationDateTime shouldBe new AbsoluteDateTime(expireDateTime)
  }
  "expirePrincipalPassword" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expirePassword"

    //Ensure the principal exists
    addPrincipal("-nokey", principal).rightValueShouldBeUnit()

    //Expire the principal password
    expirePrincipalPassword(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()

    //Ensure the password expiration date changed
    getPrincipal(principal).rightValue.passwordExpirationDateTime shouldBe new AbsoluteDateTime(expireDateTime)
  }

  //ChangePassword has a dedicated suite since it interlaces with policies
}
