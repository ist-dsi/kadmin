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
        addPrincipal("-clearpolicy -nokey", principal) shouldReturn Right(Unit)

        //This password is simultaneously very small, with just one character class and it is being reused.
        //But because this principal has no policy this should always return true
        changePassword(principal, newPassword = Some("abc")) shouldIdempotentlyReturn Right(Unit)
        
        checkPassword(principal, "abc") shouldReturn Right(Unit)
      }
    }
    "the principal has a restrictive policy" should {
      val principal = "changePasswordWithPolicy"
      val policy = "restrictive"
      addPolicy("-minlength 6 -minclasses 3 -history 2", policy) shouldReturn Right(Unit)
      addPrincipal(s"-policy $policy -nokey", principal) shouldReturn Right(Unit)

      "return PasswordTooShort" in {
        changePassword(principal, newPassword = Some("abc")) shouldReturn Left(PasswordTooShort)
      }
      "return PasswordWithoutEnoughCharacterClasses" in {
        changePassword(principal, newPassword = Some("abcabcabc")) shouldReturn Left(PasswordWithoutEnoughCharacterClasses)
      }
      "return PasswordIsBeingReused" in {
        val firstPassword = "a1b2c3ABC"
        changePassword(principal, newPassword = Some(firstPassword)) shouldReturn Right(Unit)
        changePassword(principal, newPassword = Some("abc1A2B3C")) shouldReturn Right(Unit)
        changePassword(principal, newPassword = Some(firstPassword)) shouldReturn Left(PasswordIsBeingReused)
      }
      "should succeed if the password is valid according to the policy" in {
        val password = "yey DIDN'T I say we n33ded a new password"
        changePassword(principal, newPassword = Some(password)) shouldReturn Right(Unit)

        checkPassword(principal, password) shouldReturn Right(Unit)
      }
    }
  }
}
