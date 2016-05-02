package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.FlatSpec

/**
  * $assumptions
  */
class GetTGTSpec extends FlatSpec with TestUtils {
  val kadmin = new Kadmin(ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      password-authentication = false
      command = "kadmin -c /tmp/krb5cc_0 -p $$FULL_PRINCIPAL"
    }"""))

  "obtainTicketGrantingTicket" should "succeed" in {
    import fullPermissionsKadmin.settings.{authenticatingPrincipal, authenticatingPrincipalPassword}

    KadminUtils.obtainTicketGrantingTicket("-S kadmin/admin", authenticatingPrincipal,
      authenticatingPrincipalPassword).rightValue shouldBe a [DateTime]

    //This will test whether the keytab was successfully created
    kadmin.getPrincipal(authenticatingPrincipal).rightValue.name should startWith (authenticatingPrincipal)
  }
}
