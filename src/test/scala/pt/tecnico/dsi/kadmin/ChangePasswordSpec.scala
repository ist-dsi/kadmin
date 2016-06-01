package pt.tecnico.dsi.kadmin

import org.scalatest.WordSpec

/**
  * $assumptions
  */
class ChangePasswordSpec extends WordSpec with TestUtils {
  import fullPermissionsKadmin._

  "ChangePassword" when {
    "wrong options are specified" should {
      "throw IllegalArgumentException if no option is specified" in {
        intercept[IllegalArgumentException]{
          changePassword("changePasswordWrongOptions")
        }
      }
      "throw IllegalArgumentException if both newPassword and randkey are defined" in {
        intercept[IllegalArgumentException]{
          changePassword("changePasswordWrongOptions", newPassword = Some("new password"), randKey = true)
        }
      }
      "throw IllegalArgumentException if only keysalt is specified" in {
        intercept[IllegalArgumentException]{
          changePassword("changePasswordWrongOptions", keysalt = Some("aes256-cts-hmac-sha1-96:normal"))
        }
      }
    }
    "the principal does not exits" should {
      "return NoSuchPrincipal when randomizing the keys" in {
        testNoSuchPrincipal {
          changePassword("changePasswordToRandKey", randKey = true)
        }
      }
      "return NoSuchPrincipal when setting the password" in {
        testNoSuchPrincipal {
          changePassword("changePasswordToNewPassword", newPassword = Some("new apssword"))
        }
      }
    }

    "the keys are being randomized" should {
      "idempotently succeed" in {
        val principal = "changePasswordToRandKey"
        addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()
        changePassword(principal, randKey = true).rightValueShouldIdempotentlyBeUnit()
      }
    }

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
