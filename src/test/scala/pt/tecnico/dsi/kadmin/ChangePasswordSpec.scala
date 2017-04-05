package pt.tecnico.dsi.kadmin

import org.scalatest.AsyncWordSpec

/**
  * $assumptions
  */
class ChangePasswordSpec extends AsyncWordSpec with TestUtils {
  import fullPermissionsKadmin._

  "ChangePassword" when {
    "wrong options are specified" should {
      "throw IllegalArgumentException if no option is specified" in {
        assertThrows[IllegalArgumentException] {
          changePassword("changePasswordWrongOptions")
        }
      }
      "throw IllegalArgumentException if both newPassword and randkey are defined" in {
        assertThrows[IllegalArgumentException]{
          changePassword("changePasswordWrongOptions", newPassword = Some("new password"), randKey = true)
        }
      }
      "throw IllegalArgumentException if only keysalt is specified" in {
        assertThrows[IllegalArgumentException]{
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
  
        for {
          _ <- addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()
          resultingFuture <- changePassword(principal, randKey = true).rightValueShouldIdempotentlyBeUnit()
        } yield resultingFuture
      }
    }

    "the principal has no policy" should {
      "idempotently succeed" in {
        val principal = "changePasswordWithoutPolicy"
        for {
          _ <- addPrincipal("-clearpolicy -nokey", principal).rightValueShouldBeUnit()
          // This password is simultaneously very small, with just one character class and it is being reused.
          // But because this principal has no policy this should always return unit
          _ <- changePassword(principal, newPassword = Some("abc")).rightValueShouldIdempotentlyBeUnit()
          resultingFuture <- checkPassword(principal, "abc").rightValueShouldBeUnit()
        } yield resultingFuture
      }
    }
    "the principal has a restrictive policy" should {
      val principal = "changePasswordWithPolicy"
      val policy = "restrictive"
      for {
        _ <- addPolicy("-minlength 6 -minclasses 3 -history 2", policy).rightValueShouldBeUnit()
        resultingFuture <- addPrincipal(s"-policy $policy -nokey", principal).rightValueShouldBeUnit()
      } yield resultingFuture
      
      "return PasswordTooShort" in {
        changePassword(principal, newPassword = Some("abc")) leftValueShouldBe PasswordTooShort
      }
      "return PasswordWithoutEnoughCharacterClasses" in {
        changePassword(principal, newPassword = Some("abcabcabc")) leftValueShouldBe PasswordWithoutEnoughCharacterClasses
      }
      "return PasswordIsBeingReused" in {
        val firstPassword = "a1b2c3ABC"
        for {
          _ <- changePassword(principal, newPassword = Some(firstPassword)).rightValueShouldBeUnit()
          _ <- changePassword(principal, newPassword = Some("abc1A2B3C")).rightValueShouldBeUnit()
          resultingFuture <- changePassword(principal, newPassword = Some(firstPassword)) leftValueShouldBe PasswordIsBeingReused
        } yield resultingFuture
      }
      "succeed if the password is valid according to the policy" in {
        val password = "yey DIDN'T I say we n33ded a new password"
        for {
          _ <- changePassword(principal, newPassword = Some(password)).rightValueShouldBeUnit()
          resultingFuture <- checkPassword(principal, password).rightValueShouldBeUnit()
        } yield resultingFuture
      }
    }
  }
}
