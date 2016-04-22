package pt.tecnico.dsi.kadmin

import java.util.Locale

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import work.martins.simon.expect.fluent.{ExpectBlock, When}

import scala.concurrent.duration.{Duration, FiniteDuration, DurationInt}
import scala.util.matching.Regex.Match
import scala.util.{Failure, Success, Try}

object KadminUtils extends LazyLogging {
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
    } match {
      case Success(datetime) => Right(datetime)
      case Failure(e) => Left(UnknownError(Some(e)))
    }
  }

  def parseDuration(durationString: String): FiniteDuration = {
    """(\d+) days? (\d+):(\d+):(\d+)""".r
      .findFirstMatchIn(durationString)
      .map { m =>
        m.group(1).toInt.days + m.group(2).toInt.days + m.group(3).toInt.days + m.group(4).toInt.days
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
