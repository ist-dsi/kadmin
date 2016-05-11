package pt.tecnico.dsi.kadmin

import org.joda.time.DateTime

case class Ticket(validStarting: DateTime, expires: DateTime, servicePrincipal: String, renewUtil: Option[DateTime])
