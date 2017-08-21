package pt.tecnico.dsi.kadmin

import scala.concurrent.duration.Duration

case class Policy(name: String, maximumLife: Duration, minimumLife: Duration,
                  minimumLength: Int, minimumCharacterClasses: Int,
                  oldKeysKept: Int, maximumFailuresBeforeLockout: Int = 0,
                  failureCountResetInterval: Duration = Duration.Zero, lockoutDuration: Duration = Duration.Zero,
                  allowedKeysalts: Option[Set[KeySalt]] = None) {
  val options: String = s"""-maxlife "${maximumLife.toSeconds} seconds"""" +
    s""" -minlife "${minimumLife.toSeconds} seconds"""" +
    s" -minlength $minimumLength" +
    s" -minclasses $minimumCharacterClasses" +
    s" -history $oldKeysKept" +
    s" -maxfailure $maximumFailuresBeforeLockout" +
    (if (failureCountResetInterval == Duration.Zero) "" else s""" -failurecountinterval "${failureCountResetInterval.toSeconds} seconds"""") +
    s""" -lockoutduration "${lockoutDuration.toSeconds} seconds"""" +
    allowedKeysalts.map(salts => s" -allowedkeysalts ${salts.mkString(",")}").getOrElse("")
}
