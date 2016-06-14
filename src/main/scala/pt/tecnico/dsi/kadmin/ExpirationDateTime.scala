package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

trait ExpirationDateTime {
  def toKadminRepresentation: String
}

case object Never extends ExpirationDateTime {
  val toKadminRepresentation: String = "never"
}

case class AbsoluteDateTime(_dateTime: DateTime) extends ExpirationDateTime {
  //Expiration dates in kadmin have a resolution to the second
  val dateTime = _dateTime.withMillisOfSecond(0)

  //We cannot include the timezone in the format because kadmin cannot interpret some timezones such as WEST
  val format = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  val toKadminRepresentation: String = format.print(dateTime)
  override def toString = s"AbsoluteDateTime($toKadminRepresentation)"

  override def hashCode(): Int = dateTime.hashCode()
  override def equals(other: Any): Boolean = other match {
    case that: AbsoluteDateTime => dateTime.equals(that.dateTime)
    case _ => false
  }
}