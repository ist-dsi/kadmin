package pt.tecnico.dsi.kadmin

import java.io.File
import java.nio.file.Files

import scala.concurrent.duration.FiniteDuration

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import pt.tecnico.dsi.kadmin.KadminUtils._
import work.martins.simon.expect.core.Expect
import work.martins.simon.expect.fluent.{ExpectBlock, Expect => FluentExpect}
import work.martins.simon.expect.{StdErr, StringUtils}

/**
  * @define idempotentOperation
  *  This operation is idempotent, that is, if this method is invoked twice for the same principal
  *  it will be successful in both invocations. This means that this operation can be repeated or retried as
  *  often as necessary without causing unintended effects.
  * @define startedWithDoOperation
  *  Kadmin will be started with the `doOperation` method, that is, a password authentication
  *  will performed as specified in the configuration.
  */
class Kadmin(val settings: Settings) extends LazyLogging {
  def this(realm: String, principal: String, password: String, command: Seq[String]) = {
    this(new Settings(realm, principal, "", password, command))
  }
  def this(realm: String, principal: String, password: String, command: String) = {
    this(realm, principal, password, StringUtils.splitBySpaces(command))
  }
  def this(realm: String, principal: String, password: String) = {
    this(realm, principal, password, Settings.defaultCommand(usingKeytab = false))
  }
  def this(realm: String, principal: String, keytab: File, command: Seq[String]) = {
    this(new Settings(realm, principal, keytab.getAbsolutePath, "", command))
  }
  def this(realm: String, principal: String, keytab: File, command: String) = {
    this(realm, principal, keytab, StringUtils.splitBySpaces(command))
  }
  def this(realm: String, principal: String, keytab: File) = {
    this(realm, principal, keytab, Settings.defaultCommand(usingKeytab = true))
  }

  import settings._

  /**
    * @param principal the principal name to append the realm to.
    * @return The principal with the realm appended. Throws IllegalArgumentException if the principal
    *         contains an unknown realm aka one that is different from the one in `Settings`.
    * @group Generic Operations
    */
  def fullPrincipalName(principal: String): String = principal.trim match {
    case p if p.endsWith(s"@$realm") => p
    case p if p.contains("@") =>
      throw new IllegalArgumentException("Principal with unknown realm: " + principal.substring(principal.indexOf("@")))
    case p => s"$p@$realm"
  }

