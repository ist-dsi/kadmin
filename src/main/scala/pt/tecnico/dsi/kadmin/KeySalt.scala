package pt.tecnico.dsi.kadmin

object KeySalt {
  def fromString(s: String): Option[KeySalt] = {
    "([^:,]+)(, |:)?(.+)?".r.findFirstMatchIn(s).map { m =>
      val salt = Option(m.group(3)).map(_.trim).collect {
        case "Version 5" | "normal" => "normal"
        case "Version 4" | "v4" | "no salt" => "v4"
        case "Version 5 - No Realm" | "norealm" => "norealm"
        case "Version 5 - Realm Only" | "onlyrealm" => "onlyrealm"
        case "AFS version 3" | "afs3" => "afs3"
        case "Special" | "special" => "special"
      }.getOrElse("normal")
      new KeySalt(m.group(1).trim, salt)
    }
  }
}
case class KeySalt(encryptionType: String, saltType: String) {
  override def toString: String = s"$encryptionType:$saltType"
}

object Key {
  def fromString(s: String): Option[Key] = {
    """Key: vno (\d+), (.+)""".r.findFirstMatchIn(s).map { m =>
      new Key(m.group(1).toInt, KeySalt.fromString(m.group(2)).get)
    }
  }
}
case class Key(versionNumber: Int, keySalt: KeySalt)