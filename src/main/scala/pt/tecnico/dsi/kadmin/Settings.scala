package pt.tecnico.dsi.kadmin

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import work.martins.simon.expect.StringUtils.splitBySpaces
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

  val performAuthentication = getBoolean("perform-authentication")

  val authenticatingPrincipal = getString("authenticating-principal")
  if (performAuthentication && authenticatingPrincipal.isEmpty)
    throw new IllegalArgumentException("When performing authentication authenticating-principal cannot be empty.")
  val authenticatingPrincipalPassword = getString("authenticating-principal-password")
  if (performAuthentication && authenticatingPrincipalPassword.isEmpty)
    throw new IllegalArgumentException("When performing authentication authenticating-principal-password cannot be empty.")

  val commandWithAuthentication: Seq[String] = {
    val configName = "command-with-authentication"
    val commandArray: Seq[String] = getValue(configName).valueType() match {
      case ConfigValueType.STRING => splitBySpaces(getString(configName))
      case ConfigValueType.LIST => getStringList(configName).asScala
      case _ => throw new IllegalArgumentException(s"$configName can only be String or Array of String")
    }
    require(commandArray.nonEmpty, s"$configName cannot be empty.")
    commandArray.map(_.replaceAllLiterally("$FULL_PRINCIPAL", s"$authenticatingPrincipal@$realm"))
  }

  val commandWithoutAuthentication: Seq[String] = {
    val configName = "command-without-authentication"
    val commandArray: Seq[String] = getValue(configName).valueType() match {
      case ConfigValueType.STRING => splitBySpaces(getString(configName))
      case ConfigValueType.LIST => getStringList(configName).asScala
      case _ => throw new IllegalArgumentException(s"$configName can only be String or Array of String")
    }
    require(commandArray.nonEmpty, s"$configName cannot be empty.")
    commandArray
  }

  val keytabsLocation = getString("keytabs-location")

  val kadminPrompt = getString("prompt").r

  override def toString: String = kadminConfig.root.render
}

