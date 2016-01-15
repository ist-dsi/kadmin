package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{WordSpec, Matchers}

class LackOfPrivilegesSpec extends WordSpec with Matchers with ScalaFutures with TestUtils {
  implicit val defaultPatience = PatienceConfig(
    timeout = Span(1, Seconds),
    interval = Span(2, Seconds)
  )

  val authenticatedConfig = ConfigFactory.parseString(s"""
    kadmin {
      perform-authentication = true

      authenticating-principal = "noPermissions"
      authenticating-principal-password = "MITiys4K5"

      //command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}
    }""")

  val kerberos = new Kadmin(authenticatedConfig/*.resolve()*/)
  import kerberos._

  "An Expect" when {
    "the authenticating principal does not have sufficient permissions" should {
      val principal = "kadmin/admin"
      val options = "-nokey"

      "fail while adding a principal" in {
        testInsufficientPermission("add") {
          addPrincipal(options, principal)
        }
      }

      "fail while deleting a principal" in {
        testInsufficientPermission("delete") {
          deletePrincipal(principal)
        }
      }

      "fail while modifying a principal" in {
        testInsufficientPermission("modify") {
          modifyPrincipal(options, principal)
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
