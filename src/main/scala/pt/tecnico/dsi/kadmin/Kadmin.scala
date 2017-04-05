package pt.tecnico.dsi.kadmin

import java.io.File
import java.nio.file.Files

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import work.martins.simon.expect.fluent.{ExpectBlock, RegexWhen, Expect => FluentExpect}
import work.martins.simon.expect.core.Expect
import scala.util.matching.Regex.Match

import pt.tecnico.dsi.kadmin.KadminUtils._

/**
  * @define idempotentOperation
  *  This operation is idempotent, that is, if this method is invoked twice for the same principal
  *  it will be successful in both invocations. This means that this operation can be repeated or retried as
  *  often as necessary without causing unintended effects.
  * @define startedWithDoOperation
  *  Kadmin will be started with the `doOperation` method, that is, a password authentication
  *  will performed as specified in the configuration.
  */
class Kadmin(val settings: Settings = new Settings()) extends LazyLogging {
  def this(config: Config) = this(new Settings(config))
  import settings._

  def getFullPrincipalName(principal: String): String = principal.trim match {
    case p if p.endsWith(s"@$realm") => p
    case p if p.contains("@") =>
      throw new IllegalArgumentException("Principal with unknown realm: " + principal.substring(principal.indexOf("@")))
    case p => s"$p@$realm"
  }

  /**
    * Creates an Expect that performs a kadmin operation `f` and then quits kadmin.
    * If the configuration `password-authentication` is set to true then the authentication is performed by
    * sending `password` and waiting for either an error message saying the password was incorrect or the kadmin prompt.
    * If the password was incorrect a Left(IncorrectPassword) will be returned.
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
  def doOperation[R](f: FluentExpect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val e = new FluentExpect(command, defaultUnknownError[R], scalaExpectSettings)
    if (passwordAuthentication) {
      e.expect(s"Password for ${getFullPrincipalName(principal)}: ")
        .sendln(password)
    }

    val expectBlock = e.expect
    if (passwordAuthentication) {
      expectBlock
        // Due to the fantastic consistency in kerberos commands we cannot use:
        //   addWhen(passwordIncorrect)
        // because in kadmin the error is "Incorrect password" and the `passwordIncorrect` function will add a when with:
        //   when("Password incorrect")
        // </sarcasm>
        .when("Incorrect password")
          .returning(Left(PasswordIncorrect))
          .addActions(preemptiveExit)
    }
    expectBlock
      .when("(.+) while initializing kadmin interface".r)
        .returning(m => Left(UnknownError(m.group(0))))
        .exit()
      .when(kadminPrompt)
        // All good. We can continue. We need to send a newline in order for `f` to see the KadminPrompt
        .sendln("")
      .addWhen(unknownError)
        .addActions(preemptiveExit)
    e.addExpectBlock(f)
    e.expect(kadminPrompt)
      .sendln("quit")
      .exit()
    e.toCore
  }

  /**
    * Creates `principal` using `options`. If `principal` already exists `modifyPrincipal` will be
    * invoked to make this operation idempotent (see the caveats bellow).
    *
    * $idempotentOperation However there are some <b>caveats</b>: if `principal` already exists
    * and any of `newPassword`, `randKey` or `keysalt` is defined, then `changePassword` will be invoked
    * after the `modifyPrincipal`. Since `changePassword` is not always idempotent this method might also not be.
    *
    * The password is not sent with the "-pw" option so it will not be exposed via the system process list.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to create.
    * @param options the parameters to pass to the kadmin `add_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#add-principal Add
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that creates `principal`.
    */
  def addPrincipal(options: String, principal: String, newPassword: Option[String] = None,
                  randKey: Boolean = false, keysalt: Option[String] = None): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val randKeyOption = if (randKey) "-randkey" else ""
    val saltOption = keysalt.map(s => s"""-e "$s"""").getOrElse("")

    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"add_principal $options $randKeyOption $saltOption $fullPrincipal")

