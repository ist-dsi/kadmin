package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

trait ExpirationDateTime {
  def toKadminRepresentation: String
}

case object Never extends ExpirationDateTime {
  val toKadminRepresentation: String = "never"
}

object AbsoluteDateTime {
  def apply(dateTime: DateTime): AbsoluteDateTime = {
    // Expiration dates in kadmin have a resolution to the second
    new AbsoluteDateTime(dateTime.withMillisOfSecond(0))
  }
}
case class AbsoluteDateTime private (dateTime: DateTime) extends ExpirationDateTime {
  override def toKadminRepresentation: String = {
    // Unfortunately when expiring a principal or its password kadmin does not accept the same format
    // as the one in `KadminUtils.parseDateTime`. So we are restricted to using the following:
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(dateTime)
  }
}