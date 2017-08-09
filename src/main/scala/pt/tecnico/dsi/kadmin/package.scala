package pt.tecnico.dsi

import org.joda.time.DateTime

package object kadmin {
  implicit def dateTime2AbsoluteDateTime(dateTime: DateTime): AbsoluteDateTime = AbsoluteDateTime(dateTime)
}
