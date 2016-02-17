package pt.tecnico.dsi.kadmin

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{Millis, Seconds, Span}
import work.martins.simon.expect.fluent.Expect
import scala.concurrent.ExecutionContext.Implicits.global

trait TestUtils extends ScalaFutures with Matchers {
  def computePatience(e: Expect[_]): PatienceConfig = PatienceConfig(
    timeout = Span(e.settings.timeout.toSeconds + 2, Seconds),
    interval = Span(500, Millis)
  )
  def runExpect[T](e: Expect[T]): T = e.run().futureValue(computePatience(e))

  def idempotent[T](test: => Expect[T])(expectedResult: T, repetitions: Int = 3): Unit = {
    require(repetitions >= 2, "To test for idempotency at least 2 repetitions must be made")
    //If this fails we do not want to catch its exception, because failing in the first attempt means
    //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
    //the failure with a "Operation is not idempotent".
    val firstResult = runExpect(test)
    firstResult shouldEqual expectedResult

    //This code will only be executed if the previous test succeed.
    //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
    val results = (1 until repetitions).map(_ => runExpect(test))
    try {
      results.foreach(_ shouldEqual expectedResult)
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

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]]): Unit = idempotent(e)(Left(NoSuchPrincipal))
  def testInsufficientPermission[R](permission: String)(e: Expect[Either[ErrorCase, R]]): Unit = {
    idempotent(e)(Left(InsufficientPermissions(permission)))
  }
}