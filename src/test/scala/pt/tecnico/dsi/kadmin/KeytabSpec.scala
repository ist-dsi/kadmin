package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.FlatSpec

/**
  * $assumptions
  */
class KeytabSpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin.settings.keytabsLocation

  "create keytab" should "succeed" in {
    val principal = "createKeytab"

    fullPermissionsKadmin.addPrincipal("-randkey", principal).rightValueShouldBeUnit()

    //This also changes the principal password
    fullPermissionsKadmin.createKeytab(principal).rightValueShouldBeUnit()

    val kadmin = new Kadmin(ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      password-authentication = false
      principal = "$principal"
      command = "kadmin -kt $keytabsLocation/$principal.keytab -p $$FULL_PRINCIPAL"
    }"""))

    //This will test whether the keytab was successfully created
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)
  }
}
