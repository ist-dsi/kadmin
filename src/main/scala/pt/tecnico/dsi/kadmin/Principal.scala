package pt.tecnico.dsi.kadmin

import scala.concurrent.duration.FiniteDuration

case class Principal(name: String, expirationDateTime: ExpirationDateTime,
                     lastPasswordChange: ExpirationDateTime, passwordExpirationDateTime: ExpirationDateTime,
                     maximumTicketLife: FiniteDuration, maximumRenewableLife: FiniteDuration,
                     lastModified: ExpirationDateTime, lastModifiedBy: String,
                     lastSuccessfulAuthentication: ExpirationDateTime, //
                     lastFailedAuthentication: ExpirationDateTime,
                     failedPasswordAttempts: Int,
                     keys: Set[Key], masterKeyVersionNumber: Int,
                     attributes: Set[String],
                     policy: Option[String])

case class Key(versionNumber: Int, encryptionType: String)