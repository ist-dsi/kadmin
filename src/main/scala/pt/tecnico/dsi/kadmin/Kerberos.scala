package pt.tecnico.dsi.kadmin

import java.util.Locale

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import work.martins.simon.expect.core.EndOfFile
import work.martins.simon.expect.fluent.{ExpectBlock, Expect, When}

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex.Match

/**
  * @define idempotentOperation
  *  This operation is idempotent, that is, if this method is invoked twice for the same principal
  *  it will be successful in both invocations.
  *
  * @define startedWithDoOperation
  *  Kadmin will be started with the `doOperation` method, that is, it will perform
  *  authentication as specified in the configuration.
  */
object Kerberos extends LazyLogging {
  val kerberosConfigs = ConfigFactory.load().getConfig("kadmin")
  val realm = kerberosConfigs.getString("realm")
  require(realm != "EXAMPLE.COM", s"""Realm cannot be set to "$realm".""")
  val performAuthentication = kerberosConfigs.getBoolean("perform-authentication")

  val authenticatingPrincipal = kerberosConfigs.getString("authenticating-principal")
  if (performAuthentication && authenticatingPrincipal.isEmpty)
    throw new IllegalArgumentException("When performing authentication authenticating-principal cannot be empty.")
  val authenticatingPrincipalPassword = kerberosConfigs.getString("authenticating-principal-password")
  if (performAuthentication && authenticatingPrincipalPassword.isEmpty)
    throw new IllegalArgumentException("When performing authentication authenticating-principal-password cannot be empty.")

  val commandWithAuthentication = kerberosConfigs.getString("command-with-authentication")
  require(commandWithAuthentication.nonEmpty, "command-with-authentication cannot be empty.")

  val commandWithoutAuthentication = kerberosConfigs.getString("command-without-authentication")
  require(commandWithoutAuthentication.nonEmpty, "command-without-authentication cannot be empty.")

  val KadminPrompt = "kadmin(.local)?: ".r

  def getFullPrincipalName(principal: String): String = {
    if (principal.trim.endsWith(s"@$realm")){
      principal
    /*} else if (principal.contains("@")) {
      //Will we allow the use case of a kadmin administrating more than one realm simultaneously?
      */
    } else {
      s"$principal@$realm"
    }
  }

  /**
    * Obtains a ticket granting ticket for `authenticatingPrincipal` using
    * `authenticatingPrincipalPassword` as the password.
    *
    * If the intended use case is to check whether the principal password is the correct one, then the function
    * `checkPassword` is more suited to that effect.
    *
    * The ticket will be obtained in the machine that invokes this code.
    *
    * @return Either an ErrorCase or a date time of when the obtained ticked must be renewed.
    */
  def obtainTicketGrantingTicket(authenticatingPrincipal: String = authenticatingPrincipal,
                                 authenticatingPrincipalPassword: String = authenticatingPrincipalPassword): Expect[Either[ErrorCase, DateTime]] = {
    require(authenticatingPrincipal.isEmpty, "authenticatingPrincipal cannot be empty.")
    require(authenticatingPrincipalPassword.isEmpty, "authenticatingPrincipalPassword cannot be empty.")

    val defaultValue: Either[ErrorCase, DateTime] = Left(UnknownError())
    val e = new Expect(s"kinit $authenticatingPrincipal", defaultValue)
    e.expect
      .when(s"Password for $authenticatingPrincipal: ")
        .sendln(authenticatingPrincipalPassword)
      .when(s"""Client '$authenticatingPrincipal' not found in Kerberos database""")
        .returning(Left(PrincipalDoesNotExist))
    e.expect
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
      .addWhen(unknownError)
      .when(EndOfFile)
        .returning {
          val datetimeRegex = """\d\d-\d\d-\d\d\d\d \d\d:\d\d:\d\d"""
          val e2 = new Expect("klist", defaultValue)
          e2.expect(
            s"""Ticket cache: FILE:[^\n]+
                |Default principal: $authenticatingPrincipal
                |
                |Valid starting       Expires              Service principal
                |$datetimeRegex  $datetimeRegex  krbtgt/$realm@$realm
                |\trenew until ($datetimeRegex)""".stripMargin.r)
            .returning { m: Match =>
              val dateTimeString = m.group(1)
              val dateTimeFormat = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss")
              Right(dateTimeFormat.parseDateTime(dateTimeString))
            }
          e2
        }
    e
  }

