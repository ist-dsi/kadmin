package pt.tecnico.dsi.kadmin

sealed trait ErrorCase
case object NoSuchPolicy extends ErrorCase
case object PolicyIsInUse extends ErrorCase
case object NoSuchPrincipal extends ErrorCase
case object PasswordIncorrect extends ErrorCase
case object PasswordTooShort extends ErrorCase
case object PasswordWithoutEnoughCharacterClasses extends ErrorCase
case object PasswordIsBeingReused extends ErrorCase
case object PasswordExpired extends ErrorCase
case class InsufficientPermissions(missingPrivilege: String) extends ErrorCase
case class UnknownError(cause: Option[String] = None) extends ErrorCase