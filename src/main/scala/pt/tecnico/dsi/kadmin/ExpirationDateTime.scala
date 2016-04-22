package pt.tecnico.dsi.kadmin

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.duration.FiniteDuration

trait ExpirationDateTime extends Equals {
  protected def dateTime: DateTime

  //We cannot include the timezone in the format because kadmin cannot interpret some timezones such as WEST
  protected val format = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  def toKadminRepresentation: String = format.print(dateTime)

  override def equals(other: Any): Boolean = other match {
    case that: ExpirationDateTime => (that canEqual this) &&
      //Expiration dates in kadmin have a resolution to the second, so we zero the millis
      //to give the comparison a change to succeed.
      dateTime.withMillisOfSecond(0).equals(that.dateTime.withMillisOfSecond(0))
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(dateTime)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

case class Now() extends ExpirationDateTime {
  protected val dateTime = DateTime.now()
  val toAbsolute = new AbsoluteDateTime(dateTime)

  //By NOT overriding the toKadminRepresentation method its value will be the dateTime.
  //We could have override it and used "now" instead, but by not using it we make the operations that use Now idempotent.

  override def toString = s"Now($toKadminRepresentation)"

  def canEqual(that: Any): Boolean = that.isInstanceOf[Now]
  //We want to ignore the dateTime in the comparison
  override def hashCode(): Int = toString.hashCode
  override def equals(other: Any): Boolean = canEqual(other)
}

object Never extends ExpirationDateTime {
  protected def dateTime = throw new IllegalArgumentException("Never has no dateTime")
  override val toKadminRepresentation: String = "never"
  override def toString = s"Never()"

  def canEqual(other: Any): Boolean = other.isInstanceOf[Never.type]
  override def hashCode(): Int = toString.hashCode
  //We want to ignore the dateTime in the comparison
  override def equals(other: Any): Boolean = canEqual(other)
}

class RelativeDateTime(duration: FiniteDuration) extends ExpirationDateTime {
  protected val dateTime = new DateTime(DateTime.now().getMillis + duration.toMillis)
  val toAbsolute = new AbsoluteDateTime(dateTime)

  def canEqual(other: Any): Boolean = other.isInstanceOf[RelativeDateTime]
  override def toString = s"RelativeDateTime($toKadminRepresentation)"
}

class AbsoluteDateTime(val dateTime: DateTime) extends ExpirationDateTime with LazyLogging {
  def canEqual(other: Any): Boolean = other.isInstanceOf[AbsoluteDateTime]
  override def toString = s"AbsoluteDateTime($toKadminRepresentation)"
}