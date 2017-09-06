package pt.tecnico.dsi.kadmin

import java.nio.charset.Charset

import com.typesafe.config.ConfigFactory
import org.scalatest.AsyncWordSpec
import scala.concurrent.duration.DurationInt


/**
  * $assumptions
  */
class SettingsSpec extends AsyncWordSpec with TestUtils {
  "Settings" when {
    "wrong options are specified" should {
      "throw IllegalArgumentException if realm is empty" in {
        val ex = the [IllegalArgumentException] thrownBy {
          new Settings("", "kadmin/admin", "keytab", "", Settings.defaultCommand(usingKeytab = true))
        }
        ex.getMessage should include("Realm cannot be empty.")
      }

      "throw IllegalArgumentException if principal is empty" in {
        val ex = the [IllegalArgumentException] thrownBy {
          new Settings("EXAMPLE.COM", "", "keytab", "", Settings.defaultCommand(usingKeytab = true))
        }
        ex.getMessage should include("Principal cannot be empty.")
      }

      "throw IllegalArgumentException if neither keytab and password are specified" in {
        val ex = the [IllegalArgumentException] thrownBy {
          new Settings("EXAMPLE.COM", "kadmin/admin", "", "", Settings.defaultCommand(usingKeytab = true))
        }
        ex.getMessage should include("Either password or keytab must be defined.")
      }
      "throw IllegalArgumentException if command is empty" in {
        val ex = the [IllegalArgumentException] thrownBy {
          new Settings("EXAMPLE.COM", "kadmin/admin", "keytab", "", Seq.empty)
        }
        ex.getMessage should include("Command cannot be empty.")
      }
    }

    "a string command is used" should {
      "work correctly" in {
        noException should be thrownBy {
          Settings.fromConfig(ConfigFactory.parseString(
            """kadmin {
              |  realm = "EXAMPLE.COM"
              |  password = "MITiys4K5"
              |  command-password = "kadmin -p $FULLPRINCIPAL"
              |}
            """.stripMargin))
        }
      }
    }

    "an array command is used" should {
      "work correctly" in {
        noException should be thrownBy {
          Settings.fromConfig(ConfigFactory.parseString(
            """kadmin {
              |  realm = "EXAMPLE.COM"
              |  keytab = "somekeytab"
              |  command-keytab = ["kadmin", "-p", "$FULLPRINCIPAL"]
              |}
            """.stripMargin))
        }
      }
    }

    "a double command is used" should {
      "throw IllegalArgumentException" in {
        val ex = the [IllegalArgumentException] thrownBy {
          Settings.fromConfig(ConfigFactory.parseString(
            """kadmin {
              |  realm = "EXAMPLE.COM"
              |  keytab = "somekeytab"
              |  command-keytab = 25.5
              |}
            """.stripMargin))
        }
        ex.getMessage should include("can only be String or Array of Strings")
      }
    }

    "config contains scala-expect settings under kadmin" should {
      "use those settings directly" in {
        Settings.fromConfig(ConfigFactory.parseString(
          """kadmin {
            |  realm = "EXAMPLE.COM"
            |  keytab = "somekeytab"
            |  command = ["kadmin", "-p", "$FULLPRINCIPAL"]
            |  scala-expect {
            |    timeout = 20 seconds
            |  }
            |}
          """.stripMargin)).scalaExpectSettings.timeout shouldBe 20.seconds
      }
    }

    "config contains scala-expect settings at the root" should {
      "use those settings directly" in {
        val expectSettings = Settings.fromConfig(ConfigFactory.parseString(
          """kadmin {
            |  realm = "EXAMPLE.COM"
            |  keytab = "somekeytab"
            |  command = ["kadmin", "-p", "$FULLPRINCIPAL"]
            |}
            |scala-expect {
            |  timeout = 10 seconds
            |}
          """.stripMargin)).scalaExpectSettings
        expectSettings.timeout shouldBe 10.seconds
      }
    }

    "config contains scala-expect settings under kadmin and at the root" should {
      "use both settings but those defined under kadmin take precendence" in {
        val expectSettings = Settings.fromConfig(ConfigFactory.parseString(
          """kadmin {
            |  realm = "EXAMPLE.COM"
            |  keytab = "somekeytab"
            |  command = ["kadmin", "-p", "$FULLPRINCIPAL"]
            |  scala-expect {
            |    timeout = 20 seconds
            |  }
            |}
            |scala-expect {
            |  charset = "UTF-16"
            |  timeout = 10 seconds
            |}
          """.stripMargin)).scalaExpectSettings
        expectSettings.timeout shouldBe 20.seconds
        expectSettings.charset shouldBe Charset.forName("UTF-16")
      }
    }
    
    "created via config and via the domain class" should {
      "have the same default values when using keytab" in {
        val realm = "TEST.COM"
        val keytab = "a keytab"
        val password = ""
        val kadminConfig = ConfigFactory.parseString(
          s"""kadmin {
            |  realm = "$realm"
            |  keytab = "$keytab"
            |  password = "$password"
            |}
          """.stripMargin)
      
        val configSettings = Settings.fromConfig(kadminConfig)
        val domainClassSettings = Settings(realm = realm, keytab = keytab, password = password,
          command = Settings.defaultCommand(usingKeytab = true))
        
        configSettings shouldBe domainClassSettings
      }
      "have the same default values when using passsword" in {
        val realm = "TEST.COM"
        val keytab = ""
        val password = "a password"
        val kadminConfig = ConfigFactory.parseString(
          s"""kadmin {
             |  realm = "$realm"
             |  keytab = "$keytab"
             |  password = "$password"
             |}
          """.stripMargin)
    
        val configSettings = Settings.fromConfig(kadminConfig)
        val domainClassSettings = Settings(realm = realm, keytab = keytab, password = password,
          command = Settings.defaultCommand(usingKeytab = false))
    
        configSettings shouldBe domainClassSettings
      }
    }
  }
}
