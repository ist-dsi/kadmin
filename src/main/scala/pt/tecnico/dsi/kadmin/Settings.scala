package pt.tecnico.dsi.kadmin

import java.io.File

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import work.martins.simon.expect.StringUtils.splitBySpaces
import work.martins.simon.expect.{Settings => ScalaExpectSettings}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object Settings {
  /**
    * Instantiate a `Settings` from a `Config`.
    * @param config The `Config` from which to parse the settings.
    */
  def fromConfig(config: Config = ConfigFactory.load()): Settings = {
    val kadminConfig: Config = {
      val reference = ConfigFactory.defaultReference()
      val finalConfig = config.withFallback(reference)
      finalConfig.checkValid(reference, "kadmin")
      finalConfig.getConfig("kadmin")
    }
    import kadminConfig._

    val realm = getString("realm")
    require(realm.nonEmpty, "Realm cannot be empty.")

    val principal = getString("principal")
    require(principal.nonEmpty, "Principal cannot be empty.")

    val keytab = getString("keytab")
    val password = getString("password")
    require(password.nonEmpty || keytab.nonEmpty, "Either password or keytab must be defined.")

    def getCommand(path: String): Seq[String] = {
      val commandArray: Seq[String] = getValue(path).valueType() match {
        case ConfigValueType.STRING => splitBySpaces(getString(path))
        case ConfigValueType.LIST => getStringList(path).asScala
        case _ => throw new IllegalArgumentException(s"$path can only be String or Array of String")
      }
      require(commandArray.nonEmpty, s"Command cannot be empty.")
      commandArray
        .map(_.replaceAllLiterally("$FULL_PRINCIPAL", s"$principal@$realm"))
        .map(_.replaceAllLiterally("$KEYTAB", s"$keytab"))
    }

    val scalaExpectSettings = {
      val path = "scala-expect"
      if (kadminConfig.hasPath(path)) {
        val c = if (config.hasPath(path)) {
          kadminConfig.getConfig(path).withFallback(config.getConfig(path))
        } else {
          kadminConfig.getConfig(path)
        }
        new ScalaExpectSettings(c.atPath(path))
      } else if (config.hasPath(path)) {
        new ScalaExpectSettings(config.getConfig(path).atPath(path))
      } else {
        new ScalaExpectSettings()
      }
    }

    new Settings(realm, principal, keytab, password,
      if (keytab.isEmpty) getCommand("command-password") else getCommand("command-keytab"),
      new File(getString("keytabs-location")), getString("prompt").r, scalaExpectSettings)
  }

  def withPassword(realm: String, principal: String, password: String): Settings = {
    new Settings(realm, principal, "", password, command = Seq(s"kadmin -p $principal@$realm"))
  }
  def withKeytab(realm: String, principal: String, keytab: String): Settings = {
    new Settings(realm, principal, keytab, "", command = Seq(s"kadmin -p $principal@$realm -kt $keytab"))
  }
}
/**
  * This class holds all the settings that parameterize Kadmin.
  *
  * If you would like to create an instance of settings from a typesafe config invoke `Settings.fromConfig`.
  * The `Kadmin` class facilitates this by receiving the `Config` directly in an auxiliary constructor.
  *
  * @param realm
  * @param principal
  * @param keytab
  * @param password
  * @param command
  * @param keytabsLocation
  * @param kadminPrompt
  * @param scalaExpectSettings
  */
case class Settings(realm: String, principal: String = "kadmin/admin", keytab: String, password: String,
                    command: Seq[String], keytabsLocation: File = new File("/tmp"),
                    kadminPrompt: Regex = "kadmin(.local)?: ".r, scalaExpectSettings: ScalaExpectSettings = new ScalaExpectSettings()) {
  require(realm.nonEmpty, "Realm cannot be empty.")
  require(principal.nonEmpty, "Principal cannot be empty.")
  require(password.nonEmpty || keytab.nonEmpty, "Either password or keytab must be defined.")
  require(command.nonEmpty, s"Command cannot be empty.")
}