  /**
    * Performs the kadmin `command` using a newly created expect where the authentication, either via password or
    * keytab, has already been performed. The function `f` can then be used to describe the interactions needed
    * to validate the performed command. After `f` the kadmin is exited by sending it the "quit" command.
    *
    * A keytab authentication is preferred over a password authentication. In order for a password authentication
    * to be performed the `keytab` setting must be an empty string. When performing a password authentication the
    * password will not be exposed via the system process list nor it will be exposed in the logs.
    *
    * @example {{{
    *   doOperation(s"getprinc $$fullPrincipal") { e =>
    *     e.expect("Principal not found")
    *       .returning(Left(NoSuchPrincipal))
    *   }
    * }}}
    * @param command the kerberos administration command to perform.
    * @param f aditional expect operations to perform after executing the command. This is usefull to validate the
    *          command, or to set the value returned by expect to a more meaningful type.
    * @tparam R the type for the Right of the Either returned by the Expect.
    * @return an Expect that performs the operation `f` and then quits kadmin.
    * @group Generic Operations
    */
  def doOperation[R](command: String)(f: FluentExpect[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    val finalCommand = settings.command
      .map(_.replaceAllLiterally("$FULL_PRINCIPAL", s"$principal@$realm"))
      .map(_.replaceAllLiterally("$KEYTAB", keytab))
    val e = new FluentExpect(finalCommand, defaultUnknownError[R], scalaExpectSettings)
    if (keytab.isEmpty) {
      e.expect.addWhen(expectAndSendPassword(fullPrincipalName(principal), password))
    }

    val expectBlock = e.expect
    if (keytab.isEmpty) {
      expectBlock
        .addWhen(passwordIncorrect)
          .exit()
    }
    expectBlock
      .when("(.+) while initializing kadmin interface".r, readFrom = StdErr)
        .returning(m => Left(UnknownError(m.group(0))))
        .exit()
      .when(kadminPrompt)
        .sendln(command)
      .addWhen(unknownError)
        .exit()
    e.addExpectBlock(f)
    e.expect(kadminPrompt)
      .sendln("quit")
      .exit()
    e.toCore
  }

  //region <Principal commands>
  /**
    * Creates `principal` using `options`. If `principal` already exists `modifyPrincipal` will be
    * invoked to make this operation idempotent (see the caveats bellow).
    *
    * This method is more general than the one receiving a `Principal`, as it allows to specify only the
    * pretended options, where as the one receiving a `Principal` will always use the same options.
    *
    * $idempotentOperation However there are some <b>caveats</b>: if `principal` already exists
    * and any of `newPassword`, `randKey` or `keysalt` is defined, then `changePassword` will be invoked
    * after the `modifyPrincipal`. Since `changePassword` is not always idempotent this method might also not be.
    *
    * The password is not sent with the "-pw" option so it will not be exposed via the system process list.
    * Nor it will be exposed via the logs.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to create.
    * @param options the parameters to pass to the kadmin `add_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#add-principal Add
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that creates `principal`.
    * @group Principal Operations
    */
  def addPrincipal(options: String, principal: String, password: Option[String] = None,
                   randKey: Boolean = false, keysalt: Option[KeySalt] = None): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = fullPrincipalName(principal)
    val randKeyOption = if (randKey) "-randkey" else ""
    val saltOption = keysalt.map(s => s"""-e "${s.toKadminRepresentation}"""").getOrElse("")

    doOperation(s"add_principal $options $randKeyOption $saltOption $fullPrincipal") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      password.foreach { pass =>
        e.expect.addWhen(expectAndSendPassword(fullPrincipal, pass))
        e.expect.addWhen(expectAndSendPassword(fullPrincipal, pass))
      }

      // This effectively ignores this warning. This is necessary because:
      // The next expect block matches with insufficient permission and with unknown error both from StdErr.
      // When adding a principal using a principal without permissions (eg: noPermissions) we get on StdErr
      // the warning about no policy *and then* the insufficient permissions.
      // If we included the warning and then make it be a success (since in the general case it will be a success)
      // we would be returning success for the noPermissions case when in fact it should be an error.
      // If we simply did not include it then it would be caught by the unknown error and thus
      // returning the wrong result. By ignoring the message we solve all the problems.
      if ("""-policy\b""".r.findFirstIn(options).isEmpty && !options.contains("-clearpolicy")) {
        e.expect(s"WARNING: no policy specified for $fullPrincipal; defaulting to no policy\n", readFrom = StdErr)
      }

      e.expect
        .addWhen(insufficientPermission)
        .addWhen(passwordTooShort)
        .addWhen(passwordWithoutEnoughCharacterClasses)
        .when(s"""Principal "${fullPrincipal.replace(".", "\\.")}" (created|added)""".r)
          .returning(Right(()))
        .when("Principal or policy already exists", readFrom = StdErr)
          .returningExpect {
            val m = modifyPrincipal(options, principal)
            if (password.nonEmpty || randKey) {
              m.transform[Either[ErrorCase, Unit]](
                { case Right(()) => changePassword(principal, password, randKey, keysalt) },
                { case Left(ec) => Left(ec) })
            } else {
              m
            }
          }
        .addWhen(unknownError)
    }
  }
  /**
    * Creates `principal` using `options`. The parameters to `add_principal` will be computed from `principal`.
    * If `principal` already exists `modifyPrincipal` will be invoked to make this
    * operation idempotent (see the caveats bellow).
    *
    * $idempotentOperation However there are some <b>caveats</b>: if `principal` already exists
    * and any of `newPassword`, `randKey` or `keysalt` is defined, then `changePassword` will be invoked
    * after the `modifyPrincipal`. Since `changePassword` is not always idempotent this method might also not be.
    *
    * The password is not sent with the "-pw" option so it will not be exposed via the system process list.
    * Nor it will be exposed via the logs.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to create.
    * @return an Expect that creates `principal`.
    * @group Principal Operations
    */
  def addPrincipal(principal: Principal, password: Option[String], randKey: Boolean, keysalt: Option[KeySalt]): Expect[Either[ErrorCase, Unit]] = {
    addPrincipal(principal.options, principal.name, password, randKey, keysalt)
  }

