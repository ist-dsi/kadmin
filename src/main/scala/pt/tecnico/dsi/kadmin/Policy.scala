package pt.tecnico.dsi.kadmin

import scala.concurrent.duration.Duration

case class Policy(name: String, maximumLife: Duration, minimumLife: Duration,
                  minimumLength: Int, minimumCharacterClasses: Int,
                  oldKeysKept: Int, maximumFailuresBeforeLockout: Int,
                  failureCountResetInterval: Duration, lockoutDuration: Duration,
                  allowedKeysalts: Option[Set[KeySalt]])
