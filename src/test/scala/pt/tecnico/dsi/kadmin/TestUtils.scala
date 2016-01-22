package pt.tecnico.dsi.kadmin

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import work.martins.simon.expect.fluent.Expect
import scala.concurrent.ExecutionContext.Implicits.global

trait TestUtils { self: ScalaFutures with Matchers =>
  /**
    * Tests that the operation in `test` is idempotent by repeating `test` three times.
    * If `test` fails on the second or third time a `TestFailedException` exception will
    * be thrown stating `test` is not idempotent.
    *
    * @example {{{
    *  val initialSet = Set(1, 2, 3)
    *  idempotent {
    *   (initialSet + 4) shouldBe Set(1, 2, 3, 4)
    *  }
    * }}}
    */
  def idempotent(test: => Unit): Unit = {
    //If this fails we do not want to catch its exception, because failing in the first attempt means
    //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
    //the failure with a "Operation is not idempotent".
    test

    //This code will only be executed if the previous test succeed.
    //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
    try {
      test
      test
    } catch {
      case e: TestFailedException =>
        throw new TestFailedException("Operation is not idempotent." + e.message, e, e.failedCodeStackDepth + 1)
    }
  }

  def idempotent2[T](expectedResult: T, repetitions: Int = 3)(test: => T): Unit = {
    require(repetitions >= 2, "To test for idempotency at least 2 repetitions must be made")
    //If this fails we do not want to catch its exception, because failing in the first attempt means
    //whatever is being tested in `test` is not implemented correctly. Therefore we do not want to mask
    //the failure with a "Operation is not idempotent".
    val firstResult = test
    firstResult shouldBe expectedResult

    //This code will only be executed if the previous test succeed.
    //And now we want to catch the exception because if `test` fails here it means it is not idempotent.
    val results = (1 until repetitions).map(_ => test)
    try {
      results.foreach(_ shouldBe expectedResult)
    } catch {
      case e: TestFailedException =>
        throw new TestFailedException(s"""Operation is not idempotent. Results:
                                         |$firstResult
                                         |${results.mkString("\n")}
                                         |${e.message}""".stripMargin,
          e, e.failedCodeStackDepth + 1)
    }
  }

  def testNoSuchPrincipal[R](e: Expect[Either[ErrorCase, R]])(implicit patience: PatienceConfig) = idempotent {
    e.run().futureValue(patience) shouldBe Left(NoSuchPrincipal)
  }

  def testInsufficientPermission[R](permission: String)(e: Expect[Either[ErrorCase, R]])
                                   (implicit patience: PatienceConfig) = idempotent {
    e.run().futureValue(patience) shouldBe Left(InsufficientPermissions(permission))
  }
}
