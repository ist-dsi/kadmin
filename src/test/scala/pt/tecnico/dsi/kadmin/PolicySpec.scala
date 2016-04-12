package pt.tecnico.dsi.kadmin

import org.scalatest.FlatSpec
import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PolicySpec extends FlatSpec with TestUtils {
  import authenticatedKadmin._

  "addPolicy" should "idempotently succeed" in {
    val policy = "add"
    //Ensure the policy does not exist
    deletePolicy(policy) shouldReturn Right(Unit)

    //Create the policy
    //This also tests adding a policy when a policy already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    addPolicy("-minlength 6 -minclasses 2", policy) shouldIdempotentlyReturn Right(Unit)

    //Ensure it was in fact created
    withPolicy[Unit](policy){ expectBlock =>
      expectBlock.when("""Policy: ([^\n]+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1) == policy)
        }
    } shouldReturn Right(Unit)
  }

  "deletePolicy" should "idempotently succeed" in {
    val policy = "delete"
    //Ensure the policy exists
    addPolicy("-minlength 6", policy) shouldReturn Right(Unit)

    //Delete the policy
    //This also tests deleting a policy when there is no longer a policy
    deletePolicy(policy) shouldIdempotentlyReturn Right(Unit)

    //Ensure the policy was in fact deleted
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { e =>
        //Purposefully left empty
      }
    }
  }

  "modifyPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "modifyNoSuchPolicy"
    //Ensure the policy does not exist
    deletePolicy(policy) shouldReturn Right(Unit)

    //Try to modify it
    testNoSuchPolicy {
      modifyPolicy("-minlength 6", policy)
    }
  }
  it should "idempotently succeed" in {
    val policy = "modify"
    val minLength = 9
    //Ensure the policy exists
    addPolicy("-minlength 6", policy) shouldReturn Right(Unit)

    //Modify the policy
    //TODO: test with all the options, maybe property based testing is helpful for this
    modifyPolicy(s"-minlength $minLength", policy) shouldIdempotentlyReturn Right(Unit)

    //Ensure it was in fact modified
    policyMinimumLength(authenticatedKadmin, policy) shouldReturn Right(minLength)
  }

  "getPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "getNoSuchPolicy"
    //Ensure the policy does not exist
    deletePolicy(policy) shouldReturn Right(Unit)

    //Try to get it
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { e =>
        //Purposefully left empty
      }
    }
  }
  it should "idempotently succeed" in {
    val policy = "get"
    val minLength = 13
    //Ensure the principal exists
    addPolicy(s"-minlength $minLength", policy) shouldReturn Right(Unit)

    //Read it
    policyMinimumLength(authenticatedKadmin, policy) shouldReturn Right(minLength)
  }
}
