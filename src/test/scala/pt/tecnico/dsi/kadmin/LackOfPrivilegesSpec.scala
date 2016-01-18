package pt.tecnico.dsi.kadmin

import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{WordSpec, Matchers}

import scala.concurrent.ExecutionContext

class LackOfPrivilegesSpec extends WordSpec with Matchers with ScalaFutures with TestUtils {
  implicit val defaultPatience = PatienceConfig(
    timeout = Span(1, Seconds),
    interval = Span(2, Seconds)
  )

  val authenticatedConfig = ConfigFactory.parseString(s"""
    kadmin {
      perform-authentication = true

      realm = "EXAMPLE.COM"

      authenticating-principal = "noPermissions"
      authenticating-principal-password = "MITiys4K5"

      command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}"@"$${kadmin.realm}
    }""")

  val kerberos = new Kadmin(authenticatedConfig.resolve())
  import kerberos._

  println(kerberos.settings)

  "An Expect" when {
    "the authenticating principal does not have sufficient permissions" should {
      val principal = "kadmin/admin"

      "fail while adding a principal" in {
        testInsufficientPermission("add") {
          addPrincipal("-randkey", "random")
        }
      }

      "fail while deleting a principal" in {
        testInsufficientPermission("delete") {
          deletePrincipal(principal)
        }
      }

      "fail while modifying a principal" in {
        testInsufficientPermission("modify") {
          modifyPrincipal("-randkey", principal)
        }
      }

      "fail while changing a principal password" in {
        testInsufficientPermission("changepw") {
          changePassword(principal, "a super shiny new pa$$w0rd")
        }
      }

      "fail while getting a principal" in {
        testInsufficientPermission("inquire") {
          withPrincipal[Boolean](principal) { e =>
            //Purposefully left empty
          }
        }
      }
    }
  }
}
