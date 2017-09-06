package pt.tecnico.dsi.kadmin

import java.io.File

import scala.collection.JavaConverters._
import scala.util.matching.Regex

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils.splitBySpaces
import work.martins.simon.expect.{Settings => ScalaExpectSettings}

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

    val keytab = getString("keytab")

    def getCommand(path: String): Seq[String] = {
      getValue(path).valueType() match {
        case ConfigValueType.LIST => getStringList(path).asScala
        case ConfigValueType.STRING => splitBySpaces(getString(path))
        case _ => throw new IllegalArgumentException(s"$path can only be String or Array of Strings.")
      }
    }

    val scalaExpectSettings = {
      val path = "scala-expect"
      lazy val scalaExpectConfig = if (config.hasPath(path)) {
        kadminConfig.withOnlyPath(path).withFallback(config.withOnlyPath(path))
      } else {
        kadminConfig.withOnlyPath(path)
      }
      ScalaExpectSettings.fromConfig(if (kadminConfig.hasPath(path)) scalaExpectConfig else config)
    }

    new Settings(
      getString("realm"),
      getString("principal"),
      keytab,
      getString("password"),
      if (keytab.isEmpty) getCommand("command-password") else getCommand("command-keytab"),
      new File(getString("keytabs-location")),
      getString("prompt").r,
      scalaExpectSettings)
  }

  def defaultCommand(usingKeytab: Boolean): Seq[String] = {
    val withPassword = Seq("kadmin", "-p", "$FULL_PRINCIPAL")
    if (usingKeytab)
      withPassword ++ Seq("-kt", "$KEYTAB")
    else
      withPassword
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
final case class Settings(realm: String, principal: String = "kadmin/admin", keytab: String, password: String,
                    command: Seq[String], keytabsLocation: File = new File("/tmp"),
                    kadminPrompt: Regex = "kadmin(.local)?: ".r, scalaExpectSettings: ScalaExpectSettings = new ScalaExpectSettings()) extends LazyLogging {
  require(realm.nonEmpty, "Realm cannot be empty.")
  require(principal.nonEmpty, "Principal cannot be empty.")
  require(password.nonEmpty || keytab.nonEmpty, "Either password or keytab must be defined.")
  require(command.nonEmpty, "Command cannot be empty.")
  
  // We have to redefine equals because Regex does not implement equals :(
  override def equals(other: Any): Boolean = other match {
    case that: Settings =>
      realm == that.realm &&
        principal == that.principal &&
        keytab == that.keytab &&
        password == that.password &&
        command == that.command &&
        keytabsLocation == that.keytabsLocation &&
        kadminPrompt.regex == that.kadminPrompt.regex &&
        scalaExpectSettings == that.scalaExpectSettings
    case _ => false
  }
}
