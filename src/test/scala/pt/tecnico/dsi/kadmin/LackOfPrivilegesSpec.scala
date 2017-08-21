package pt.tecnico.dsi.kadmin

import org.scalatest.AsyncFreeSpec
import work.martins.simon.expect.core.Expect

/**
  * $assumptions
  */
class LackOfPrivilegesSpec extends AsyncFreeSpec with TestUtils {
  import noPermissionsKadmin._

  "When the authenticating principal does not have sufficient permissions" - {
    "each operation should fail with insufficient permissions" - {
      "principal operations" - {
        val principal = "random"
        Map[String, Expect[Either[ErrorCase, _]]](
          "add" -> addPrincipal("-nokey", principal),
          "delete" -> deletePrincipal(principal),
          // There is a very minor security flaw in change password, if you try to change the password for a non
          // existing principal having logged in with a principal without any permissions you will get the
          // principal does not exist before you get "Operation requires ... privilege". So we need to pass in a
          // valid principal.
          "change-password" -> changePassword("kadmin/admin", newPassword = Some("a super shiny new pa$$w0rd")),
          // The documentation says "This command requires the inquire privilege" however the code says otherwise.
          "get" -> withPrincipal[Boolean](principal) { _ => /*Purposefully left empty*/ },
          "list" -> listPrincipals(""),
        ) foreach { case (privilege, operation) =>
          privilege in {
            testInsufficientPermission(privilege)(operation)
          }
        }

        "modifying a principal" in {
          // The documentation says "This command requires the modify privilege." however the code says otherwise.
          // Maybe it needs both the get and the modify privilege
          testInsufficientPermission("get") {
            modifyPrincipal(options = "", principal)
          }
        }

        "randomizing the principal keys" in {
          testInsufficientPermission("change-password") {
            changePassword("kadmin/admin", randKey = true)
          }
        }
      }

      "policy operations" - {
        val policy = "inexisting"
        Map(
          "add" -> addPolicy("-minlength 5", policy),
          "delete" -> deletePolicy(policy),
          "modify" -> modifyPolicy(options = "", policy),
          "get" -> withPolicy[Boolean](policy) { _ => /*Purposefully left empty*/ },
          "list" -> listPolicies(""),
        ) foreach { case (privilege, operation) =>
          privilege in {
            testInsufficientPermission(privilege)(operation)
          }
        }
      }
    }
  }
}
