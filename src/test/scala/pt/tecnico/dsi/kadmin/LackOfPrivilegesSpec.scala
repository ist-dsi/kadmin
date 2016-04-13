package pt.tecnico.dsi.kadmin

import org.scalatest.WordSpec

/**
  * $assumptions
  */
class LackOfPrivilegesSpec extends WordSpec with TestUtils {
  import noPermissionsKadmin._

  "An Expect" when {
    "the authenticating principal does not have sufficient permissions" should {
      val principal = "kadmin/admin"

      "fail while adding a principal" in {
        testInsufficientPermission("add") {
          addPrincipal(options = "-nokey", principal = "random")
        }
      }

      "fail while deleting a principal" in {
        testInsufficientPermission("delete") {
          deletePrincipal(principal)
        }
      }

      "fail while modifying a principal" in {
        testInsufficientPermission("get") {
          modifyPrincipal(options = "", principal)
        }
      }

      "fail while changing a principal password" in {
        testInsufficientPermission("change-password") {
          changePassword(principal, newPassword = Some("a super shiny new pa$$w0rd"))
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
