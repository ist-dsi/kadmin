package pt.tecnico.dsi.kadmin

import org.scalatest.AsyncWordSpec

/**
  * $assumptions
  */
class LackOfPrivilegesSpec extends AsyncWordSpec with TestUtils {
  import noPermissionsKadmin._

  "Kadmin" when {
    "the authenticating principal does not have sufficient permissions" should {
      val principal = "kadmin/admin"

      "fail while adding a principal" in {
        testInsufficientPermission("add") {
          addPrincipal("-nokey", principal = "random")
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
      "fail when randomizing the principal keys" in {
        testInsufficientPermission("change-password") {
          changePassword(principal, randKey = true)
        }
      }

      "fail while getting a principal" in {
        testInsufficientPermission("get") {
          withPrincipal[Boolean](principal) { e =>
            //Purposefully left empty
          }
        }
      }

      "fail while listings principals" in {
        testInsufficientPermission("list") {
          listPrincipals("")
        }
      }
    }
  }
}
