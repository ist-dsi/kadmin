package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.FlatSpec

/**
  * $assumptions
  */
class KeytabSpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin._
  import settings.keytabsLocation

  "obtain keytab" should "return KeytabDoesNotExist if there is no keytab" in {
    val principal = "obtainKeytab"

    deletePrincipal(principal).rightValueShouldBeUnit()

    obtainKeytab(principal).left.value shouldBe KeytabDoesNotExist
  }
  it should "succeed if a keytab exists" in {
    val principal = "obtainKeytab"

    addPrincipal("-randkey", principal).rightValueShouldBeUnit()

    createKeytab("", principal).rightValueShouldBeUnit()

    obtainKeytab(principal).right.value.length should be > 0
  }

  "create keytab" should "succeed" in {
    val principal = "createKeytab"

    addPrincipal("-randkey", principal).rightValueShouldBeUnit()

    //This also changes the principal password
    createKeytab("", principal).rightValueShouldBeUnit()

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
