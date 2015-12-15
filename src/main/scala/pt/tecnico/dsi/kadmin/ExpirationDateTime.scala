package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import squants.time.Time

trait ExpirationDateTime {
  protected val format = DateTimeFormat.forPattern("\"yyyy-MM-dd HH:mm:ss zzz\"")
  val toKadminRepresentation: String
}

object Now {
  def apply = new Now
}
class Now extends ExpirationDateTime {
  private val dateTime = DateTime.now()
  //By using this instead of "now" we make the operations that use Now idempotent.
  val toKadminRepresentation: String  = format.print(dateTime)
  val toAbsolute = new AbsoluteDateTime(dateTime)
}

object Never extends ExpirationDateTime {
  val toKadminRepresentation: String = "never"
}

class RelativeDateTime(duration: Time) extends ExpirationDateTime {
  private val dateTime = new DateTime(DateTime.now().getMillis + duration.millis)
  val toKadminRepresentation: String  = format.print(dateTime)
  val toAbsolute = new AbsoluteDateTime(dateTime)
}

class AbsoluteDateTime(dateTime: DateTime) extends ExpirationDateTime {
  val toKadminRepresentation: String  = format.print(dateTime)
}