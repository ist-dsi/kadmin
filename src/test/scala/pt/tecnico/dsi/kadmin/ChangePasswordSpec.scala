package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.scalatest.FlatSpec
import squants.time.TimeConversions._

/**
  * $assumptions
  */
class ChangePasswordSpec extends FlatSpec with TestUtils with LazyLogging {
  import authenticatedKadmin._

  /*"changePassword" should "return NoSuchPrincipal when the principal does not exists" in {
    val principal = "changePasswordNoSuchPrincipal"
    runExpect(deletePrincipal(principal)) shouldBe Right(true)
    testNoSuchPrincipal {
      changePassword(principal, "newPassword")
    }
  }
  it should "return PasswordTooShort when the password is too short" in {
    val principal = "changePasswordPasswordTooShort"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "p$1")
    }(Left(PasswordTooShort))
  }
  it should "return PasswordWithoutEnoughCharacterClasses when the password does not have enough character classes" in {
    val principal = "changePasswordNotEnoughClasses"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    idempotent {
      changePassword(principal, "super big password with only text")
    }(Left(PasswordTooShort))
  }
  it should "idempotently succeed" in {
    val principal = "changePassword"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)
    //This will fail if the principal policy does not allow password reuses
    val password = "big passwords have @least 20 characters"
    idempotent {
      changePassword(principal, password)
    }(Right(true))
    idempotent {
      checkPassword(principal, password)
    }(Right(true))
  }*/

  val expireDateTime = 2.hours.toAbsolute
  val utcExpireDateTime = new AbsoluteDateTime(expireDateTime.dateTime.withZone(DateTimeZone.forID("UTC")))
  "expirePrincipalPassword and getPasswordExpirationDate" should "idempotently succeed" in {
    //expirePrincipal uses internally the modifyPrincipal so we do not test for NoSuchPrincipal nor lack of privilege
    val principal = "expire"
    runExpect(addPrincipal("-nokey", principal)) shouldBe Right(true)

    idempotent {
      expirePrincipalPassword(principal, expireDateTime)
    }(Right(true))

    idempotent {
      getPasswordExpirationDate(principal)
    }(Right(utcExpireDateTime))
  }
}
