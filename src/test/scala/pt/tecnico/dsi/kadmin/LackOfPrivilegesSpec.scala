package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

class LackOfPrivilegesSpec extends WordSpec with TestUtils {
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

  //println(kerberos.settings)

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
        testInsufficientPermission("get") {
          modifyPrincipal("", principal)
        }
      }

      "fail while changing a principal password" in {
        testInsufficientPermission("change-password") {
          changePassword(principal, "a super shiny new pa$$w0rd")
        }
      }

      "fail while getting a principal" in {
        testInsufficientPermission("get") {
          withPrincipal[Boolean](principal) { e =>
            //Purposefully left empty
          }
        }
      }
    }
  }
}
