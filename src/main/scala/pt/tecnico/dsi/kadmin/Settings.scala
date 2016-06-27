package pt.tecnico.dsi.kadmin

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import work.martins.simon.expect.StringUtils.{splitBySpaces, escape, IndentableString}
import work.martins.simon.expect.{Settings => ScalaExpectSettings}

import scala.collection.JavaConverters._

/**
  * This class holds all the settings that parameterize kadmin.
  *
  * By default these settings are read from the Config obtained with `ConfigFactory.load()`.
  *
  * You can change the settings in multiple ways:
  *
  *  - Change them in the default configuration file (e.g. application.conf)
  *  - Pass a different config holding your configurations: {{{
  *       new Settings(yourConfig)
  *     }}}
  *     However it will be more succinct to pass your config directly to Kadmin: {{{
  *       new Kadmin(yourConfig)
  *     }}}
  *  - Extend this class overriding the settings you want to redefine {{{
  *      object YourSettings extends Settings() {
  *        override val realm: String = "YOUR.DOMAIN.TLD"
  *        override val keytabsLocation: String = "/var/local/keytabs"
  *        override val commandWithAuthentication: String = s"""ssh user@server:port "kadmin -p \$authenticatingPrincipal""""
  *      }
  *      new Kadmin(YourSettings)
  *    }}}
  *
  * @param config
  */
class Settings(config: Config = ConfigFactory.load()) {
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

  val password = getString("password")
  val keytab = getString("keytab")
  require(password.nonEmpty || keytab.nonEmpty, "Either password or keytab must be defined.")

  val passwordAuthentication = keytab.isEmpty

  def getCommand(path: String): Seq[String] = {
    val commandArray: Seq[String] = getValue(path).valueType() match {
      case ConfigValueType.STRING => splitBySpaces(getString(path))
      case ConfigValueType.LIST => getStringList(path).asScala
      case _ => throw new IllegalArgumentException(s"$path can only be String or Array of String")
    }
    require(commandArray.nonEmpty, s"$path cannot be empty.")
    commandArray
      .map(_.replaceAllLiterally("$FULL_PRINCIPAL", s"$principal@$realm"))
      .map(_.replaceAllLiterally("$KEYTAB", s"$keytab"))
  }
  val commandKeytab = getCommand("command-keytab")
  val commandPassword = getCommand("command-password")

  val command = if (passwordAuthentication) commandPassword else commandKeytab

  val keytabsLocation = getString("keytabs-location")

  val kadminPrompt = getString("prompt").r

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

  override def equals(other: Any): Boolean = other match {
    case that: Settings =>
      realm == that.realm &&
      principal == that.principal &&
      password == that.password &&
      keytab == that.keytab &&
      commandKeytab == that.commandKeytab &&
      commandPassword == that.commandPassword &&
      keytabsLocation == that.keytabsLocation &&
      kadminPrompt == that.kadminPrompt &&
      scalaExpectSettings == that.scalaExpectSettings
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(realm, principal, password, keytab, commandKeytab, commandPassword,
      keytabsLocation, kadminPrompt, scalaExpectSettings)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
  override def toString: String =
    s"""Kadmin Settings:
       |\tRealm: $realm
       |
       |\tPrincipal: $principal
       |
       |\tPassword: $password
       |\tKeytab: $keytab
       |
       |${if (passwordAuthentication) "Password" else "Keytab"} authentication will be performed using command:
       |\t$command
       |
       |\tCommand keytab: $commandKeytab
       |\tCommand password: $commandPassword
       |
       |\tKeytabs location: $keytabsLocation
       |
       |\tPrompt: ${escape(kadminPrompt.regex)}
       |
       |${scalaExpectSettings.toString.indent()}
     """.stripMargin
}

