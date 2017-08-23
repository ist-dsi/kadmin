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
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      // This also tests deleting a principal when there is no longer a principal
      _ <- deletePrincipal(principal).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- testNoSuchPrincipal(getPrincipal(principal))
    } yield resultingFuture
  }

  "modifyPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "modifyNoSuchPrincipal"
    
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      resultingFuture <- testNoSuchPrincipal(modifyPrincipal("", principal))
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "modify"
    for {
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      //TODO: test with all the options, maybe property based testing is helpful for this
      _ <- modifyPrincipal("-allow_forwardable", principal).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPrincipal(principal).rightValue (_.attributes should contain("DISALLOW_FORWARDABLE"))
    } yield resultingFuture
  }

  "withPrincipal" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "withPrincipalNoSuchPrincipal"
    
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      resultingFuture <- testNoSuchPrincipal {
        withPrincipal[Boolean](principal){ _ => /*Purposefully left empty*/ }
      }
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "withPrincipal"
    
    for {
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      resultingFuture <- withPrincipal[Boolean](principal) { expectBlock =>
        expectBlock.when( """Principal: ([^\n]+)\n""".r)
          .returning(m => Right(m.group(1) == fullPrincipalName(principal)))
      } rightValueShouldIdempotentlyBe true
    } yield resultingFuture
  }

  "getPrincipal" should "work when the principal has no keys" in {
    val principal = "getPrincipalNoKeys"
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_.keys shouldBe Set.empty)
    } yield resultingFuture
  }
  it should "work when the principal has no policy" in {
    val principal = "getPrincipalNoPolicy"
    
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      _ <- addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_.policy shouldBe None)
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val principal = "get"
    // We are using every type of salt to test whether getPrincipal can handle them all.
    val everySaltType = "aes256-cts:normal,aes256-cts:v4,aes256-cts:norealm,aes256-cts:onlyrealm,des:afs3,aes256-cts:special"
    for {
      _ <- addPrincipal(s"-e $everySaltType", principal, password = Some("a shiny new insecure password")).rightValueShouldBeUnit()
      resultingFuture <- getPrincipal(principal) idempotentRightValue (_ shouldBe a[Principal])
    } yield resultingFuture
  }

  "listPrincipals" should "idempotently succeed" in {
    listPrincipals("*") idempotentRightValue (_ should contain allOf ("noPermissions@EXAMPLE.COM", "kadmin/admin@EXAMPLE.COM"))
  }

  val expireDateTime = new DateTime(DateTimeZone.forID("UTC")).plusHours(2)
  "expirePrincipal" should "idempotently succeed" in {
    val principal = "expire"

    for {
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      _ <- expirePrincipal(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPrincipal(principal).rightValue (_.expirationDateTime shouldBe AbsoluteDateTime(expireDateTime))
    } yield resultingFuture
  }
  "expirePrincipalPassword" should "idempotently succeed" in {
    val principal = "expirePassword"

    for {
      _ <- addPrincipal("-nokey", principal).rightValueShouldBeUnit()
      _ <- expirePrincipalPassword(principal, expireDateTime).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPrincipal(principal).rightValue (_.passwordExpirationDateTime shouldBe AbsoluteDateTime(expireDateTime))
    } yield resultingFuture
  }

  // ChangePassword has a dedicated suite since it interlaces with policies
}
