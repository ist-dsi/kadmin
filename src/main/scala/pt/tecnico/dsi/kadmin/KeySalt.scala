package pt.tecnico.dsi.kadmin

import pt.tecnico.dsi.kadmin.Salt._

sealed trait Salt
object Salt {
  case object Normal extends Salt
  case object V4 extends Salt
  case object NoRealm extends Salt
  case object OnlyRealm extends Salt
  case object Afs3 extends Salt
  case object Special extends Salt
}

object KeySalt {
  def fromString(s: String): Option[KeySalt] = {
    "([^:,]+)(, |:)?(.+)?".r.findFirstMatchIn(s).map { m =>
      val salt = Option(m.group(3)).map(_.trim).collect {
        case "Version 5" | "normal" | "" => Normal
        case "Version 4" | "v4" | "no salt" => V4
        case "Version 5 - No Realm" | "norealm" => NoRealm
        case "Version 5 - Realm Only" | "onlyrealm" => OnlyRealm
        case "AFS version 3" | "afs3" => Afs3
        case "Special" | "special" => Special
      }.getOrElse(Normal)
      new KeySalt(m.group(1).trim, salt)
    }
  }
}
case class KeySalt(encryptionType: String, salt: Salt) {
  val toKadminRepresentation: String = s"$encryptionType:${salt.getClass.getSimpleName.toLowerCase}"
}

object Key {
  def fromString(s: String): Option[Key] = {
    """Key: vno (\d+), (.+)""".r.findFirstMatchIn(s).map { m =>
      new Key(m.group(1).toInt, KeySalt.fromString(m.group(2)).get)
    }
  }
}
case class Key(versionNumber: Int, keySalt: KeySalt)