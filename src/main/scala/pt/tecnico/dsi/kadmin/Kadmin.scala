package pt.tecnico.dsi.kadmin

import java.io.File
import java.nio.file.Files
import java.util.Locale

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import work.martins.simon.expect.EndOfFile
import work.martins.simon.expect.fluent.{RegexWhen, ExpectBlock, Expect, When}

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex.Match

/**
  * @define idempotentOperation
  *  This operation is idempotent, that is, if this method is invoked twice for the same principal
  *  it will be successful in both invocations. This means that this operation can be repeated or retried as
  *  often as necessary without causing unintended effects.
  * @define startedWithDoOperation
  *  Kadmin will be started with the `doOperation` method, that is, it will perform
  *  authentication as specified in the configuration.
  */
class Kadmin(val settings: Settings = new Settings()) extends LazyLogging {
  def this(config: Config) = this(new Settings(config))
  import settings._

  def getFullPrincipalName(principal: String): String = {
    if (principal.trim.endsWith(s"@$realm")){
      principal
    } else if (principal.contains("@")) {
      throw new IllegalArgumentException("Principal with unknown realm: " + principal.substring(principal.indexOf("@")))
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

    val fullPrincipal = getFullPrincipalName(authenticatingPrincipal)

    val defaultValue: Either[ErrorCase, DateTime] = Left(UnknownError())
    val e = new Expect(s"kinit $fullPrincipal", defaultValue)
    e.expect
      .when(s"Password for $fullPrincipal: ")
        .sendln(authenticatingPrincipalPassword)
      .when(s"""Client '$fullPrincipal' not found in Kerberos database""")
        .returning(Left(NoSuchPrincipal))
    e.expect
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
      .addWhen(unknownError)
      .when(EndOfFile)
        .returningExpect {
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

  //region <Generic commands>
  /**
    * Creates an Expect that performs an authenticated kadmin operation `f` and then quits kadmin.
    *
    * Kadmin is started using `command-with-authentication` configuration value.
    * The authentication is performed by sending `authenticatingPrincipalPassword` and waiting for either
    * an error message saying the password was incorrect or the kadmin prompt. If the password was incorrect Expect
    * will return a Left(IncorrectPassword).
    *
    * If no authentication is required use `withoutAuthentication` instead.
    *
    * @example {{{
    *   withAuthentication { e =>
    *     e.expect(KadminPrompt)
    *       .sendln(s"getprinc fullPrincipal")
    *   }
    * }}}
    * @param f the kerberos administration operation to perform.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the authentication, the operation `f` and then quits kadmin.
    */
  def withAuthentication[R](f: Expect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val defaultValue: Either[ErrorCase, R] = Left(UnknownError())
    val e = new Expect(commandWithAuthentication, defaultValue)
    val fullPrincipal = getFullPrincipalName(authenticatingPrincipal)
    e.expect(s"Password for $fullPrincipal: ")
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
      .when(kadminPrompt)
        //All good. The password was correct. We can continue.
        //We need to send a newline in order for `f` to see the KadminPrompt
        .sendln("")
      .addWhen(unknownError)
        .addActions(preemptiveExit)
    e.addExpectBlock(f)
    e.expect(kadminPrompt)
      .sendln("quit")
      .exit()
    e
  }

  /**
    * Creates an Expect that performs a kadmin operation `f` and then quits kadmin.
    *
    * Kadmin is started using `command-without-authentication` configuration value. It is assumed that this command
    * starts kadmin in a way that requires no authentication (such as using kadmin.local on the master KDC).
    *
    * If authentication is required use `withAuthentication` instead.
    *
    * @example {{{
    *   withoutAuthentication { e =>
    *     e.expect(KadminPrompt)
    *       .sendln(s"getprinc fullPrincipal")
    *   }
    * }}}
    * @param f the kerberos administration operation to perform.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the operation `f` and then quits kadmin.
    */
  def withoutAuthentication[R](f: Expect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val defaultValue: Either[ErrorCase, R] = Left(UnknownError())
    val e = new Expect(commandWithoutAuthentication, defaultValue)
    e.expect
      .when(kadminPrompt)
        //All good. We can continue.
        //We need to send a newline in order for `f` to see KadminPrompt
        .sendln("")
      .addWhen(unknownError)
        .addActions(preemptiveExit)
    e.addExpectBlock(f)
    e.expect(kadminPrompt)
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
  //endregion

  //region <Add/Modify commands>
  /**
    * Creates `principal` using `options`.
    *
    * $idempotentOperation Except if `options` contains any of:
    *  - `-randkey`
    *  - `-pw ''password''`
    *  - `-e ''enc:salt''`
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to create.
    * @param options the parameters to pass to the kadmin `add_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#add-principal Add
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that creates `principal`.
    */
  def addPrincipal(options: String, principal: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"add_principal $options $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" (created|added).""".r)
          .returning(Right(true))
        .when("Principal or policy already exists")
          .returningExpect(modifyPrincipal(options, principal))
          //Is modifying the existing principal the best approach?
          //Would deleting the existing principal and create a new one be a better one?
          //  · By deleting we will be losing the password history. Which would make the add idempotent when using the
          //    change password options (-pw, -e and -randkey). But we would partially lose the restraint that prohibits
          //    the reuse of the password.
          //  · By modifying we run into troubles when using the change password options (-pw, -e or -randkey).
          //TODO: would invoking changePassword for these options solve the problem?
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }

  /**
    * Modifies `principal` using `options`.
    *
    * $idempotentOperation Except if `options` contains any of:
    *  - `-randkey`
    *  - `-pw ''password''`
    *  - `-e ''enc:salt''`
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to modify.
    * @param options the parameters to pass to the kadmin `modify_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#modify-principal Modify
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that modifies `principal`.
    */
  def modifyPrincipal(options: String, principal: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val clearPolicy = options.contains("-clearpolicy")
    val cleanedOptions = options
      .replaceAll("-clearpolicy", "")
      //Contrary to what is stated in the documentation the option -nokey is not valid in modify.
      .replaceAll("""-nokey\b""", "")

    doOperation { e =>
      if (clearPolicy) {
        // Unfortunately -clearpolicy is not idempotent in kadmin. If clear policy is attempted in a principal
        // which already has no policy an error will be outputted.
        // If clear policy '''and''' any other modification is made simultaneously, for example:
        // modify_principal -clearpolicy -pwexpire <somedate> <principal>
        // And the principal already has no policy, the entire modification will fail due to the clear policy. And the
        // other modifications won't be performed (in this case the pwexpire). So it is necessary to perform the clear
        // policy on its own (to ensure idempotency) and then the remaining modifications.
        e.expect(kadminPrompt)
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
          .addWhen(insufficientPermission)
            .addActions(preemptiveExit)
          .addWhen(unknownError)
            .addActions(preemptiveExit)
      }
      e.expect(kadminPrompt)
        .sendln(s"""modify_principal $cleanedOptions $fullPrincipal""")
      val w = e.expect
        .when(s"""Principal "$fullPrincipal" modified.""")
          .returning(Right(true))
      if (clearPolicy == false) {
        //We just need to check for these cases when the policy was not cleared. Because in the case the policy
        //was cleared these cases will already have been caught.
        w.addWhen(principalDoesNotExist)
          .addWhen(insufficientPermission)
          .addWhen(unknownError)
      }
    }
  }

  /**
    * Sets the `principal` expiration date time to `expirationDateTime`.
    *
    * To expire the principal immediately: {{{
    *   expirePrincipal(principal)
    * }}}
    * To expire the principal 2 days from now: {{{
    *   import squants.time.TimeConversions._
    *   expirePrincipal(principal, 2.days)
    * }}}
    * To ensure a principal never expires: {{{
    *   expirePrincipal(principal, Never)
    * }}}
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to expire.
    * @return an Expect that expires `principal`.
    */
  def expirePrincipal(principal: String, expirationDateTime: ExpirationDateTime = Now()): Expect[Either[ErrorCase, Boolean]] = {
    val dateTimeString = expirationDateTime.toKadminRepresentation
    modifyPrincipal(s"""-expire "$dateTimeString"""", principal)
  }

  /**
    * Set the password expiration date of `principal` to `datetime` (with some caveats, read below).
    *
    * This method might not change the password expiration date time. This is due to the fact that `principal` might
    * have a policy that imposes a limit on how soon the password can expire and `datetime` comes sooner than that limit.
    *
    * To guarantee that the date will actually change it is necessary to clear the principal policy. This can be
    * achieved by invoking this method with `force` set to true. If you do so, then it is your responsibility to
    * change, at a later time, the policy back to the intended one. However bear in mind that doing so might cause the
    * expiration date to revert back to the one defined by the policy.
    *
    * WARNING when this method is invoked with `force` set to false and the password expiration date does not change
    * (due to the policy) `getPasswordExpirationDate` will return the original date (the one set by the policy).
    * However if the policy is cleared and `getPasswordExpirationDate` is invoked again, the obtained datetime
    * will be the one set by this method. This caveat comes from the kadmin utility and not from this library.
    *
    * Due to its caveats this method SHOULD ONLY BE USED FOR DEBUGGING applications where the fact that the principal
    * password is about to expire or has expired changes the behavior of the application.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to set the password expiration date.
    * @param datetime the datetime to set as the password expiration date.
    * @param force whether or not to clear the principal policy. By default this is set to false.
    * @return an Expect that sets the password expiration date of `principal` to `date`.
    */
  def expirePrincipalPassword(principal: String, datetime: ExpirationDateTime = Now(),
                              force: Boolean = false): Expect[Either[ErrorCase, Boolean]] = {
    val dateTimeString = datetime.toKadminRepresentation
    modifyPrincipal(s"""${if (force) "-clearpolicy " else ""}-pwexpire "$dateTimeString"""", principal)
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

  /**
    * Changes the `principal` password to `newPassword` and/or sets its key to a random value
    * and/or sets its salt to `salt`.
    *
    * In some cases this operation might not be idempotent. For example: if the policy assigned to `principal`
    * does not allow the same password to be reused, the first time the password is changed it will be successful,
    * however on the second time it will fail with an ErrorCase `PasswordIsBeingReused`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to change the password.
    * @param newPassword the new password
    * @return an Expect that changes `principal` password.
    */
  def changePassword(principal: String, newPassword: Option[String] = None,
                     randKey: Boolean = false, salt: Option[String] = None): Expect[Either[ErrorCase, Boolean]] = {
    require(newPassword.nonEmpty || randKey || salt.nonEmpty,
      "At least one of newPassword, randKey or salt must be defined " +
      "(be a Some, for newPassword and salt. Or set to true, for the randKey).")

    val newPasswordOption = newPassword.map(p => s"""-pw "$p"""").getOrElse("")
    val randKeyOption = if (randKey) "-randkey" else ""
    val saltOption = salt.map(s => s"""-e "$s"""").getOrElse("")
    val options = Seq(newPasswordOption, randKeyOption, saltOption).mkString(" ")

    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"""change_password $options $fullPrincipal""")
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
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }
  //endregion

  //region <Keytab commands>
  /**
    * @return The File for the `principal` keytab.
    */
  protected def getKeytabFile(principal: String): File = {
    val principalWithoutRealm = if (principal.contains("@")) {
      principal.substring(0, principal.indexOf("@"))
    } else {
      principal
    }
    val cleanedPrincipal = if (principalWithoutRealm.contains("/")) {
      principalWithoutRealm.replaceAll("/", ".")
    } else {
      principalWithoutRealm
    }
    new File(keytabsLocation, s"$cleanedPrincipal.keytab")
  }

  /**
    * Creates a keytab for the given `principal`. The keytab can then be obtained with the `obtainKeytab` method.
    *
    * This operation is NOT idempotent, since multiple invocations lead to the keytab file being appended
    * with the same tickets but with different keys.
    *
    * @param principal the principal for whom to create the keytab.
    * @return an Expect that creates the keytab for `principal`.
    */
  def createKeytab(principal: String): Expect[Either[ErrorCase, Boolean]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val keytabPath = getKeytabFile(principal).getAbsolutePath
    doOperation{ e =>
      e.expect(kadminPrompt)
        .sendln(s"ktadd -keytab $keytabPath $fullPrincipal")
      e.expect
        .when("Entry for principal (.*?) added to keytab".r)
          .returning(Right(true))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }

  /**
    * Obtains a keytab for the given `principal`.
    * If the principal does not have a keytab or the keytab exists but it isn't readable by the current user a None
    * will be returned.
    *
    * @param principal the principal to obtain the keytab.
    */
  def obtainKeytab(principal: String): Option[Array[Byte]] = {
    val f = getKeytabFile(principal)
    if (f.canRead) {
      Some(Files.readAllBytes(f.toPath))
    } else {
      if (f.exists() == false) {
        logger.info(s"""Keytab file "${f.getAbsolutePath}" doesn't exist.""")
      } else if (f.canRead == false) {
        val currentUser = System.getProperty("user.name")
        logger.info(s"""User "$currentUser" has insufficient permissions to read the keytab file "${f.getAbsolutePath}".""")
        //Should we return an error (eg. inside an Either) if the file exists but we do not have permissions to read it?
      }
      None
    }
  }
  //endregion

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
      e.expect(kadminPrompt)
        //With the -force option it no longer prompts for deletion.
        .sendln(s"delete_principal -force $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" deleted.""")
          .returning(Right(true))
        .addWhen(principalDoesNotExist)
          //This is what makes this operation idempotent
          .returning(Right(true))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }

  //region <Get/Read commands>
  /**
    * Performs the operation `f` over the output returned by "get_principal principal".
    * This is useful to read the principal attributes.
    *
    * $startedWithDoOperation
    *
    * Specialized functions to obtain the principal expiration date and the password expiration date already exist.
    * See [getExpirationDate] and [getPasswordExpirationDate].
    *
    * Consider using the `parseDateTime` method if `f` is to parse a date time.
    *
    * @example {{{
    *   withPrincipal(principal){ expectBlock =>
    *     expectBlock.when("""Maximum ticket life: ([^\n]+)\n""".r)
    *       .returning{ m: Match =>
    *         //m.group(1) will contain the maximum ticket life.
    *       }
    * }}}
    * @param principal the principal to get the attributes.
    * @param f the operation to perform upon the principal attributes.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that lists the `principal` attributes, performs the operation `f` and then quits kadmin.
    */
  def withPrincipal[R](principal: String)(f: ExpectBlock[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"get_principal $fullPrincipal")
      e.expect
        .addWhen(principalDoesNotExist)
        .addWhen(insufficientPermission)
        .addWhens(f)
    }
  }

  /**
    * Gets the expiration date of `principal`.
    *
    * See the `parseDateTime` method to understand how the datetime is parsed.
    *
    * The returned ExpirationDateTime will either be of type `Never` or `AbsoluteDateTime`, and never of type
    * `Now` or `RelativeDateTime`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to read the expiration date from.
    * @return an Expect that get the expiration date of `principal`.
    */
  def getExpirationDate(principal: String): Expect[Either[ErrorCase, ExpirationDateTime]] = {
    withPrincipal(principal){ expectBlock =>
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

  /**
    * Gets the password expiration datetime of `principal`.
    *
    * See the `parseDateTime` method to understand how the datetime is parsed.
    *
    * The returned ExpirationDateTime will either be of type `Never` or `AbsoluteDateTime`, and never of type
    * `Now` or `RelativeDateTime`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to read the password expiration date from.
    * @return an Expect that get the password expiration date of `principal`.
    */
  def getPasswordExpirationDate(principal: String): Expect[Either[ErrorCase, ExpirationDateTime]] = {
    withPrincipal(principal){ expectBlock =>
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
    * Tries to parse a date time string returned by a kadmin `get_principal` operation.
    *
    * The string must be in the format `"EEE MMM dd HH:mm:ss zzz yyyy"`, see
    * [[http://www.joda.org/joda-time/apidocs/org/joda/time/format/DateTimeFormat.html Joda Time DateTimeFormat]]
    * for an explanation of the format.
    *
    * If the string is either `"[never]"` or `"[none]"` the `Never` will be returned.
    * Otherwise the string will be parsed in the following way:
    *
    *  1. Any text following the year, as long as it is separated with a space, will be removed from `dateTimeString`.
    *  1. Since `DateTimeFormat` cannot process time zones, the timezone will be removed from `dateTimeString`, and an
    *    attempt to match it against one of `DateTimeZone.getAvailableIDs` will be made. If no match is found the default
    *    timezone will be used.
    *  1. The default locale will be used when reading the date. This is necessary for the day of the week (EEE) and
    *    the month of the year (MMM) parts.
    *  1. Finally a `DateTimeFormat` will be constructed using the format above (except the time zone), the
    *    computed timezone and the default locale.
    *  1. The clean `dateString` (the result of step 1 and 2) will be parsed to a `DateTime` using the format
    *    constructed in step 4.
    *
    * @param dateTimeString the string containing the date time.
    * @return `Never` or an `AbsoluteDateTime`
    */
  def parseDateTime(dateTimeString: String): ExpirationDateTime = dateTimeString.trim match {
    case "[never]" | "[none]" => Never
    case trimmedDateString =>
      //dateString must be in the format: EEE MMM 19 15:49:03 z* 2016 .*
      //EEE = three letter day of the week, eg: English: Tue, Portuguese: Ter
      //MMM = three letter month of the year, eg: English: Feb, Portuguese: Fev
      //zzz = the timezone
      val parts = trimmedDateString.split("""\s+""")
      require(parts.size >= 6, s"""Not enough fields in "$trimmedDateString" for format "EEE MMM dd HH:mm:ss zzz yyyy".""")

      //Discards any field after the year
      val Array(dayOfWeek, month, day, time, timezone, year, _*) = parts

      val finalTimeZone = if (DateTimeZone.getAvailableIDs.contains(timezone)) {
        DateTimeZone.forID(timezone)
      } else {
        val default = DateTimeZone.getDefault
        logger.warn(s"Unknown timezone: $timezone. Using the default one: $default.")
        default
      }

      val fmt = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss yyyy")
        //We cannot parse the time zone with the DateTimeFormat because is does not support it.
        .withZone(finalTimeZone)
        //We define the locale because the text corresponding to the EEE and MMM patterns
        //mostly likely is locale specific.
        .withLocale(Locale.getDefault)

      val dateTime = fmt.parseDateTime(s"$dayOfWeek $month $day $time $year")
      new AbsoluteDateTime(dateTime)
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
    //-l sets the lifetime of the obtained ticket to 1 second
    //val e = new Expect(s"""kinit -V -l 0:00:01 $fullPrincipal""", defaultValue)

    //Setting the credential cache to /dev/null would be better than setting the lifetime of the ticket to 1 second.
    //Because this way no ticket is ever created.
    val e = new Expect(s"""kinit -V -c /dev/null $fullPrincipal""", defaultValue)

    e.expect
      .when(s"Password for $fullPrincipal:")
        .sendln(password)
      .when(s"""Client '$fullPrincipal' not found in Kerberos database""")
        .returning(Left(NoSuchPrincipal))
    e.expect
      .when("Internal credentials cache error while storing credentials")
        //Because we set the credential cache to /dev/null kadmin fails when trying to write the ticket to the cache.
        //This means the authentication was successful and thus the password is correct.
        .returning(Right(true))
      .when("Authenticated to Kerberos")
        .returning(Right(true))
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
    e
  }
  //endregion

  //region <Policy commands>
  /**
    * Creates `policy` using `options`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the policy to create.
    * @param options the parameters to pass to the kadmin `add_policy` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#add-policy Add
    * Policy (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that creates `policy`.
    */
  def addPolicy(options: String, policy: String): Expect[Either[ErrorCase, Boolean]] = {
    doOperation { e =>
      val command: String = s"add_policy $options $policy"
      e.expect(kadminPrompt)
        .sendln(command)
      e.expect
        .when("Principal or policy already exists|Unknown code adb 1 while creating policy".r)
          .returningExpect(modifyPolicy(options, policy))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
          .returning { m: Match =>
            val error = m.group(1)
            if (error.trim == command) {
              Right(true)
            } else {
              Left(UnknownError(Some(error)))
            }
          }
    }
  }
  /**
    * Modifies `policy` using `options`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the principal to policy.
    * @param options the parameters to pass to the kadmin `modify_policy` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#modify-policy Modify
    * policy (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that modifies `policy`.
    */
  def modifyPolicy(options: String, policy: String): Expect[Either[ErrorCase, Boolean]] = {
    doOperation { e =>
      val command: String = s"modify_policy $options $policy"
      e.expect(kadminPrompt)
        .sendln(command)
      e.expect
        .addWhen(policyDoesNotExist)
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
          .returning { m: Match =>
            val error = m.group(1)
            if (error.trim == command) {
              Right(true)
            } else {
              Left(UnknownError(Some(error)))
            }
          }
    }
  }
  /**
    * Deletes `policy`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the policy to delete.
    * @return an Expect that deletes `policy`.
    */
  def deletePolicy(policy: String): Expect[Either[ErrorCase, Boolean]] = {
    doOperation { e =>
      val command = s"delete_policy -force $policy"
      e.expect(kadminPrompt)
        //With the -force option it no longer prompts for deletion.
        .sendln(command)
      e.expect
        .when("Policy is in use")
          .returning(Left(PolicyIsInUse))
        .addWhen(policyDoesNotExist)
          //This is what makes this operation idempotent
          .returning(Right(true))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
          .returning { m: Match =>
            val error = m.group(1)
            if (error.trim == command) {
              Right(true)
            } else {
              Left(UnknownError(Some(error)))
            }
          }
    }
  }
  /**
    * Performs the operation `f` over the output returned by "get_policy policy".
    * This is useful to read the policy attributes.
    *
    * $startedWithDoOperation
    *
    * @example {{{
    *   withPolicy(policy){ expectBlock =>
    *     expectBlock.when("""Minimum password length: (\d+)\n""".r)
    *       .returning{ m: Match =>
    *         //m.group(1) will contain the minimum password length.
    *       }
    * }}}
    * @param policy the policy to get the attributes.
    * @param f the operation to perform upon the policy attributes.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that lists the `policy` attributes, performs the operation `f` and then quits kadmin.
    */
  def withPolicy[R](policy: String)(f: ExpectBlock[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"get_policy $policy")
      e.expect
        .addWhen(policyDoesNotExist)
        .addWhen(insufficientPermission)
        .addWhens(f)
    }
  }
  //endregion

  private def insufficientPermission[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("""Operation requires ``([^']+)'' privilege""".r)
      .returning { m: Match =>
        Left(InsufficientPermissions(m.group(1)))
      }
  }
  private def principalDoesNotExist[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Principal does not exist")
      .returning(Left(NoSuchPrincipal))
  }
  private def policyDoesNotExist[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Policy does not exist")
      .returning(Left(NoSuchPolicy))
  }
  private def passwordIncorrect[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Password incorrect")
      .returning(Left(PasswordIncorrect))
  }
  private def passwordExpired[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Password expired")
      .returning(Left(PasswordIncorrect))
  }
  private def unknownError[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    //(?s) inline regex flag for dotall mode. In this mode '.' matches any character, including a line terminator.
    expectBlock.when(s"(?s)(.+?)(?=\n$kadminPrompt)".r)
      .returning { m: Match =>
        Left(UnknownError(Some(m.group(1))))
      }
  }

  private def preemptiveExit[R](when: When[Either[ErrorCase, R]]): Unit = {
    when
      //We send the quit to allow kadmin a graceful shutdown
      .sendln("quit")
      //This ensures the next expect(s) (if any) do not get executed and
      //we don't end up returning something else by mistake.
      .exit()
  }
}
