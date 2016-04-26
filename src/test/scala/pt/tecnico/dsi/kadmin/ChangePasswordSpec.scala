package pt.tecnico.dsi.kadmin

import org.scalatest.WordSpec

/**
  * $assumptions
  */
class ChangePasswordSpec extends WordSpec with TestUtils {
  import fullPermissionsKadmin._

  "ChangePassword" when {
    "the principal has no policy" should {
      "idempotently succeed" in {
        val principal = "changePasswordWithoutPolicy"
        addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()

        //This password is simultaneously very small, with just one character class and it is being reused.
        //But because this principal has no policy this should always return true
        changePassword(principal, newPassword = Some("abc")).rightValueShouldIdempotentlyBeUnit()

        checkPassword(principal, "abc").rightValueShouldBeUnit()
      }
    }
    "the principal has a restrictive policy" should {
      val principal = "changePasswordWithPolicy"
      val policy = "restrictive"
      addPolicy("-minlength 6 -minclasses 3 -history 2", policy).rightValueShouldBeUnit()
      addPrincipal(s"-policy $policy -nokey", principal).rightValueShouldBeUnit()

      "return PasswordTooShort" in {
        changePassword(principal, newPassword = Some("abc")).leftValue shouldBe PasswordTooShort
      }
      "return PasswordWithoutEnoughCharacterClasses" in {
        changePassword(principal, newPassword = Some("abcabcabc")).leftValue shouldBe PasswordWithoutEnoughCharacterClasses
      }
      "return PasswordIsBeingReused" in {
        val firstPassword = "a1b2c3ABC"
        changePassword(principal, newPassword = Some(firstPassword)).rightValueShouldBeUnit()
        changePassword(principal, newPassword = Some("abc1A2B3C")).rightValueShouldBeUnit()
        changePassword(principal, newPassword = Some(firstPassword)).leftValue shouldBe PasswordIsBeingReused
      }
      "succeed if the password is valid according to the policy" in {
        val password = "yey DIDN'T I say we n33ded a new password"
        changePassword(principal, newPassword = Some(password)).rightValueShouldBeUnit()

        checkPassword(principal, password).rightValueShouldBeUnit()
      }
    }
  }
}
