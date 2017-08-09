package pt.tecnico.dsi.kadmin

sealed trait ErrorCase
case object NoSuchPolicy extends ErrorCase
case object NoSuchPrincipal extends ErrorCase
case object PasswordIncorrect extends ErrorCase
case object PasswordTooShort extends ErrorCase
case object PasswordWithoutEnoughCharacterClasses extends ErrorCase
case object PasswordIsBeingReused extends ErrorCase
case object PasswordExpired extends ErrorCase
case object KeytabDoesNotExist extends ErrorCase
case object KeytabIsNotReadable extends ErrorCase

case class InsufficientPermissions(missingPrivilege: String) extends ErrorCase

object UnknownError {
  def apply(message: String): UnknownError = new UnknownError(Some(new Exception(message)))
}
case class UnknownError(cause: Option[Throwable] = None) extends ErrorCase