package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec

/**
  * $assumptions
  */
class TicketsSpec extends FlatSpec with TestUtils {
  import fullPermissionsKadmin.settings.{principal, password, realm}
  val kadmin = new Kadmin(ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      password-authentication = false
      principal = "$principal"
      command = "kadmin -c /tmp/krb5cc_0 -p $$FULL_PRINCIPAL"
    }"""))

  "obtainTicketGrantingTicket with password" should "succeed" in {
    //obtain a TGT. We pass the -S flag so we can later use kadmin with the obtained ticket
    KadminUtils.obtainTGT("-S kadmin/admin", principal, password).rightValueShouldBeUnit()

    //Try to access kadmin using the credencial cache created when obtaining the TGT
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)
  }

  "obtainTicketGrantingTicket with keytab" should "succeed" in {
    //Create the keytab

    //Obtain a TGT. We pass the -S flag so we can later use kadmin with the obtained ticket
    KadminUtils.obtainTGT("-S kadmin/admin", principal, password).rightValueShouldBeUnit()

    //Then we try to access kadmin using the credencial cache created when obtaining the TGT
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)
  }

  "listTickets" should "succeed" in {
    //Obtain a TGT
    KadminUtils.obtainTGT(principal = principal, password = password).rightValueShouldBeUnit()

    val (p, tickets) = KadminUtils.listTickets().rightValue
    p shouldBe s"$principal@$realm"
    tickets.exists(_.servicePrincipal == s"krbtgt/$realm@$realm") shouldBe true
  }

  "destroyTickets" should "succeed" in {
    //Ensure we have the ticket and it is working
    KadminUtils.obtainTGT("-S kadmin/admin", principal, password).rightValueShouldBeUnit()
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)

    KadminUtils.destroyTickets().rightValueShouldBeUnit()
    kadmin.getPrincipal(principal).leftValue shouldBe a [UnknownError]
  }
}
