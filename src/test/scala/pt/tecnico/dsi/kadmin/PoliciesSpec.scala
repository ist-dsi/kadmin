package pt.tecnico.dsi.kadmin

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

import scala.util.matching.Regex.Match

/**
  * $assumptions
  */
class PoliciesSpec extends FlatSpec with TestUtils with LazyLogging {
  import authenticatedKadmin._


  "addPolicy" should "idempotently succeed" in {
    val policy = "add"
    runExpect(deletePolicy(policy)) shouldBe Right(true)
    //This also tests adding a policy when a policy already exists
    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      addPolicy("-minlength 6 -minclasses 2", policy)
    }(Right(true))
  }

  "deletePolicy" should "idempotently succeed" in {
    val policy = "delete"
    runExpect(addPolicy("-minlength 6", policy)) shouldBe Right(true)
    //This also tests deleting a policy when there is no longer a policy
    idempotent {
      deletePolicy(policy)
    }(Right(true))
  }

  "modifyPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "modifyNoSuchPolicy"
    runExpect(deletePolicy(policy)) shouldBe Right(true)
    testNoSuchPolicy {
      modifyPolicy("-minlength 6", policy)
    }
  }
  it should "idempotently succeed" in {
    val policy = "modify"
    runExpect(addPolicy("-minlength 6", policy)) shouldBe Right(true)
    runExpect(policyMinimumLength(authenticatedKadmin, policy)) shouldBe Right(6)

    //TODO: test with all the options, maybe property based testing is helpful for this
    idempotent {
      modifyPolicy("-minlength 9", policy)
    }(Right(true))

    runExpect(policyMinimumLength(authenticatedKadmin, policy)) shouldBe Right(9)
  }

  "getPolicy" should "return NoSuchPolicy when the policy does not exists" in {
    val policy = "get"
    runExpect(deletePolicy(policy)) shouldBe Right(true)
    testNoSuchPolicy {
      withPolicy[Boolean](policy) { e =>
        //Purposefully left empty
      }
    }
  }
  

}
