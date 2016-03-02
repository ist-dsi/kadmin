package pt.tecnico.dsi.kadmin

import org.scalatest.WordSpec

/**
  * $assumptions
  */
class ChangePasswordSpec extends WordSpec with TestUtils {
  import authenticatedKadmin._

  "ChangePassword" when {
    "the principal has no policy" should {
      "idempotently succeed" in {
        val principal = "changePasswordWithoutPolicy"
        addPrincipal("-clearpolicy -nokey", principal) shouldReturn Right(true)

        //This password is simultaneously very small, with just one character class and it is being reused.
        //But because this principal has no policy this should always return true
        changePassword(principal, newPassword = Some("abc")) shouldIdempotentlyReturn Right(true)

        checkPassword(principal, "abc") shouldReturn Right(true)
      }
    }
    "the principal has a restrictive policy" should {
      val principal = "changePasswordWithPolicy"
      val policy = "restrictive"
      addPolicy("-minlength 6 -minclasses 3 -history 2", policy) shouldReturn Right(true)
      addPrincipal(s"-policy $policy -nokey", principal) shouldReturn Right(true)

      "return PasswordTooShort" in {
        changePassword(principal, newPassword = Some("abc")) shouldReturn Left(PasswordTooShort)
      }
      "return PasswordWithoutEnoughCharacterClasses" in {
        changePassword(principal, newPassword = Some("abcabcabc")) shouldReturn Left(PasswordWithoutEnoughCharacterClasses)
      }
      "return PasswordIsBeingReused" in {
        val firstPassword = "a1b2c3ABC"
        changePassword(principal, newPassword = Some(firstPassword)) shouldReturn Right(true)
        changePassword(principal, newPassword = Some("abc1A2B3C")) shouldReturn Right(true)
        changePassword(principal, newPassword = Some(firstPassword)) shouldReturn Left(PasswordIsBeingReused)
      }
      "should succeed if the password is valid according to the policy" in {
        val password = "yey DIDN'T I say we n33ded a new password"
        changePassword(principal, newPassword = Some(password)) shouldReturn Right(true)

        checkPassword(principal, password) shouldReturn Right(true)
      }
    }
  }
}
