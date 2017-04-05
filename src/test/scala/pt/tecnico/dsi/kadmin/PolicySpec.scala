package pt.tecnico.dsi.kadmin

import org.scalatest.AsyncFlatSpec
import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PolicySpec extends AsyncFlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPolicy" should "idempotently succeed" in {
    val policy = "add"
    for {
      // Ensure the policy does not exist
      _ <- deletePolicy(policy).rightValueShouldBeUnit()
    
      // Create the policy
      // This also tests adding a policy when a policy already exists
      // TODO: test with all the options, maybe property based testing is helpful for this
      _ <- addPolicy("-minlength 6 -minclasses 2", policy).rightValueShouldIdempotentlyBeUnit()

      // Ensure it was in fact created
      resultingFuture <- getPolicy(policy).rightValue (_.minimumCharacterClasses shouldBe 2)
    } yield resultingFuture
  }

  "deletePolicy" should "idempotently succeed" in {
    val policy = "delete"
    for {
      // Ensure the policy exists
      _ <- addPolicy("-minlength 6", policy).rightValueShouldBeUnit()

      // Delete the policy
      // This also tests deleting a policy when there is no longer a policy
      _ <- deletePolicy(policy).rightValueShouldIdempotentlyBeUnit()

      // Ensure the policy was in fact deleted
      resultingFuture <- testNoSuchPolicy(getPolicy(policy))
    } yield resultingFuture
  }

  "modifyPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "modifyNoSuchPolicy"
    for {
      // Ensure the policy does not exist
      _ <- deletePolicy(policy).rightValueShouldBeUnit()
  
      // Try to modify it
      resultingFuture <- testNoSuchPolicy(modifyPolicy("-minlength 6", policy))
    } yield resultingFuture
  }
  it should "idempotently succeed" in {
    val policy = "modify"
    val minLength = 9
    for {
      // Ensure the policy exists
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
  
      // Modify the policy
      // TODO: test with all the options, maybe property based testing is helpful for this
      _ <- modifyPolicy(s"-minlength $minLength", policy).rightValueShouldIdempotentlyBeUnit()
  
      //Ensure it was in fact modified
      resultingFuture <- getPolicy(policy) rightValue (_.minimumLength shouldBe minLength)
    } yield resultingFuture
  }

  "withPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "withPolicyNoSuchPolicy"
    // Ensure the policy does not exist
    deletePolicy(policy).rightValueShouldBeUnit()

    // Try to get it
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { e =>
        //Purposefully left empty
      }
    }
  }
  it should "idempotently succeed" in {
    val policy = "withPolicy"
    val minLength = 13
    
    for {
      // Ensure the principal exists
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
  
      // Read it
      resultingFuture <- withPolicy[Int](policy) { expectBlock =>
        expectBlock.when( """Minimum password length: (\d+)\n""".r)
          .returning { m: Match =>
            Right(m.group(1).toInt)
          }
      } rightValueShouldIdempotentlyBe minLength
    } yield resultingFuture
  }

  "getPolicy" should "idempotently succeed" in {
    val policy = "get"
    val minLength = 9
    
    for {
      // Ensure the policy exists
      _ <- addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()
  
      // Get it
      resultingFuture <- getPolicy(policy) idempotentRightValue (_.minimumLength shouldBe minLength)
    } yield resultingFuture

  }
}
