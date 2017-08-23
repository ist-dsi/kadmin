package pt.tecnico.dsi.kadmin

import org.scalatest.AsyncFlatSpec
import scala.concurrent.duration.DurationInt

/**
  * $assumptions
  */
class PolicySpec extends AsyncFlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPolicy" should "idempotently succeed" in {
    val policy = "add"
    for {
      _ <- deletePolicy(policy).rightValueShouldBeUnit()
      // This also tests adding a policy when a policy already exists
      // TODO: test with all the options, maybe property based testing is helpful for this
      _ <- addPolicy("-minlength 6 -minclasses 2", policy).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPolicy(policy).rightValue (_.minimumCharacterClasses shouldBe 2)
    } yield resultingFuture
  }

  "deletePolicy" should "idempotently succeed" in {
    val policy = "delete"
    for {
      _ <- addPolicy("-minlength 6", policy).rightValueShouldBeUnit()
      // This also tests deleting a policy when there is no longer a policy
      _ <- deletePolicy(policy).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- testNoSuchPolicy(getPolicy(policy))
    } yield resultingFuture
  }

  "modifyPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "modifyNoSuchPolicy"
    for {
      _ <- deletePolicy(policy).rightValueShouldBeUnit()
      resultingFuture <- testNoSuchPolicy(modifyPolicy("-minlength 6", policy))
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val policy = "modify"
    val minLength = 9
    for {
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
      // TODO: test with all the options, maybe property based testing is helpful for this
      _ <- modifyPolicy(s"-minlength $minLength", policy).rightValueShouldIdempotentlyBeUnit()
      resultingFuture <- getPolicy(policy) rightValue (_.minimumLength shouldBe minLength)
    } yield resultingFuture
  }

  "withPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "withPolicyNoSuchPolicy"
    deletePolicy(policy).rightValueShouldBeUnit()
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { _ => /*Purposefully left empty*/ }
    }
  }
  it should "idempotently succeed" in {
    import scala.util.matching.Regex.Match

    val policy = "withPolicy"
    val minLength = 13
    
    for {
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
      resultingFuture <- withPolicy[Int](policy) { expectBlock =>
        expectBlock.when( """Minimum password length: (\d+)\n""".r)
          .returning { m: Match =>
            Right(m.group(1).toInt)
          }
      } rightValueShouldIdempotentlyBe minLength
    } yield resultingFuture
  }

  "getPolicy" should "idempotently succeed with options" in {
    val policy = "getOptions"
    val minLength = 9
    for {
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
      resultingFuture <- getPolicy(policy) idempotentRightValue (_.minimumLength shouldBe minLength)
    } yield resultingFuture
  }
  it should "idempotently succeed with domain class Policy" in {
    val (minLength, minCharacterClasses, oldKeysKept, maxFailuresBeforeLockout) = (9, 2, 1, 3)
    val (maxLife, minLife, failureCountResetInterval, lockoutDuration) = (50.days, 5.days, 1.day, 1.day)
    // We might as well test the keysalts at the same time
    val allowedKeysalts = Some(Set(
      KeySalt.fromString("aes256-cts:normal"),
      KeySalt.fromString("aes256-cts:v4"),
      KeySalt.fromString("aes256-cts:norealm"),
      KeySalt.fromString("aes256-cts:onlyrealm"),
      KeySalt.fromString("aes256-cts:afs3"),
      KeySalt.fromString("aes256-cts:special"),
      KeySalt.fromString("aes256-cts:norealm"),
    ).collect { case Some(k) => k })

    val policy = Policy("getDomainClass", maxLife, minLife, minLength, minCharacterClasses, oldKeysKept, maxFailuresBeforeLockout,
    failureCountResetInterval, lockoutDuration, allowedKeysalts)

    for {
      _ <- addPolicy(policy).rightValueShouldBeUnit()
      resultingFuture <- getPolicy(policy.name) idempotentRightValue (_ shouldEqual policy)
    } yield resultingFuture
  }

  "listPolicies" should "idempotently succeed" in {
    for {
      _ <- addPolicy(Policy(name = "first", 50.days, 5.days, 5, 2, 1)).rightValueShouldBeUnit()
      _ <- addPolicy(Policy(name = "second", 50.days, 5.days, 5, 2, 1)).rightValueShouldBeUnit()
      _ <- addPolicy(Policy(name = "third", 50.days, 5.days, 5, 2, 1)).rightValueShouldBeUnit()
      resultingFuture <- listPolicies("*") idempotentRightValue (_ should contain allOf ("first", "second", "third"))
    } yield resultingFuture
  }
}

