package pt.tecnico.dsi.kadmin

import com.typesafe.config.{ConfigFactory, Config}

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

  val commandWithAuthentication = getString("command-with-authentication")
    .replaceAllLiterally("$FULL_PRINCIPAL", s"$authenticatingPrincipal@$realm")
  require(commandWithAuthentication.nonEmpty, "command-with-authentication cannot be empty.")

  val commandWithoutAuthentication = getString("command-without-authentication")
  require(commandWithoutAuthentication.nonEmpty, "command-without-authentication cannot be empty.")

  val keytabsLocation = getString("keytabs-location")

  val kadminPrompt = getString("prompt").r

  override def toString: String = kadminConfig.root.render
}

