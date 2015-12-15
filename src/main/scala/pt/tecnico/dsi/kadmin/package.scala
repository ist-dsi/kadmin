package pt.tecnico.dsi

import org.joda.time.DateTime
import squants.time.Time

import scala.concurrent.duration.FiniteDuration

package object kadmin {
  implicit def dateTime2AbsoluteDateTime(dateTime: DateTime): AbsoluteDateTime = new AbsoluteDateTime(dateTime)
  implicit def finiteDuration2RelativeDateTime(duration: Time): RelativeDateTime = new RelativeDateTime(duration)
}
