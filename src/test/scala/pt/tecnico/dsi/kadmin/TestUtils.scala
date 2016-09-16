package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{Assertion, AsyncTestSuite, EitherValues, Matchers}
import work.martins.simon.expect.core.Expect

import scala.concurrent.Future

/**
  * @define assumptions These tests make the following assumptions:
  *  - The realm EXAMPLE.COM exists.
  *  - Kerberos client is installed in the machine where the tests are being ran.
  *     And the configuration has the realm EXAMPLE.COM.
  *  - In EXAMPLE.COM KDC the kadm5.acl file has at least the following entries
  *     - kadmin/admin@EXAMPLE.COM  *
  *     - noPermissions@EXAMPLE.COM X
  *  - The password for these two principals is "MITiys4K5".
  *
  * These assumptions are valid when:
  *  - Running the tests locally with docker-compose (look at the folder kerberos-docker).
  *  - Running the tests in the Travis CI (look at .travis.yml, which makes use of the kerberos-docker).
  */
trait TestUtils extends ScalaFutures with Matchers with EitherValues with LazyLogging { self: AsyncTestSuite ⇒
  def createConfigFor(principal: String) = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      principal = "$principal"
      password = "MITiys4K5"
    }
    """)
  val fullPermissionsKadmin = new Kadmin(createConfigFor("kadmin/admin"))
  val noPermissionsKadmin = new Kadmin(createConfigFor("noPermissions"))

  implicit class RichExpect[T](expect: Expect[Either[ErrorCase, T]]) {
    def idempotentTest(test: Either[ErrorCase, T] => Assertion, repetitions: Int = 3): Future[Assertion] = {
      require(repetitions >= 2, "To test for idempotency at least 2 repetitions must be made")
      
      expect.run().flatMap { firstResult ⇒
        //If this fails we do not want to catch its exception, because failing in the first attempt means
        //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
        //the failure with a "Operation is not idempotent".
        test(firstResult)
  
        //This code will only be executed if the previous test succeed.
        //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
        val remainingResults: Future[Seq[Either[ErrorCase, T]]] = Future.sequence {
          (1 until repetitions) map { _ =>
            expect.run()
          }
        }
  
        remainingResults map { results ⇒
          try {
            results.foreach(test)
            succeed
          } catch {
            case e: TestFailedException =>
              val otherResultsString = (1 until repetitions).map { i =>
                f"  $i%2d\t${results(i)}"
              }.mkString("\n")
              throw new TestFailedException(s"""Operation is not idempotent. Results:
                                                |  01:\t$firstResult
                                                |$otherResultsString
                                                |${e.message}""".stripMargin,
                e, e.failedCodeStackDepth + 1)
          }
        }
      }
    }
    def test(test: Either[ErrorCase, T] ⇒ Assertion): Future[Assertion] = expect.run().map(test)
  
    def rightValue(testOnRight: T => Assertion): Future[Assertion] = test(t => testOnRight(t.right.value))
    def leftValue(testOnLeft: ErrorCase => Assertion): Future[Assertion] = test(t => testOnLeft(t.left.value))
    
    def leftValueShouldBe(error: ErrorCase): Future[Assertion] = test(_.left.value shouldBe error)
    def rightValueShouldBe(t: T): Future[Assertion] = test(_.right.value shouldBe t)
    def rightValueShouldBeUnit()(implicit ev: T =:= Unit): Future[Assertion] = test(_.right.value.shouldBe(()))

    def leftValueShouldIdempotentlyBe(leftValue: ErrorCase): Future[Assertion] = idempotentTest(_.left.value shouldBe leftValue)
    def rightValueShouldIdempotentlyBe(rightValue: T): Future[Assertion] = idempotentTest(_.right.value shouldBe rightValue)
    def rightValueShouldIdempotentlyBeUnit()(implicit ev: T =:= Unit): Future[Assertion] = idempotentTest(_.right.value.shouldBe(()))

    def idempotentRightValue(testOnRight: T => Assertion): Future[Assertion] = idempotentTest(t => testOnRight(t.right.value))
  }

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]]): Future[Assertion] = e leftValueShouldIdempotentlyBe NoSuchPrincipal
  def testNoSuchPolicy[R](e: Expect[Either[ErrorCase, R]]): Future[Assertion] = e leftValueShouldIdempotentlyBe NoSuchPolicy
  def testInsufficientPermission[R](permission: String)(e: Expect[Either[ErrorCase, R]]): Future[Assertion] = {
    e leftValueShouldIdempotentlyBe InsufficientPermissions(permission)
  }
}