package pt.tecnico.dsi.kadmin

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.AsyncFlatSpec

/**
  * $assumptions
  */
class PrincipalSpec extends AsyncFlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPrincipal" should "idempotently succeed when setting nokey" in {
    val principal = "addNoKey"
    
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      //This also tests adding a principal when a principal already exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPrincipal(principal).rightValue (_.name shouldBe fullPrincipalName(principal))
    } yield resultingFuture
  }
  it should "idempotently succeed when setting a password" in {
    val principal = "addPassword"
    
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      // This also tests adding a principal when a principal already exists
      _ <- addPrincipal("", principal, password = Some("aShinyN3wPassword")).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPrincipal(principal).rightValue (_.name shouldBe fullPrincipalName(principal))
    } yield resultingFuture
  }
  // Maybe these two tests should be in the PasswordSpec
  it should "return PasswordToShort when the password is too short" in {
    val principal = "addPasswordTooShort"
    val policy = "addPasswordTooShort"

    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      _ <- addPolicy("-minlength 5", policy).rightValueShouldBeUnit()
      resultingFuture <- addPrincipal(s"-policy $policy", principal, password = Some("a")) leftValueShouldIdempotentlyBe PasswordTooShort
    } yield resultingFuture
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password has too few character classes" in {
    val principal = "addPasswordTooShort"
    val policy = "addPasswordTooShort"

    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      _ <- addPolicy("-minclasses 5", policy).rightValueShouldBeUnit()
      resultingFuture <- addPrincipal(s"-policy $policy", principal, password = Some("aaaaaaaaaaaa")) leftValueShouldIdempotentlyBe PasswordWithoutEnoughCharacterClasses
    } yield resultingFuture
  }

  "deletePrincipal" should "idempotently succeed" in {
    val principal = "delete"
    
    for {
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Delete the principal
      // This also tests deleting a principal when there is no longer a principal
      _ <- deletePrincipal(principal).rightValueShouldIdempotentlyBeUnit()
  
      // Ensure the principal was in fact deleted
      resultingFuture <- testNoSuchPrincipal(getPrincipal(principal))
    } yield resultingFuture
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "modifyNoSuchPrincipal"
    
    for {
      // Ensure the principal does not exist
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
  
      // Try to modify it
      resultingFuture <- testNoSuchPrincipal(modifyPrincipal("", principal))
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    for {
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Modify the principal
      //TODO: test with all the options, maybe property based testing is helpful for this
      _ <- modifyPrincipal("-allow_forwardable", principal).rightValueShouldIdempotentlyBeUnit()
  
      // Ensure it was in fact modified
      resultingFuture <- getPrincipal(principal).rightValue (_.attributes should contain("DISALLOW_FORWARDABLE"))
    } yield resultingFuture
  }

  "withPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "withPrincipalNoSuchPrincipal"
    
    for {
      // Ensure the principal does not exist
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
  
      // Try to get it
      resultingFuture <- testNoSuchPrincipal {
        withPrincipal[Boolean](principal) { e =>
          //Purposefully left empty
        }
      }
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "withPrincipal"
    
    for {
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Read it
      resultingFuture <- withPrincipal[Boolean](principal) { expectBlock =>
        expectBlock.when( """Principal: ([^\n]+)\n""".r)
          .returning { m =>
            Right(m.group(1) == fullPrincipalName(principal))
          }
      } rightValueShouldIdempotentlyBe true
    } yield resultingFuture
  }

  "getPrincipal" should "work when the principal has no keys" in {
    val principal = "getPrincipalNoKeys"
    for {
      // Ensure the principal does not exist
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
  
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Get it
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_.keys shouldBe Set.empty)
    } yield resultingFuture
  }
  it should "work when the principal has no policy" in {
    val principal = "getPrincipalNoPolicy"
    
    for {
      // Ensure the principal does not exist
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
  
      // Ensure the principal exists
      _ <- addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()
  
      // Get it
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_.policy shouldBe None)
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "get"
    val everySaltType = "aes256-cts:normal,aes256-cts:v4,aes256-cts:norealm,aes256-cts:onlyrealm,des:afs3,aes256-cts:special"
    for {
      // Ensure the principal exists. We are using every type of salt to test whether getPrincipal can handle them all.
      _ <- addPrincipal(s"-e $everySaltType", principal, password = Some("a shiny new insecure password")).rightValueShouldBeUnit()

      // Get it
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_ shouldBe a[Principal])
    } yield resultingFuture
  }

  "listPrincipals" should "idempotently succeed" in {
    listPrincipals("*") idempotentRightValue (_ should contain allOf ("noPermissions@EXAMPLE.COM", "kadmin/admin@EXAMPLE.COM"))
  }

  val expireDateTime = new DateTime(DateTimeZone.forID("UTC")).plusHours(2)
  "expirePrincipal" should "idempotently succeed" in {
    // expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"

    for {
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Expire it
      _ <- expirePrincipal(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()
  
      // Ensure the expiration date changed
      resultingFuture <- getPrincipal(principal).rightValue (_.expirationDateTime shouldBe AbsoluteDateTime(expireDateTime))
    } yield resultingFuture
  }
  "expirePrincipalPassword" should "idempotently succeed" in {
    // expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expirePassword"

    for {
      // Ensure the principal exists
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
  
      // Expire the principal password
      _ <- expirePrincipalPassword(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()
  
      // Ensure the password expiration date changed
      resultingFuture <- getPrincipal(principal).rightValue (_.passwordExpirationDateTime shouldBe AbsoluteDateTime(expireDateTime))
    } yield resultingFuture
  }

  // ChangePassword has a dedicated suite since it interlaces with policies
}
