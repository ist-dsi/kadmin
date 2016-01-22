package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import squants.time.Time

trait ExpirationDateTime {
  protected val format = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss zzz")
  val toKadminRepresentation: String
}

case class Now() extends ExpirationDateTime {
  private val dateTime = DateTime.now()
  //By using this instead of "now" we make the operations that use Now idempotent.
  val toKadminRepresentation: String  = format.print(dateTime)
  val toAbsolute = new AbsoluteDateTime(dateTime)
  override def toString = s"Now($toKadminRepresentation)"
}

object Never extends ExpirationDateTime {
  val toKadminRepresentation: String = "never"
  override def toString = s"Never()"
}

class RelativeDateTime(duration: Time) extends ExpirationDateTime {
  private val dateTime = new DateTime(DateTime.now().getMillis + duration.millis)
  val toKadminRepresentation: String  = format.print(dateTime)
  val toAbsolute = new AbsoluteDateTime(dateTime)

  def canEqual(other: Any): Boolean = other.isInstanceOf[RelativeDateTime]
  override def equals(other: Any): Boolean = other match {
    case that: RelativeDateTime => (that canEqual this) && dateTime == that.dateTime
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(dateTime)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
  override def toString = s"RelativeDateTime($toKadminRepresentation)"
}

class AbsoluteDateTime(protected val dateTime: DateTime) extends ExpirationDateTime {
  val toKadminRepresentation: String  = format.print(dateTime)

  def canEqual(other: Any): Boolean = other.isInstanceOf[AbsoluteDateTime]
  override def equals(other: Any): Boolean = other match {
    case that: AbsoluteDateTime => (that canEqual this) && dateTime.equals(that.dateTime)
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(dateTime)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
  override def toString = s"AbsoluteDateTime($toKadminRepresentation)"
}