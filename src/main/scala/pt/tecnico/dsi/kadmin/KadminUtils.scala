package pt.tecnico.dsi.kadmin

import java.io.File
import java.util.Locale

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import work.martins.simon.expect.EndOfFile
import work.martins.simon.expect.fluent.{Expect, ExpectBlock, When}

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.matching.Regex.Match
import scala.util.{Failure, Success, Try}

object KadminUtils extends LazyLogging {
  /**
    * Obtains a ticket granting ticket for `principal` using `password`.
    *
    * @param options options to pass to the `kinit` command.
    * @return Either an ErrorCase or Unit if the operation was successful.
    */
  def obtainTGT(options: String = "", principal: String, password: String): Expect[Either[ErrorCase, Unit]] = {
    require(principal.nonEmpty, "principal cannot be empty.")
    require(password.nonEmpty, "password cannot be empty.")

    val defaultValue: Either[ErrorCase, Unit] = Left(UnknownError())
    val e = new Expect(s"kinit $options $principal", defaultValue)
    e.expect
      .when(s"Password for $principal")
        .sendln(password)
      .when(s"""Client '[^']+' not found in Kerberos database""".r)
        .returning(Left(NoSuchPrincipal))
    e.expect
      .addWhen(passwordIncorrect)
      .addWhen(passwordExpired)
      .when(EndOfFile)
        .returning(Right(()))
    e
  }

  /**
    * Obtains a ticket granting ticket using 'keytabFile'.
    *
    * @param options options to pass to the `kinit` command.
    * @return Either an ErrorCase or Unit if the operation was successful.
    */
  def obtainTGTWithKeytab(options: String = "", keytabFile: File): Expect[Either[ErrorCase, Unit]] = {
    val defaultValue: Either[ErrorCase, Unit] = Left(UnknownError())
    val e = new Expect(s"kinit -kt ${keytabFile.getAbsolutePath} $options", defaultValue)
    e.expect
      .when(s"""Client '[^']+' not found in Kerberos database""".r)
        .returning(Left(NoSuchPrincipal))
    e.expect
      .when(EndOfFile)
        .returning(Right(()))
    e
  }

  /**
    * Lists cached Kerberos tickets
    *
    * @param options options to pass to the `klist` command.
    * @return The default principal and the list of all the cached tickets
    */
  def listTickets(options: String = ""): Expect[Either[ErrorCase, (String, Seq[Ticket])]] = {
    val defaultValue: Either[ErrorCase, (String, Seq[Ticket])] = Left(UnknownError())
    val datetimeRegex = """\d\d/\d\d/\d\d \d\d:\d\d:\d\d"""
    val ticketRegex = s"""(?s)($datetimeRegex)\\s+($datetimeRegex)\\s+([^\n]+)(\\s+renew until $datetimeRegex)?"""
      .stripMargin.r("validStarting", "expires", "servicePrincipal", "renewUtil")
    val dateTimeFormat = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss")

    val e = new Expect(s"klist $options", defaultValue)
    e.expect(
      s"""Ticket cache: FILE:[^\n]+
          |Default principal: ([^\n]+)
          |
          |Valid starting\\s+Expires\\s+Service principal
          |(.+?)$$""".stripMargin.r)
      .returning { m: Match =>
        val principal = m.group(1)
        val tickets = ticketRegex.findAllMatchIn(m.group(2)).map { m =>
          Ticket(
            dateTimeFormat.parseDateTime(m.group("validStarting")),
            dateTimeFormat.parseDateTime(m.group("expires")),
            m.group("servicePrincipal"),
            Option(m.group("renewUtil")).map(dateTimeFormat.parseDateTime)
          )
        }.toSeq

        Right((principal, tickets))
      }
    e
  }

  /**
    * Destroys the default credencials cache.
    *
    * @param options options to pass to the `kdestroy` command.
    * @return
    */
  def destroyTickets(options: String = ""): Expect[Either[ErrorCase, Unit]] = {
    val defaultValue: Either[ErrorCase, Unit] = Left(UnknownError())
    val e = new Expect(s"kdestroy", defaultValue)
    e.expect
      .when(EndOfFile)
        .returning(Right(()))
      /*.when("(?s)(.+?)".r)
        .returning { m =>
          Left(UnknownError(Some(new Exception(m.group(1)))))
        }*/
    e
  }


  /**
    * Tries to parse a date time string returned by a kadmin `get_principal` operation.
    *
    * The string must be in the format `"EEE MMM dd HH:mm:ss zzz yyyy"`, see
    * [[http://www.joda.org/joda-time/apidocs/org/joda/time/format/DateTimeFormat.html Joda Time DateTimeFormat]]
    * for an explanation of the format.
    *
    * If the string is either `"[never]"` or `"[none]"` the `Never` object will be returned.
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
  def parseDateTime(dateTimeString: => String): Either[ErrorCase, ExpirationDateTime] = {
    Try {
      dateTimeString.trim match {
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
            logger.info(s"Unknown timezone: $timezone. Using the default one: $default.")
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
    } match {
      case Success(datetime) => Right(datetime)
      case Failure(e) => Left(UnknownError(Some(e)))
    }
  }

  /**
    * Parses `durationString` into a FiniteDuration.
    *
    * The expected format is "d days? HH:mm:ss".
    *
    * @param durationString the string to parse.
    * @return the parsed FiniteDuration or Duration.Zero if an error occurred.
    */
  def parseDuration(durationString: String): FiniteDuration = {
    """(\d+) days? (\d+):(\d+):(\d+)""".r
      .findFirstMatchIn(durationString)
      .map { m =>
        m.group(1).toInt.days + m.group(2).toInt.hours + m.group(3).toInt.minutes + m.group(4).toInt.seconds
      }
      .getOrElse(Duration.Zero)
  }

  def insufficientPermission[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("""Operation requires ``([^']+)'' privilege""".r)
      .returning { m: Match =>
        Left(InsufficientPermissions(m.group(1)))
      }
  }
  def principalDoesNotExist[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Principal does not exist")
      .returning(Left(NoSuchPrincipal))
  }
  def policyDoesNotExist[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Policy does not exist")
      .returning(Left(NoSuchPolicy))
  }
  def passwordIncorrect[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Password incorrect")
      .returning(Left(PasswordIncorrect))
  }
  def passwordExpired[R](expectBlock: ExpectBlock[Either[ErrorCase, R]]) = {
    expectBlock.when("Password expired")
      .returning(Left(PasswordExpired))
  }

  def preemptiveExit[R](when: When[Either[ErrorCase, R]]): Unit = {
    when
      //We send the quit to allow kadmin a graceful shutdown
      .sendln("quit")
      //This ensures the next expect(s) (if any) do not get executed and
      //we don't end up returning something else by mistake.
      .exit()
  }
}
