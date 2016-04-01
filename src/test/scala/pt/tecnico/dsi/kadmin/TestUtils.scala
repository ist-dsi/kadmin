package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{Millis, Seconds, Span}
import work.martins.simon.expect.fluent.Expect
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

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
trait TestUtils extends ScalaFutures with Matchers {
  def createConfigFor(principal: String) = ConfigFactory.parseString(s"""
    kadmin {
      perform-authentication = true

      realm = "EXAMPLE.COM"

      authenticating-principal = "$principal"
      authenticating-principal-password = "MITiys4K5"

      command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}"@"$${kadmin.realm}
    }""")
  val authenticatedKadmin = new Kadmin(createConfigFor("kadmin/admin").resolve())
  val unAuthenticatedKadmin = new Kadmin(createConfigFor("noPermissions").resolve())

  implicit class RichExpect[T](expect: Expect[T]) {
    def shouldIdempotentlyReturn(expectedResult: T, repetitions: Int = 3): Unit = {
      require(repetitions >= 2, "To test for idempotency at least 2 repetitions must be made")
      //If this fails we do not want to catch its exception, because failing in the first attempt means
      //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
      //the failure with a "Operation is not idempotent".
      val firstResult = shouldReturn(expectedResult)

      //This code will only be executed if the previous test succeed.
      //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
      val results = (1 until repetitions).map(_ => value)
      try {
        results.foreach(_ shouldBe expectedResult)
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
    def shouldReturn(expectedResult: T): Unit = value shouldBe expectedResult
    def value: T = expect.run().futureValue(new PatienceConfig(
      timeout = Span(expect.settings.timeout.toSeconds + 1, Seconds),
      interval = Span(500, Millis)
    ))
  }

  def policyMinimumLength(kadmin: Kadmin, policy: String): Expect[Either[ErrorCase, Int]] = {
    kadmin.withPolicy[Int](policy) { expectBlock =>
      expectBlock.when("""Minimum password length: (\d+)\n""".r)
        .returning { m: Match =>
          Right(m.group(1).toInt)
        }
    }
  }

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]]): Unit = {
    e shouldIdempotentlyReturn Left(NoSuchPrincipal)
  }
  def testNoSuchPolicy[R](e: Expect[Either[ErrorCase, R]]): Unit = {
    e shouldIdempotentlyReturn Left(NoSuchPolicy)
  }
  def testInsufficientPermission[R](permission: String)(e: Expect[Either[ErrorCase, R]]): Unit = {
    e shouldIdempotentlyReturn Left(InsufficientPermissions(permission))
  }
}