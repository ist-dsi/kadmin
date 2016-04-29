package pt.tecnico.dsi.kadmin

import scala.concurrent.duration.DurationInt
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{EitherValues, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{Millis, Seconds, Span}
import work.martins.simon.expect.fluent.Expect

import scala.concurrent.ExecutionContext.Implicits.global

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
trait TestUtils extends ScalaFutures with Matchers with EitherValues with LazyLogging {
  def createConfigFor(principal: String) = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"

      authenticating-principal = "$principal"
      authenticating-principal-password = "MITiys4K5"

      command-with-authentication = ["kadmin", "-p", "$$FULL_PRINCIPAL"]
    }""")
  val fullPermissionsKadmin = new Kadmin(createConfigFor("kadmin/admin"))
  val noPermissionsKadmin = new Kadmin(createConfigFor("noPermissions"))

  def idempotent[T](expect: Expect[Either[ErrorCase, T]], repetitions: Int = 3)(test: Either[ErrorCase, T] => Unit): Unit = {
    require(repetitions >= 2, "To test for idempotency at least 2 repetitions must be made")
    //If this fails we do not want to catch its exception, because failing in the first attempt means
    //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
    //the failure with a "Operation is not idempotent".
    val firstResult = expect.value
    test(firstResult)

    //This code will only be executed if the previous test succeed.
    //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
    val results = (1 until repetitions).map(_ => expect.value)
    try {
      results.foreach(test)
    } catch {
      case e: TestFailedException =>
        throw new TestFailedException(s"""Operation is not idempotent. Results:
                                          |  1st:
                                          |    $firstResult
                                          |  Rest:
                                          |    ${results.mkString("\n    ")}
                                          |${e.message}""".stripMargin,
          e, e.failedCodeStackDepth + 1)
    }
  }

  implicit class RichExpect[T](expect: Expect[Either[ErrorCase, T]]) {
    def leftValue: ErrorCase = value.left.value
    def rightValue: T = value.right.value
    def rightValueShouldBeUnit(): Unit = rightValue.shouldBe(())

    def leftValueShouldIdempotentlyBe(leftValue: ErrorCase): Unit = idempotent(expect)(_.left.value shouldBe leftValue)
    def rightValueShouldIdempotentlyBe(rightValue: T): Unit = idempotent(expect)(_.right.value shouldBe rightValue)
    def rightValueShouldIdempotentlyBeUnit(): Unit = idempotent(expect)(_.right.value.shouldBe(()))

    def idempotentRightValue(rightValue: T => Unit): Unit = idempotent(expect)(t => rightValue(t.right.value))

    def value: Either[ErrorCase, T] = expect.run().futureValue(PatienceConfig(
      timeout = scaled(expect.settings.timeout),
      interval = scaled(500.millis)
    ))
  }

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]]): Unit = e leftValueShouldIdempotentlyBe NoSuchPrincipal
  def testNoSuchPolicy[R](e: Expect[Either[ErrorCase, R]]): Unit = e leftValueShouldIdempotentlyBe NoSuchPolicy
  def testInsufficientPermission[R](permission: String)(e: Expect[Either[ErrorCase, R]]): Unit = {
    e leftValueShouldIdempotentlyBe InsufficientPermissions(permission)
  }
}