  /**
    * Creates an Expect that performs an authenticated kadmin operation `f` and then quits kadmin.
    *
    * Kadmin is started using the `command-with-authentication` configuration value.
    * The authentication is performed by sending `authenticatingPrincipalPassword` and waiting for either
    * an error message saying the password was incorrect or the kadmin prompt. If the password was incorrect Expect
    * will return a Left(IncorrectPassword).
    *
    * @example {{{
    *   withAuthentication { e =>
    *     e.expect(KadminPrompt)
    *       .sendln(s"getprinc fullPrincipal")
    *   }
    * }}}
    *
    * @param f the kerberos administration operation to perform.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the authentication, the operation `f` and then quits kadmin.
    */
  def withAuthentication[R](f: Expect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val defaultValue: Either[ErrorCase, R] = Left(UnknownError())
    val e = new Expect(commandWithAuthentication, defaultValue)
    e.expect(s"Password for $authenticatingPrincipal: ")
      .sendln(authenticatingPrincipalPassword)
    e.expect
      //Due to the fantastic consistency in kerberos commands we cannot use:
      //  addWhen(passwordIncorrect)
      //because in kadmin the error is "Incorrect password" and the `passwordIncorrect` function will add a when with:
      //  when("Password incorrect")
      //</sarcasm>
      .when("Incorrect password")
        .returning(Left(PasswordIncorrect))
        .addActions(preemptiveExit)
      .when(KadminPrompt)
        //All good. The password was correct. We can continue.
        //We need to send a newline in order for `f` to see the KadminPrompt
        .sendln("")
      .addWhen(unknownError)
        .addActions(preemptiveExit)
    e.addExpectBlock(f)
    e.expect(KadminPrompt)
      .sendln("quit")
    e
  }

  /**
    * Creates an Expect that performs a kadmin operation `f` and then quits kadmin.
    *
    * Kadmin is started using the `command-without-authentication` configuration value. It is assumed that this command
    * starts kadmin in a way that requires no authentication (such as using kadmin.local on the
    * master KDC).
    *
    * If authentication is required use `withAuthentication` instead.
    *
    * @example {{{
    *   withoutAuthentication { e =>
    *     e.expect(KadminPrompt)
    *       .sendln(s"getprinc fullPrincipal")
    *   }
    * }}}
    *
    * @param f the kerberos administration operation to perform.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the operation `f` and then quits kadmin.
    */
  def withoutAuthentication[R](f: Expect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val defaultValue: Either[ErrorCase, R] = Left(UnknownError())
    val e = new Expect(commandWithoutAuthentication, defaultValue)
    e.expect
      .when(KadminPrompt)
        //All good. We can continue.
        //We need to send a newline in order for `f` to see KadminPrompt
        .sendln("")
      .addWhen(unknownError)
        .addActions(preemptiveExit)
    e.addExpectBlock(f)
    e.expect(KadminPrompt)
      .sendln("quit")
    e
  }

  /**
    * Creates an Expect that performs a kadmin operation `f` and then quits kadmin.
    * If the configuration `perform-authentication` is set to true then access to kadmin will be authenticated.
    * Otherwise it will be unauthenticated.
    *
    * @example {{{
    *   doOperation { e =>
    *     e.expect(KadminPrompt)
    *       .sendln(s"getprinc fullPrincipal")
    *   }
    * }}}
    *
    * @param f the kerberos administration operation to perform.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the operation `f` and then quits kadmin.
    */
  def doOperation[R](f: Expect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    if (performAuthentication) {
      withAuthentication(f)
    } else {
      withoutAuthentication(f)
    }
  }