  /**
    * Modifies `principal` using `options`. This method is more general than the one receiving a `Principal`,
    * as it allows to specify only the pretended options, where as the one receiving a `Principal` will always use
    * the same options.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to modify.
    * @param options the parameters to pass to the kadmin `modify_principal` operation.
    * See [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#modify-principal Modify
    * Principal (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that modifies `principal`.
    * @group Principal Operations
    */
  def modifyPrincipal(options: String, principal: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = fullPrincipalName(principal)
    // Contrary to what is stated in the documentation the option -nokey is not valid in modify.
    val cleanedOptions = options.replaceAll("""-nokey\b""", "")

    doOperation(s"""modify_principal $cleanedOptions $fullPrincipal""") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      e.expect
        .when(s"""Principal "$fullPrincipal" modified.""")
          .returning(Right(()))
        .addWhen(principalDoesNotExist)
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
    }
  }
  /**
    * Modifies `principal` using `options`. The parameters to `modify_principal` will be computed from `principal`.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to modify.
    * @return an Expect that modifies `principal`.
    * @group Principal Operations
    */
  def modifyPrincipal(principal: Principal): Expect[Either[ErrorCase, Unit]] = modifyPolicy(principal.options, principal.name)

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
    * @group Principal Operations
    */
  def expirePrincipal(principal: String, expirationDateTime: ExpirationDateTime = DateTime.now()): Expect[Either[ErrorCase, Unit]] = {
    modifyPrincipal(s"""-expire "${expirationDateTime.toKadminRepresentation}"""", principal)
  }

  /**
    * Set the password expiration date of `principal` to `datetime` with some caveats, read below.
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
    * @group Principal Operations
    */
  def expirePrincipalPassword(principal: String, expirationDateTime: ExpirationDateTime = DateTime.now(),
                              force: Boolean = false): Expect[Either[ErrorCase, Unit]] = {
    val dateTimeString = expirationDateTime.toKadminRepresentation
    modifyPrincipal(s"""${if (force) "-clearpolicy" else ""} -pwexpire "$dateTimeString"""", principal)
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
    * Nor it will be exposed via the logs.
    *
    * $startedWithDoOperation
    *
    * @param principal the principal to change the password.
    * @param newPassword the new password
    * @return an Expect that changes `principal` password.
    * @group Principal Operations
    */
  def changePassword(principal: String, newPassword: Option[String] = None,
                     randKey: Boolean = false, keysalt: Option[KeySalt] = None): Expect[Either[ErrorCase, Unit]] = {
    require(newPassword.nonEmpty ^ randKey, "Either newPassword must be a Some or randKey must be true.")
    val randKeyOption = if (randKey) "-randkey" else ""
    val saltOption = keysalt.map(s => s"""-e "${s.toKadminRepresentation}"""").getOrElse("")

    val fullPrincipal = fullPrincipalName(principal)
    doOperation(s"""change_password $randKeyOption $saltOption $fullPrincipal""") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      newPassword match {
        case None =>
          // If newPassword is a None then randKey must be true
          e.expect
            .when(s"""Key for "$fullPrincipal" randomized""")
              .returning(Right(()))
            .addWhen(insufficientPermission)
            .addWhen(principalDoesNotExist)
            .addWhen(unknownError)
        case Some(pass) =>
          e.expect.addWhen(expectAndSendPassword(fullPrincipal, pass))
          e.expect.addWhen(expectAndSendPassword(fullPrincipal, pass))
          e.expect
            .when(s"""Password for "$fullPrincipal" changed.""")
              .returning(Right(()))
            .addWhen(passwordTooShort)
            .addWhen(passwordWithoutEnoughCharacterClasses)
            .when("Cannot reuse password", readFrom = StdErr)
              .returning(Left(PasswordIsBeingReused))
            .addWhen(insufficientPermission) //Seems like kadmin does not fail fast
            .addWhen(principalDoesNotExist) //Seems like kadmin does not fail fast
            .addWhen(unknownError)
      }
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
    * @group Principal Operations
    */
  def deletePrincipal(principal: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = fullPrincipalName(principal)
    // With the -force option it no longer prompts for deletion.
    doOperation(s"delete_principal -force $fullPrincipal") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      e.expect
        .when(s"""Principal "$fullPrincipal" deleted.""")
          .returning(Right(()))
        .addWhen(principalDoesNotExist)
          // This is what makes this operation idempotent
          .returning(Right(()))
        .addWhen(insufficientPermission)
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
    * @group Principal Operations
    */
  def deletePrincipal(principal: Principal): Expect[Either[ErrorCase, Unit]] = deletePrincipal(principal.name)

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
    * @group Principal Operations
    */
  def withPrincipal[R](principal: String)(f: ExpectBlock[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    doOperation(s"get_principal ${fullPrincipalName(principal)}") { e: FluentExpect[Either[ErrorCase, R]] =>
      e.expect
        .addWhen(principalDoesNotExist)
        .addWhen(insufficientPermission)
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
    * @group Principal Operations
    */
  def getPrincipal(principal: String): Expect[Either[ErrorCase, Principal]] = withPrincipal(principal){ expectBlock: ExpectBlock[Either[ErrorCase, Principal]] =>
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
        // TODO: fix this ugly code to something simpler
        val groups = m.groupNames.map(name => (name, m.group(name))).toMap
        val (datesString, othersDates) = groups.partition { case (name, _) => name.contains("DateTime") }
        val (durationsString, others) = othersDates.partition { case (name, _) => name.contains("maximum") }
        // We convert them to Streams in order to fail as soon as an error in found
        val datesStream = datesString.toStream.map { case (key, value) => (key, parseDateTime(value)) }
        val durationsStream = durationsString.toStream.map { case (key, value) => (key, parseDuration(value)) }

        datesStream.collectFirst {
          case (_, Left(errorCase)) => Left(errorCase)
        } getOrElse {
          durationsStream.collectFirst {
            case (_, Left(errorCase)) => Left(errorCase)
          } getOrElse {
            val dates = datesStream.collect { case (name, Right(date)) => (name, date) }.toMap
            val durations = durationsStream.collect { case (name, Right(duration)) => (name, duration) }.toMap

            Right(Principal(fullPrincipalName(principal), dates("expirationDateTime"),
              dates("lastPasswordChangeDateTime"), dates("passwordExpirationDateTime"),
              durations("maximumTicketLife"), durations("maximumRenewableLife"),
              dates("lastModifiedDateTime"), m.group("lastModifiedBy"),
              dates("lastSuccessfulAuthenticationDateTime"), dates("lastFailedAuthenticationDateTime"), others("failedPasswordAttempts").toInt,
              others("keys").split("\n").flatMap(Key.fromString).toSet, others("masterKey").toInt,
              others("attributes").split(" ").filter(_.nonEmpty).toSet,
              Option(others("policy")).map(_.trim).filter(_ != "[none]")
            ))
          }
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
    * @group Principal Operations
    */
  def listPrincipals(expressionGlob: String): Expect[Either[ErrorCase, Seq[String]]] = {
    val expression = if (expressionGlob.isEmpty || expressionGlob.endsWith(s"@$realm")) {
      expressionGlob
    } else {
      expressionGlob.replaceAll("@.*$", "") + s"@$realm"
    }
    val command = s"list_principals $expression"
    doOperation(command) { e: FluentExpect[Either[ErrorCase, Seq[String]]] =>
      e.expect
        .addWhen(insufficientPermission)
        .when(s"${expression.replace("*", "\\*").replace("?", "\\?")}\n(([^\n]+\n)+)(?=$kadminPrompt)".r)
          .returning(m => Right(m.group(1).split("\n").toSeq))
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
    * @group Principal Operations
    */
  def checkPassword(principal: String, password: String): Expect[Either[ErrorCase, Unit]] = {
    val fullPrincipal = fullPrincipalName(principal)
    // -V turns on verbose output
    // -c sets the credential cache to /dev/null. This ensures no ticket is ever created.
    val e = new FluentExpect(s"""kinit -V -c /dev/null $fullPrincipal""", defaultUnknownError[Unit], scalaExpectSettings)
    e.expect
      .addWhen(expectAndSendPassword(fullPrincipal, password))
      .when(s"""Client '$fullPrincipal' not found in Kerberos database""", readFrom = StdErr)
        .returning(Left(NoSuchPrincipal))
    e.expect
      .when("Authenticated to Kerberos", readFrom = StdErr)
        .returning(Right(()))
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
        .exit()
    e.toCore
  }
  //endregion

  //region <Keytab commands>
  /**
    * @return The File for the `principal` keytab.
    * @group Keytab Operations
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
    * @group Keytab Operations
    */
  def createKeytab(options: String, principal: String): Expect[Either[ErrorCase, Unit]] = {
    val keytabPath = getKeytabFile(principal).getAbsolutePath
    doOperation(s"ktadd -keytab $keytabPath $options ${fullPrincipalName(principal)}"){ e: FluentExpect[Either[ErrorCase, Unit]] =>
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
    * @group Keytab Operations
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
  /** Most of policy methods look to be more complicated then they should.
    * For example modifyPolicy could be (in a perfect world):
    *
    * {{{
    *   doOperation(s"modify_policy $options $policy") { e: FluentExpect[Either[ErrorCase, Unit]] =>
    *     e.expect
    *      .addWhen(policyDoesNotExist)
    *      .addWhen(insufficientPermission)
    *      .addWhen(unknownError)
    *      .when(kadminPrompt)
    *        .returning(Right(()))
    *        // We need to exit because if we don't doOperation will try to match with kadmin prompt causing a timeout
    *        .exit()
    *   }
    * }}}
    *
    * However it is not because if it were it would not be idempotent. It is not trivial to understand why, so we
    * will try our best to explain it.
    *
    * In the ideal implementation we have one expect block with 3 whens
    *  · the first three handle error situations (therefor reading from StdErr)
    *  · and the last one handles the success case (reading from StdOut)
    * Because the expect block has whens reading from both StdOut and StdErr scala-expect will <b>concurrently</b> read
    * from both InputStreams and match with the one which has output first. The kadmin will output the prompt
    * and the "Policy does not exist" roughly at the same time, which means scala-expect can see the StdOut before
    * it sees the StdErr. If one is trying to modify a non existing policy this would mean we would be returning
    * a success (because scala-expect matched with StdOut) when a error should be returned. The principal commands
    * do not suffer from this problem because the success case always outputs some text like "Principal "bla@EXAMPLE.COM" modified."
    *
    * To overcome this problem we employed the following strategy:
    *  · If any of the error cases match, we return the error case and exit right away to prevent the next expect
    *    block from being executed.
    *  · If we match with the prompt (the success case) we don't consider it a success right away, and "try again"
    *    by sending a newline, which allows the next expect block to see the prompt again.
    *  · In the next expect block we again match with the error cases, and with the prompt, but now we consider it
    *    a success.
    * Why does this solve the problem? Scala-expect maintains a list of which InputStream was output (change the log
    * level of scala-expect to TRACE and you will see it).
    * As the process is generating output scala-expect is populating this list. So in the non existing policy case
    * this list could be populated with:
    *  · StdErr then StdOut, aka, the "Policy does not exist" followed by the prompt.
    *  · StdOut then StdErr, aka, the prompt followed by the "Policy does not exist". Or:
    * If the first case we catch the error first and all is good. However in the second case we catch the success.
    * By trying again, we are effectively sending the prompt to the end of the list, and causing the next element
    * of the list to be retrieved thus matching with the error case.
    * If no error case ever existed we simply will be matching with the prompt again, and now we are sure it is a
    * success.
    */


  /**
    * Creates `policy` using `options`. This method is more general than the one receiving a `Policy`,
    * as it allows to specify only the pretended options, where as the one receiving a `Policy` will always use
    * the same options.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the policy to create.
    * @param options the parameters to pass to the kadmin `add_policy` operation.
    * See [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#add-policy Add
    * Policy (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that creates `policy`.
    * @group Policy Operations
    */
  def addPolicy(options: String, policy: String): Expect[Either[ErrorCase, Unit]] = {
    doOperation(s"add_policy $options $policy") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      e.expect
        .when("Principal or policy already exists|Unknown code adb 1".r, readFrom = StdErr)
          .returningExpect(modifyPolicy(options, policy)) // Returning expect already has an implicit exit
        .addWhen(insufficientPermission)
          .exit()
        .addWhen(unknownError)
          .exit()
        .when(kadminPrompt)
          // Lets try again
          .sendln("")
      e.expect
        .when("Principal or policy already exists|Unknown code adb 1".r, readFrom = StdErr)
          .returningExpect(modifyPolicy(options, policy))
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
        .when(kadminPrompt)
          .returning(Right(()))
          // We need to exit because if we don't doOperation will try to match with kadmin prompt causing a timeout
          .exit()
    }
  }
  /**
    * Creates `policy` using `options`. The parameters to `add_policy` will be computed from `policy`.
    * All the parameters listed [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#add-policy here]]
    * will be used, except "allowedkeysalts", which will only be used if it is a Some.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the policy to create.
    * @return an Expect that creates `policy`.
    * @group Policy Operations
    */
  def addPolicy(policy: Policy): Expect[Either[ErrorCase, Unit]] = addPolicy(policy.options, policy.name)

  /**
    * Modifies `policy` using `options`. This method is more general than the one receiving a `Policy`,
    * as it allows to specify only the pretended options, where as the one receiving a `Policy` will always use
    * the same options.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the principal to policy.
    * @param options the parameters to pass to the kadmin `modify_policy` operation.
    * See [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#modify-policy Modify
    * policy (MIT Kerberos)]] for a full list. The parameters are not checked for validity.
    * @return an Expect that modifies `policy`.
    * @group Policy Operations
    */
  def modifyPolicy(options: String, policy: String): Expect[Either[ErrorCase, Unit]] = {
    doOperation(s"modify_policy $options $policy") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      e.expect
        .addWhen(policyDoesNotExist)
          .exit()
        .addWhen(insufficientPermission)
          .exit()
        .addWhen(unknownError)
          .exit()
        .when(kadminPrompt)
          // Lets try again
          .sendln("")
      e.expect
        .addWhen(policyDoesNotExist)
        .addWhen(insufficientPermission)
        .addWhen(unknownError)
        .when(kadminPrompt)
          .returning(Right(()))
          // We need to exit because if we don't doOperation will try to match with kadmin prompt causing a timeout
          .exit()
    }
  }
  /**
    * Modifies `policy` using `options`. The parameters to `modify_policy` will be computed from `policy`.
    * All the parameters listed [[http://web.mit.edu/kerberos/krb5-1.15/doc/admin/admin_commands/kadmin_local.html#add-policy here]]
    * will be used, except "allowedkeysalts", which will only be used if it is a Some.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the principal to policy.
    * @return an Expect that modifies `policy`.
    * @group Policy Operations
    */
  def modifyPolicy(policy: Policy): Expect[Either[ErrorCase, Unit]] = modifyPolicy(policy.options, policy.name)

  /**
    * Deletes `policy`.
    *
    * $idempotentOperation
    *
    * $startedWithDoOperation
    *
    * @param policy the policy to delete.
    * @return an Expect that deletes `policy`.
    * @group Policy Operations
    */
  def deletePolicy(policy: String): Expect[Either[ErrorCase, Unit]] = {
    // With the -force option kadmin no longer prompts for deletion.
    doOperation(s"delete_policy -force $policy") { e: FluentExpect[Either[ErrorCase, Unit]] =>
      e.expect
        .addWhen(insufficientPermission)
          .exit()
        .addWhen(policyDoesNotExist)
          // This is what makes this operation idempotent
          .returning(Right(()))
          .exit()
        .addWhen(unknownError)
          .exit()
        .when(kadminPrompt)
          // Lets try again
          .sendln("")
      e.expect
        .addWhen(insufficientPermission)
        .addWhen(policyDoesNotExist)
          .returning(Right(()))
        .addWhen(unknownError)
        .when(kadminPrompt)
          .returning(Right(()))
          // We need to exit because if we don't doOperation will try to match with kadmin prompt causing a timeout
          .exit()
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
    * @group Policy Operations
    */
  def deletePolicy(policy: Policy): Expect[Either[ErrorCase, Unit]] = deletePolicy(policy.name)

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
    * @group Policy Operations
    */
  def withPolicy[R](policy: String)(f: ExpectBlock[Either[ErrorCase, R]] => Unit): Expect[Either[ErrorCase, R]] = {
    doOperation(s"get_policy $policy") { e: FluentExpect[Either[ErrorCase, R]] =>
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
    * @group Policy Operations
    */
  def getPolicy(policy: String): Expect[Either[ErrorCase, Policy]] = withPolicy(policy){ expectBlock: ExpectBlock[Either[ErrorCase, Policy]] =>
    // (?s) inline regex flag for dotall mode. In this mode '.' matches any character, including a line terminator.
    expectBlock.when(("""(?s)Policy: (\w+)
                       |Maximum password life: ([^\n]+)
                       |Minimum password life: ([^\n]+)
                       |Minimum password length: (\d+)
                       |Minimum number of password character classes: (\d+)
                       |Number of old keys kept: (\d+)
                       |Maximum password failures before lockout: (\d+)
                       |Password failure count reset interval: ([^\n]+)
                       |Password lockout duration: ([^\n]+)(.*)(?=\n""".stripMargin + kadminPrompt.pattern + ")").r(
      "policy", "maximumLife", "minimumLife",
      "minimumLength", "minimumCharacterClasses",
      "oldKeysKept", "maximumFailuresBeforeLockout",
      "failureCountResetInterval", "lockoutDuration", "allowedKeySalts"))
      .returning { m =>
        val keysalts = Option(m.group("allowedKeySalts")).flatMap { s =>
          "Allowed key/salt types: (.*)".r.findFirstMatchIn(s)
        } map { m =>
          m.group(1).split(",").flatMap(KeySalt.fromString).toSet
        }

        // TODO: fix this ugly code to something simpler
        val durationsStream = Seq("maximumLife", "minimumLife", "failureCountResetInterval", "lockoutDuration")
          .toStream
          .map { key => (key, parseDuration(m.group(key))) }

        durationsStream.collectFirst {
          case (_, Left(errorCase)) => Left(errorCase)
        } getOrElse {
          val durations: Map[String, FiniteDuration] = durationsStream.collect { case (name, Right(duration)) => (name, duration) }.toMap
          Right(Policy(
            policy, durations("maximumLife"), durations("minimumLife"),
            m.group("minimumLength").toInt, m.group("minimumCharacterClasses").toInt,
            m.group("oldKeysKept").toInt, m.group("maximumFailuresBeforeLockout").toInt,
            durations("failureCountResetInterval"), durations("lockoutDuration"),
            keysalts
          ))
        }
      }
  }

  /**
    * List all policies matching the glob expression.
    *
    * If `expressionGlob` is the empty String all policies will be listed.
    *
    * @param expressionGlob the glob expression to pass to kadmin get_policies.
    * @return an Expect that returns the list of policies.
    * @group Policy Operations
    */
  def listPolicies(expressionGlob: String): Expect[Either[ErrorCase, Seq[String]]] = {
    val command = s"get_policies $expressionGlob"
    doOperation(command) { e: FluentExpect[Either[ErrorCase, Seq[String]]] =>
      e.expect
        .addWhen(insufficientPermission)
        .when(s"${expressionGlob.replace("*", "\\*").replace("?", "\\?")}\n(([^\n]+\n)*)(?=$kadminPrompt)".r)
          .returning(m => Right(m.group(1).split("\n").toSeq))
    }
  }
  //endregion
}
