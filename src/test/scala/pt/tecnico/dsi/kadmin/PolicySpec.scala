package pt.tecnico.dsi.kadmin

import org.scalatest.FlatSpec
import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PolicySpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin._

  "addPolicy" should "idempotently succeed" in {
    val policy = "add"
    //Ensure the policy does not exist
    deletePolicy(policy).rightValueShouldBeUnit()

    //Create the policy
    //This also tests adding a policy when a policy already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    addPolicy("-minlength 6 -minclasses 2", policy).rightValueShouldIdempotentlyBeUnit()

    //Ensure it was in fact created
    getPolicy(policy).rightValue.minimumCharacterClasses shouldBe 2
  }

  "deletePolicy" should "idempotently succeed" in {
    val policy = "delete"
    //Ensure the policy exists
    addPolicy("-minlength 6", policy).rightValueShouldBeUnit()

    //Delete the policy
    //This also tests deleting a policy when there is no longer a policy
    deletePolicy(policy).rightValueShouldIdempotentlyBeUnit()

    //Ensure the policy was in fact deleted
    testNoSuchPolicy{
      getPolicy(policy)
    }
  }

  "modifyPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "modifyNoSuchPolicy"
    //Ensure the policy does not exist
    deletePolicy(policy).rightValueShouldBeUnit()

    //Try to modify it
    testNoSuchPolicy {
      modifyPolicy("-minlength 6", policy)
    }
  }
  it should "idempotently succeed" in {
    val policy = "modify"
    val minLength = 9
    //Ensure the policy exists
    addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()

    //Modify the policy
    //TODO: test with all the options, maybe property based testing is helpful for this
    modifyPolicy(s"-minlength $minLength", policy).rightValueShouldIdempotentlyBeUnit()

    //Ensure it was in fact modified
    getPolicy(policy).rightValue.minimumLength shouldBe minLength
  }

  "withPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "withPolicyNoSuchPolicy"
    //Ensure the policy does not exist
    deletePolicy(policy).rightValueShouldBeUnit()

    //Try to get it
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { e =>
        //Purposefully left empty
      }
    }
  }
  it should "idempotently succeed" in {
    val policy = "withPolicy"
    val minLength = 13
    //Ensure the principal exists
    addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()

    //Read it

    withPolicy[Int](policy) { expectBlock =>
      expectBlock.when("""Minimum password length: (\d+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1).toInt)
        }
    } rightValueShouldIdempotentlyBe minLength
  }

  "getPolicy" should "idempotently succeed" in {
    val policy = "get"
    val minLength = 9
    //Ensure the policy exists
    addPolicy(s"-minlength $minLength", policy).rightValueShouldBeUnit()

    //Get it
    getPolicy(policy) idempotentRightValue (_.minimumLength shouldBe minLength)

  }
}