      newPassword.foreach { pass =>
        e.expect(s"""password for principal "$fullPrincipal"""")
          .sendln(pass)
        e.expect(s"""password for principal "$fullPrincipal"""")
          .sendln(pass)
      }

      e.expect
        .when("Password is too short")
          .returning(Left(PasswordTooShort))
        .when("Password does not contain enough character classes")
          .returning(Left(PasswordWithoutEnoughCharacterClasses))
        .when(s"""Principal "$fullPrincipal" (created|added).""".r)
          .returning(Right(()))
        .when("Principal or policy already exists")
          .returningExpect{
            val m = modifyPrincipal(options, principal)
            if (newPassword.nonEmpty || randKey) {
              m.transform[Either[ErrorCase, Unit]] {
                case Right(()) => changePassword(principal, newPassword, randKey, keysalt)
              }{
                case Left(ec) => Left(ec)
              }
            } else {
              m
            }
          }
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }

  //region <Modify commands>
  /**
    * Modifies `principal` using `options`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to modify.
    * @param options the parameters to pass to the kadmin `modify_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-devel/doc/admin/admin_commands/kadmin_local.html#modify-principal Modify
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that modifies `principal`.
    */
  def modifyPrincipal(options: String, principal: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val clearPolicy = options.contains("-clearpolicy")
    val cleanedOptions = options
      .replaceAll("-clearpolicy", "")
      // Contrary to what is stated in the documentation the option -nokey is not valid in modify.
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
            // Its all good. We can continue.
          .when("User modification failed: No such attribute")
            // This happens when the principal already has no policy. (This error happens in kadmin.local)
            // Its all good. We can continue.
          .when("Database store error")
            // This happens when the principal already has no policy. (This error happens in kadmin)
            // Its all good. We can continue.
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
          .returning(Right(()))
      if (clearPolicy == false) {
        // We just need to check for these cases when the policy was not cleared. Because in the case the policy
        // was cleared these cases will already have been caught.
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
    * @param expirationDateTime the datetime to set as the principal expiration date. The timezone will be ignored.
    * @return an Expect that expires `principal`.
    */
  def expirePrincipal(principal: String, expirationDateTime: ExpirationDateTime = DateTime.now()): Expect[Either[ErrorCase, Unit]] = {
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
    * @param expirationDateTime the datetime to set as the password expiration date. The timezone will be ignored.
    * @param force whether or not to clear the principal policy. By default this is set to false.
    * @return an Expect that sets the password expiration date of `principal` to `date`.
    */
  def expirePrincipalPassword(principal: String, expirationDateTime: ExpirationDateTime = DateTime.now(),
                              force: Boolean = false): Expect[Either[ErrorCase, Unit]] = {
    val dateTimeString = expirationDateTime.toKadminRepresentation
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
    * Changes the `principal` password to `newPassword` or sets its key to a random value. Optionally its salt to `salt`.
    *
    * In some cases this operation might not be idempotent. For example: if the policy assigned to `principal`
    * does not allow the same password to be reused, the first time the password is changed it will be successful,
    * however on the second time it will fail with an ErrorCase `PasswordIsBeingReused`.
    *
    * The password is not sent with the "-pw" option so it will not be exposed via the system process list.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to change the password.
    * @param newPassword the new password
    * @return an Expect that changes `principal` password.
    */
  def changePassword(principal: String, newPassword: Option[String] = None,
                     randKey: Boolean = false, keysalt: Option[String] = None): Expect[Either[ErrorCase, Unit]] = {
    require(newPassword.nonEmpty ^ randKey, "Either newPassword must be a Some or randKey must be true.")
    val randKeyOption = if (randKey) "-randkey" else ""
    val saltOption = keysalt.map(s => s"""-e "$s"""").getOrElse("")

    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(s"""change_password $randKeyOption $saltOption $fullPrincipal""")

      newPassword match {
        case None =>
          // If newPassword is a None then randKey must be true
          e.expect
            .addWhen(principalDoesNotExist)
            .addWhen(insufficientPermission)
            .when(s"""Key for "$fullPrincipal" randomized""")
              .returning(Right(()))
            .addWhen(unknownError)
        case Some(pass) =>
          e.expect(s"""password for principal "$fullPrincipal":""")
            .sendln(pass)
          e.expect(s"""password for principal "$fullPrincipal":""")
            .sendln(pass)
          e.expect
            .when("Password is too short")
              .returning(Left(PasswordTooShort))
            .when("Password does not contain enough character classes")
              .returning(Left(PasswordWithoutEnoughCharacterClasses))
            .when("Cannot reuse password")
              .returning(Left(PasswordIsBeingReused))
            .when(s"""Password for "$fullPrincipal" changed.""")
              .returning(Right(()))
            .addWhen(principalDoesNotExist) //Seems like kadmin does not fail fast
            .addWhen(insufficientPermission) //Seems like kadmin does not fail fast
            .addWhen(unknownError)
      }
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
  def deletePrincipal(principal: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    doOperation { e =>
      e.expect(kadminPrompt)
        // With the -force option it no longer prompts for deletion.
        .sendln(s"delete_principal -force $fullPrincipal")
      e.expect
        .when(s"""Principal "$fullPrincipal" deleted.""")
          .returning(Right(Unit))
        .addWhen(principalDoesNotExist)
          // This is what makes this operation idempotent
          .returning(Right(Unit))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }

  //region <Get/Read commands>
  /**
    * Performs the operation `f` over the output returned by "get_principal principal".
    * This is useful to read the principal attributes that are not included with `getPrincipal`.
    *
    * $startedWithDoOperation
    *
    * Consider using the `parseDateTime` method if `f` is to parse a date time.
    * And `parseDuration` method if `f` is to parse a duration.
    *
    * @example {{{
    *   withPrincipal(principal){ expectBlock =>
    *     expectBlock.when("""Maximum ticket life: ([^\n]+)\n""".r)
    *       .returning{ m: Match =>
    *         val maximumTicketLife = parseDuration(m.group(1))
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
        .addWhen(communicationFailure)
        .addWhens(f)
    }
  }

  /**
    * Performs a "get_principal principal" and parses the output to the domain class `Principal`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal name.
    * @return an Expect that returns the `Principal`.
    */
  def getPrincipal(principal: String): Expect[Either[ErrorCase, Principal]] = withPrincipal(principal){ expectBlock =>
    // (?s) inline regex flag for dotall mode. In this mode '.' matches any character, including a line terminator.
    expectBlock.when("""(?s)Expiration date: ([^\n]+)
                      |Last password change: ([^\n]+)
                      |Password expiration date: ([^\n]+)
                      |Maximum ticket life: ([^\n]+)
                      |Maximum renewable life: ([^\n]+)
                      |Last modified: ([^(]+) \((.*)\)
                      |Last successful authentication: ([^\n]+)
                      |Last failed authentication: ([^\n]+)
                      |Failed password attempts: (\d+)
                      |Number of keys: \d+
                      |(.*?)MKey: vno (\d+)
                      |Attributes:([^\n]*)
                      |Policy: ([^\n]+)""".stripMargin.r(
            "expirationDateTime", "lastPasswordChangeDateTime", "passwordExpirationDateTime",
            "maximumTicketLife", "maximumRenewableLife",
            "lastModifiedDateTime", "lastModifiedBy",
            "lastSuccessfulAuthenticationDateTime", "lastFailedAuthenticationDateTime", "failedPasswordAttempts",
            "keys", "masterKey", "attributes", "policy"))
      .returning { m =>
        val groups = m.groupNames.map(name => (name, m.group(name))).toMap
        val (datesString, others) = groups.partition { case (name, _) => name.contains("DateTime") }
        val datesStream = datesString.toStream.map { case (key, value) => (key, parseDateTime(value)) }

        datesStream.collectFirst {
          case (_, Left(errorCase)) => Left(errorCase)
        } getOrElse {
          val dates = datesStream.collect { case (name, Right(date)) => (name, date) }.toMap
          Right(Principal(getFullPrincipalName(principal), dates("expirationDateTime"),
            dates("lastPasswordChangeDateTime"), dates("passwordExpirationDateTime"),
            parseDuration(others("maximumTicketLife")), parseDuration(others("maximumRenewableLife")),
            dates("lastModifiedDateTime"), m.group("lastModifiedBy"),
            dates("lastSuccessfulAuthenticationDateTime"), dates("lastFailedAuthenticationDateTime"), others("failedPasswordAttempts").toInt,
            others("keys").split("\n").flatMap(Key.fromString).toSet, others("masterKey").toInt,
            others("attributes").split(" ").filter(_.nonEmpty).toSet,
            Option(others("policy")).map(_.trim).filter(_ != "[none]")
          ))
        }
      }
  }

  /**
    * List all principals matching the glob expression.
    *
    * If `expressionGlob` is the empty String all principals will be listed.
    *
    * @param expressionGlob the glob expression to pass to kadmin list_principals.
    * @return an Expect that returns the list of principals.
    */
  def listPrincipals(expressionGlob: String): Expect[Either[ErrorCase, Seq[String]]] = {
    val expression = if (expressionGlob.isEmpty || expressionGlob.endsWith(s"@$realm")) {
      expressionGlob
    } else {
      expressionGlob.replaceAll("@.*$", "") + s"@$realm"
    }
    val command = s"list_principals $expression"
    doOperation { e =>
      e.expect(kadminPrompt)
        .sendln(command)
      e.expect
        .addWhen(insufficientPermission)
        .addWhen(unableToAccessDatabase)
        .when(s"${expression.replace("*", "\\*").replace("?", "\\?")}\\s+((?>[^@]+@$realm\n)*)(?=$kadminPrompt\\s*)".r)
          .returning { m: Match =>
            Right(m.group(1).split("\n").toSeq)
          }
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
  def checkPassword(principal: String, password: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    // -l sets the lifetime of the obtained ticket to 1 second
    // val e = new Expect(s"""kinit -V -l 0:00:01 $fullPrincipal""", defaultValue)

    // -c sets the credential cache to /dev/null. This ensures no ticket is ever created.
    val e = new FluentExpect(s"""kinit -V -c /dev/null $fullPrincipal""", defaultUnknownError[Unit], scalaExpectSettings)

    e.expect
      .when(s"Password for $fullPrincipal:")
        .sendln(password)
      .when(s"""Client '$fullPrincipal' not found in Kerberos database""")
        .returning(Left(NoSuchPrincipal))
    e.expect
      .when("Internal credentials cache error while storing credentials")
        // Because we set the credential cache to /dev/null kadmin fails when trying to write the ticket to the cache.
        // This means the authentication was successful and thus the password is correct.
        .returning(Right(()))
      .when("Authenticated to Kerberos")
        .returning(Right(()))
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
    e.toCore
  }
  //endregion

  //region <Keytab commands>
  /**
    * @return The File for the `principal` keytab.
    */
  def getKeytabFile(principal: String): File = {
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
    * @param options the options to pass to the ktadd command. These are not check for validity.
    * @return an Expect that creates the keytab for `principal`.
    */
  def createKeytab(options: String, principal: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = getFullPrincipalName(principal)
    val keytabPath = getKeytabFile(principal).getAbsolutePath
    doOperation{ e =>
      e.expect(kadminPrompt)
        .sendln(s"ktadd -keytab $keytabPath $options $fullPrincipal")
      e.expect
        .when("Entry for principal (.*?) added to keytab".r)
          .returning(Right(()))
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
  def obtainKeytab(principal: String): Either[ErrorCase, Array[Byte]] = {
    getKeytabFile(principal) match {
      case f if f.canRead =>
        Right(Files.readAllBytes(f.toPath))
      case f if !f.exists() =>
        logger.info(s"""Keytab file "${f.getAbsolutePath}" doesn't exist.""")
        Left(KeytabDoesNotExist)
      case f if !f.canRead =>
        val currentUser = System.getProperty("user.name")
        logger.info(s"""User "$currentUser" has insufficient permissions to read the keytab file "${f.getAbsolutePath}".""")
        Left(KeytabIsNotReadable)
      case _ =>
        Left(UnknownError())
    }
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
  def addPolicy(options: String, policy: String): Expect[Either[ErrorCase, Unit]] = {
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
              Right(())
            } else {
              Left(UnknownError(error))
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
  def modifyPolicy(options: String, policy: String): Expect[Either[ErrorCase, Unit]] = {
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
              Right(())
            } else {
              Left(UnknownError(error))
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
  def deletePolicy(policy: String): Expect[Either[ErrorCase, Unit]] = {
    doOperation { e =>
      // With the -force option kadmin no longer prompts for deletion.
      val command = s"delete_policy -force $policy"
      e.expect(kadminPrompt)
        .sendln(command)
      e.expect
        .when("Policy is in use")
          .returning(Left(PolicyIsInUse))
        .addWhen(policyDoesNotExist)
          // This is what makes this operation idempotent
          .returning(Right(()))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
          .returning { m: Match =>
            val error = m.group(1)
            if (error.trim == command) {
              Right(())
            } else {
              Left(UnknownError(error))
            }
          }
    }
  }
  /**
    * Performs the operation `f` over the output returned by "get_policy $$policy".
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
  /**
    * Performs a "get_policy $$policy" and parses the output to the domain class `Policy`.
    *
    * $startedWithDoOperation
    *
    * @param policy the policy name.
    * @return an Expect that returns the `Policy`.
    */
  def getPolicy(policy: String): Expect[Either[ErrorCase, Policy]] = withPolicy(policy){ expectBlock =>
    expectBlock.when("""Maximum password life: (\d+)
                       |Minimum password life: (\d+)
                       |Minimum password length: (\d+)
                       |Minimum number of password character classes: (\d+)
                       |Number of old keys kept: (\d+)
                       |Maximum password failures before lockout: (\d+)
                       |Password failure count reset interval: ([^\n]+)
                       |Password lockout duration: ([^\n]+)(.*?)""".stripMargin.r(
      "maximumLife", "minimumLife",
      "minimumLength", "minimumCharacterClasses",
      "oldKeysKept", "maximumFailuresBeforeLockout",
      "failureCountResetInterval", "lockoutDuration", "allowedKeySalts"))
      .returning { m: Match =>
        val keysalts = Option(m.group("allowedKeySalts")).flatMap { s =>
          "Allowed key/salt types: (.*)".r.findFirstMatchIn(s)
        }.map { m =>
          m.group(1).split(",").flatMap(KeySalt.fromString).toSet
        }

        Right(Policy(
          policy, parseDuration(m.group("maximumLife")), parseDuration(m.group("minimumLife")),
          m.group("minimumLength").toInt, m.group("minimumCharacterClasses").toInt,
          m.group("oldKeysKept").toInt, m.group("maximumFailuresBeforeLockout").toInt,
          parseDuration(m.group("failureCountResetInterval")), parseDuration(m.group("lockoutDuration")),
          keysalts
        ))
      }
  }
  //endregion

  def unknownError[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]): RegexWhen[Either[ErrorCase, R]] = {
    //(?s) inline regex flag for dotall mode. In this mode '.' matches any character, including a line terminator.
    expectBlock.when(s"(?s)(.+?)(?=\n$kadminPrompt)".r)
      .returning { m: Match =>
        Left(UnknownError(m.group(1)))
      }
  }
}
