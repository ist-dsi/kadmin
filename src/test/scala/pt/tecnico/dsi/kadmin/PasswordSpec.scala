package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime
import org.scalatest.AsyncWordSpec
import work.martins.simon.expect.fluent.Expect

/**
  * $assumptions
  */
class PasswordSpec extends AsyncWordSpec with TestUtils {
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
          changePassword("changePasswordWrongOptions", keysalt = Some(KeySalt("aes256-cts-hmac-sha1-96", Salt.Special)))
        }
      }
    }

    "the principal does not exist" should {
      "return NoSuchPrincipal when randomizing the keys" in {
        testNoSuchPrincipal {
          changePassword("changePasswordToRandKey", randKey = true)
        }
      }
      "return NoSuchPrincipal when setting the password" in {
        testNoSuchPrincipal {
          changePassword("changePasswordToNewPassword", newPassword = Some("new password"))
        }
      }
      /*"return NoSuchPrincipal when checking the password" in {
        testNoSuchPrincipal {
          checkPassword("changePasswordToNewPassword", password = "new password")
        }
      }*/
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
        changePassword(principal, newPassword = Some("abc")) leftValueShouldIdempotentlyBe PasswordTooShort
      }
      "return PasswordWithoutEnoughCharacterClasses" in {
        changePassword(principal, newPassword = Some("abcabcabc")) leftValueShouldIdempotentlyBe PasswordWithoutEnoughCharacterClasses
      }
      "return PasswordIsBeingReused" in {
        val password = "a1b2c3ABC"
        for {
          _ <- changePassword(principal, newPassword = Some(password)).rightValueShouldBeUnit()
          resultingFuture <- changePassword(principal, newPassword = Some(password)) leftValueShouldIdempotentlyBe PasswordIsBeingReused
        } yield resultingFuture
      }
      "succeed if the password is valid according to the policy" in {
        val password = "yey DIDN'T I say we n33ded a new password"
        for {
          // We cannot test for idempotency here since the policy records the last 2 passwords but since we
          // already tested changePassword for idempotency above this is not a problem
          _ <- changePassword(principal, newPassword = Some(password)).rightValueShouldBeUnit()
          resultingFuture <- checkPassword(principal, password).rightValueShouldBeUnit()
        } yield resultingFuture
      }
    }
  }

  "CheckPassword" when {
    "the password was expired" should {
      "return PasswordExpired" in {
        val principal = "passwordExpired"
        val password = "some password"
        for {
          _ <- addPrincipal("", principal, Some(password)).rightValueShouldBeUnit()
          _ <- expirePrincipalPassword(principal, DateTime.now().minusDays(5)).rightValueShouldBeUnit()
          resultingFuture <- checkPassword(principal, password) leftValueShouldIdempotentlyBe PasswordExpired
        } yield resultingFuture
      }
    }
  }

  "doOperation" when {
    "the password is incorrect" should {
      "return PasswordIncorrect" in {
        val kadmin = new Kadmin(fullPermissionsKadmin.settings.copy(password = "wrong password"))
        kadmin.doOperation("i'll never be executed") { _: Expect[Either[ErrorCase, Unit]] =>
          ()
        } leftValueShouldBe PasswordIncorrect
      }
    }
  }
}