  /**
    * Creates `principal` using `parameters`.
    *
    * `parameters` are the possible parameters to pass to the kadmin add_principal operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#add-principal]] for a
    * full list.
    *
    * `parameters` are not checked for validity.
    *
    * Unfortunately we cannot make this operation idempotent because to do so would depend on the `parameters`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to create.
    * @param parameters the options to pass to add_principal.
    * @return an Expect that creates `principal`.
    */
  def addPrincipal(principal: String, parameters: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(KadminPrompt)
        .sendln(s"add_principal $parameters $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" added.""")
          .returning(Right(true))
        .addWhen(insufficientPermissions("add"))
        .addWhen(unknownError)
    }
  }

  /**
    * Deletes `principal`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to delete.
    * @return an Expect that deletes `principal`.
    */
  def deletePrincipal(principal: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(KadminPrompt)
        //With the -force option this command no longer prompts for deletion.
        .sendln(s"delete_principal -force $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" deleted.""")
          .returning(Right(true))
        .when("Principal does not exist")
          .returning(Right(true)) //This is what makes this operation idempotent
        .addWhen(insufficientPermissions("delete"))
        .addWhen(unknownError)
    }
  }

  /**
    * Modifies `principal` using `parameters`.
    *
    * `parameters` are the possible parameters to pass to the kadmin modify_principal operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#modify-principal]] for a
    * full list.
    *
    * `parameters` are not checked for validity.
    *
    * Unfortunately we cannot guarantee this operation is idempotent because to do so would depend on the `parameters`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to modify.
    * @param parameters the modifications to perform on `principal`.
    * @return an Expect that modifies `principal`.
    */
  def modifyPrincipal(principal: String, parameters: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(KadminPrompt)
        .sendln(s"modify_principal $parameters $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" modified.""")
          .returning(Right(true))
        .addWhen(principalDoesNotExist)
        .addWhen(insufficientPermissions("modify"))
        .addWhen(unknownError)
    }
  }

  /**
    * Changes the `principal` password to `newPassword`.
    *
    * In some cases this operation might not be idempotent. For example: if the policy assigned to `principal`
    * does not allow the same password to be reused, the first time this operation is executed it will be successful,
    * however on the second time it will fail with an ErrorCase `PasswordIsBeingReused`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to change the password.
    * @param newPassword the new password
    * @return an Expect that changes `principal` password.
    */
  def changePassword(principal: String, newPassword: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(KadminPrompt)
        .sendln(s"change_password -pw $newPassword $fullPrincipal")
      e.expect
        .when(s"""Password for "$fullPrincipal" changed.""")
          .returning(Right(true))
        .when("Password is too short")
          .returning(Left(PasswordTooShort))
        .when("Password does not contain enough character classes")
          .returning(Left(PasswordWithoutEnoughCharacterClasses))
        .when("Cannot reuse password")
          .returning(Left(PasswordIsBeingReused))
        .addWhen(principalDoesNotExist)
        .addWhen(insufficientPermissions("changepw"))
        .addWhen(unknownError)
    }
  }

  /**
    * Performs the operation `f` over the output returned by "getprinc principal".
    * This is useful to read the principal attributes.
    *
    * $startedWithDoOperation
    *
    * A specialized function to obtain the principal password expiration date called
    * `setPasswordExpirationDate` already exists.
    *
    * Consider using the `parseDateTime` method if `f` is to parse a date time.
    *
    * @example {{{
    *   withPrincipal(principal){ expectBlock =>
    *     expectBlock.when("Maximum ticket life: ([^\n]+)\n".r)
    *       .returning{ m: Match =>
    *         //m.group(1) will contain the maximum ticket life.
    *       }
    * }}}
    *
    * @param principal the principal to get the attributes.
    * @param f the operation to perform upon the principal attributes.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that lists the `principal` attributes, performs the operation `f` and then quits kadmin.
    */
  def getPrincipal[R](principal: String)(f: ExpectBlock[Either[ErrorCase, R]] => When[Either[ErrorCase, R]]): Expect[Either[ErrorCase, R]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(KadminPrompt)
        .sendln(s"getprinc $fullPrincipal")
      e.expect
        .addWhen(principalDoesNotExist)
          .addActions(preemptiveExit)
        .addWhen(insufficientPermissions("inquire"))
          .addActions(preemptiveExit)
        .addWhen(f)
    }
  }

  /**
    * Expires `principal`. In other words, sets the expiration date to now.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to expire.
    * @return an Expect that expires `principal`.
    */
  def expirePrincipal(principal: String): Expect[Either[ErrorCase, Boolean]] = {
    modifyPrincipal(principal, "-expire now")
  }
  /**
    * Unexpires `principal`. In other words, sets the expiration date to never.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to unexpire.
    * @return an Expect that unexpires `principal`.
    */
  def unexpirePrincipal(principal: String): Expect[Either[ErrorCase, Boolean]] = {
    modifyPrincipal(principal, "-expire never")
  }

  /**
    * Set the password expiration date of `principal` to `date`.
    *
    * This method also clears the principal policy. This is necessary because the principal policy might impose a
    * limit on how soon the password can expire. And if `date` happens before the limit then the password expiration
    * date will remain unchanged.
    *
    * This method should only be used for debugging applications where the fact that the principal password
    * is about to expire or has expired changes the behavior of the application.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to set the password expiration date.
    * @param datetime the datetime to set as the password expiration date.
    * @return an Expect that sets the password expiration date of `principal` to `date`.
    */
  def setPasswordExpirationDate(principal: String, datetime: Either[Never.type, DateTime]): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val dateString = datetime.fold(_ => "never", date => DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(date))
    // Clear policy must be done separately from the pwexpire because if both the clearpolicy and the pwexpire are
    // performed together and the principal already has no policy then the command would fail with
    // "No such attribute while modifying $fullPrincipal" or "Database store error" and the password expiration date wont be modified.

    doOperation { e =>
      e.expect(KadminPrompt)
        .sendln(s"modify_principal -clearpolicy $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" modified.""")
          //Its all good. We can continue.
        .when("User modification failed: No such attribute")
          //This happens when the principal already has no policy. (This error happens in kadmin.local)
          //Its all good. We can continue.
        .when("Database store error")
          //This happens when the principal already has no policy. (This error happens in kadmin)
          //Its all good. We can continue.
        .addWhen(principalDoesNotExist)
          .addActions(preemptiveExit)
        .addWhen(insufficientPermissions("modify"))
          .addActions(preemptiveExit)
        .addWhen(unknownError)
          .addActions(preemptiveExit)
      e.expect(KadminPrompt)
        .sendln(s"""modify_principal -pwexpire "$dateString" $fullPrincipal""")
      e.expect
        .when(s"""Principal "$fullPrincipal" modified.""")
          .returning(Right(true))
      //We do not need to check for the modify privilege or if the principal exists. Because if any of those cases
      //were to happen they would be caught when performing the clear policy.

      // Sometimes there isn't the need to clear the policy. This is true when `date` lies ahead of how soon the
      // password can expire according to the policy. We could avoid the clear policy in two different ways:
      // First alternative:
      //   a) Get the current policy of the user.
      //   b) Find the minimum password life that policy sets.
      //   c) If `date` is after the minimum perform the set of the password expiration date.
      //   d) Otherwise fail with some error stating that it is necessary to clear the policy for this operation to work.
      // Second alternative:
      //   a) Set the password expiration date.
      //   b) Get the password expiration date.
      //   c) If the password expiration date is the pretended one, return true.
      //   d) Otherwise set the password expiration date to its original value (the one obtained from the get).
      //      This is step is necessary because if the principal policy is cleared then the password expiration date
      //      will be set to the value we set it to previously. Which, most likely, is not what the user was expecting.
      //   e) Fail with some error stating that it is necessary to clear the policy for this operation to work.
      // We chose not to follow any of these alternatives because they are more complex. And also because this method
      // should only be used for debugging purposes.
    }
  }

  /**
    * Gets the password expiration datetime of `principal`.
    *
    * See the `parseDateTime` method to understand how the datetime is parsed.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to read the password expiration date from.
    * @return an Expect that get the password expiration date of `principal`.
    */
  def getPasswordExpirationDate(principal: String): Expect[Either[ErrorCase, Either[Never.type, DateTime]]] = {
    getPrincipal(principal){ expectBlock =>
      expectBlock.when("Password expiration date: ([^\n]+)\n".r)
        .returning{ m: Match =>
          Try {
            parseDateTime(m.group(1))
          } match {
            case Success(datetime) => Right(datetime)
            case Failure(e) => Left(UnknownError(Some(e.getMessage)))
          }
        }
    }
  }

  /**
    * Gets the expiration date of `principal`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to read the expiration date from.
    * @return an Expect that get the expiration date of `principal`.
    */
  def getExpirationDate(principal: String): Expect[Either[ErrorCase, Either[Never.type, DateTime]]] = {
    getPrincipal(principal){ expectBlock =>
      expectBlock.when("Expiration date: ([^\n]+)\n".r)
        .returning{ m: Match =>
          Try {
            parseDateTime(m.group(1))
          } match {
            case Success(datetime) => Right(datetime)
            case Failure(e) => Left(UnknownError(Some(e.getMessage)))
          }
        }
    }
  }

  object Never

  /**
    * Tries to parse a date time string returned by a kadmin get_principal operation.
    *
    * The string must be in the format `"EEE MMM dd HH:mm:ss zzz yyyy"`, see
    * [[http://www.joda.org/joda-time/apidocs/org/joda/time/format/DateTimeFormat.html Joda Time DateTimeFormat]]
    * for an explanation of the format.
    *
    * If the string is either `"[never]"` or `"[none]"` the object Never will be returned inside a Left.
    * Otherwise the string will be parsed in the following way:
    *
    *  1. Any text following the year, as long as it is separated with a space, will be removed from `dateTimeString`.
    *  1. Since `DateTimeFormat` cannot process time zones, the timezone will be removed from `dateTimeString`, and an
    *    attempt to match it against one of `DateTimeZone.getAvailableIDs` will be made. If no match is found the default
    *    timezone will be used.
    *  1. The default locale will be used when reading the date. This is necessary for the day of the week and the month
    *    of the year parts.
    *  1. Finally a `DateTimeFormat` will be constructed using the format above (except the time zone), the
    *    computed timezone and the default locale.
    *  1. The clean `dateString` (the result of step 1 and 2) will be parsed to a `DateTime` using the format
    *    constructed in step 4.
    *
    * @param dateTimeString the string containing the date time.
    */
  def parseDateTime(dateTimeString: String): Either[Never.type, DateTime] = {
    val trimmedDateString = dateTimeString.trim
    if (trimmedDateString == "[never]" || trimmedDateString == "[none]") {
      Left(Never)
    } else {
      //dateString must be in the format: EEE MMM 19 15:49:03 z* 2016 .*
      //EEE = three letter day of the week, eg: English: Tue, Portuguese: Ter
      //MMM = three letter month of the year, eg: English: Feb, Portuguese: Fev
      //zzz = the timezone
      val parts = trimmedDateString.split("""\s""")
      require(parts.size < 6, "Not enought fields in `dateTimeString` for format \"EEE MMM dd HH:mm:ss zzz yyyy\".")

      val Array(dayOfWeek, month, day, time, timezone, year, _*) = parts

      val finalTimeZone = if (DateTimeZone.getAvailableIDs.contains(timezone)) {
        DateTimeZone.forID(timezone)
      } else {
        val default = DateTimeZone.getDefault
        logger.warn(s"Kerberos (kadmin) returned an unknown timezone: $timezone. Using the default one: $default.")
        default
      }

      val fmt = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss yyyy")
        //We cannot parse the time zone with the DateTimeFormat because is does not support it.
        .withZone(finalTimeZone)
        //We define the locale because the text corresponding to the EEE and MMM patterns
        //mostly likely is locale specific.
        .withLocale(Locale.getDefault)

      Right(fmt.parseDateTime(s"$dayOfWeek $month $day $time $year"))
    }
  }

  /**
    * Checks if the password of `principal` is `password`.
    *
    * The check is performed by trying to obtain a ticket with kinit.
    *
    * A ticket won't actually be generated since kinit is invoked with the crendentials cache set to /dev/null.
    *
    * To obtain a ticket use the function `obtainTicketGrantingTicket`.
    *
    * @param principal the principal to test the password.
    * @param password the password to test.
    * @return an Expect that checks if the password of `principal` is `password`.
    */
  def checkPassword(principal: String, password: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val defaultValue: Either[ErrorCase, Boolean] = Left(UnknownError())
    //By setting the credential cache to /dev/null we no longer have the need to remove the generated
    //credential (the ticket) from the cache, nor do we have to carefully manage the lifetime of the ticket.
    val e = new Expect(s"""kinit -c /dev/null $fullPrincipal""", defaultValue)
    e.expect
      .when(s"Password for $fullPrincipal:")
        .sendln(password)
      .when(s"""Client '$fullPrincipal' not found in Kerberos database""")
        .returning(Left(PrincipalDoesNotExist))
    e.expect
      .when("Internal credentials cache error while storing credentials")
        //Because we set the credential cache to /dev/null kadmin fails when trying to write the ticket to the cache.
        //This means the authentication was successful and thus the password is correct.
        .returning(Right(true))
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
    e
  }

  private def insufficientPermissions[R](privilege: String) = { expectBlock: ExpectBlock[Either[ErrorCase, R]] =>
    expectBlock
      .when(s"Operation requires ``$privilege'' privilege")
        .returning(Left(InsufficientPermissions(privilege)))
  }
  private def principalDoesNotExist[R] = { expectBlock: ExpectBlock[Either[ErrorCase, R]] =>
    expectBlock
      .when("Principal does not exist")
        .returning(Left(PrincipalDoesNotExist))
  }
  private def passwordIncorrect[R] = { expectBlock: ExpectBlock[Either[ErrorCase, R]] =>
    expectBlock
      .when("Password incorrect")
        .returning(Left(PasswordIncorrect))
  }
  private def passwordExpired[R] = { expectBlock: ExpectBlock[Either[ErrorCase, R]] =>
    expectBlock
      .when("Password expired")
      .returning(Left(PasswordIncorrect))
  }
  private def unknownError[R] = { expectBlock: ExpectBlock[Either[ErrorCase, R]] =>
    expectBlock
      //By using a regex (even if it is greedy) we might not see the entire error output
      .when("(.+)".r)
        .returning{ m: Match =>
          Left(UnknownError(Some(m.group(1))))
        }
  }

  private def preemptiveExit[R]: When[Either[ErrorCase, R]] => Unit = { when =>
    when
      //We send the quit to allow kadmin a graceful shutdown
      .sendln("quit")
      //This ensures the next expect(s) (if any) do not get executed and
      //we don't end up returning something else by mistake.
      .exit()
  }

  sealed trait ErrorCase
  case object PrincipalDoesNotExist extends ErrorCase
  case object PasswordIncorrect extends ErrorCase
  case object PasswordTooShort extends ErrorCase
  case object PasswordWithoutEnoughCharacterClasses extends ErrorCase
  case object PasswordIsBeingReused extends ErrorCase
  case object PasswordExpired extends ErrorCase
  case class InsufficientPermissions(missingPrivilege: String) extends ErrorCase
  case class UnknownError(cause: Option[String] = None) extends ErrorCase
}
