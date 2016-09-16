package pt.tecnico.dsi

import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration

package object kadmin {
  implicit def dateTime2AbsoluteDateTime(dateTime: DateTime): AbsoluteDateTime = AbsoluteDateTime(dateTime)
  implicit def finiteDuration2AbsoluteDateTime(duration: FiniteDuration): AbsoluteDateTime = {
    AbsoluteDateTime(new DateTime(DateTime.now().getMillis + duration.toMillis))
  }
}
