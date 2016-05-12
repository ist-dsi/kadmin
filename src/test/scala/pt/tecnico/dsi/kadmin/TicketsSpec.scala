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
      command = "kadmin -c /tmp/krb5cc_0"
    }"""))

  "obtainTicketGrantingTicket with password" should "succeed" in {
    //obtain a TGT. We pass the -S flag so we can later use kadmin with the obtained ticket
    KadminUtils.obtainTGT(s"-S $principal", principal, password).rightValueShouldBeUnit()

    //Try to access kadmin using the credencial cache created when obtaining the TGT
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)
  }

  "obtainTicketGrantingTicket with keytab" should "succeed" in {
    //Create the keytab
    val principalWithKeytab = "test/admin"
    fullPermissionsKadmin.addPrincipal("-randkey", principalWithKeytab).rightValueShouldBeUnit()
    fullPermissionsKadmin.createKeytab("", principalWithKeytab).rightValueShouldBeUnit()

    val keytabFile = fullPermissionsKadmin.getKeytabFile(principalWithKeytab)

    //Obtain a TGT. We pass the -S flag so we can later use kadmin with the obtained ticket
    KadminUtils.obtainTGTWithKeytab(s"-S $principal", keytabFile, principalWithKeytab).rightValueShouldBeUnit()


    val kadminWithKeytab = new Kadmin(ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      password-authentication = false
      principal = "$principalWithKeytab"
      command = "kadmin -kt ${keytabFile.getAbsolutePath} -p $$FULL_PRINCIPAL"
    }"""))

    //Then we try to access kadmin using the credencial cache created when obtaining the TGT
    kadminWithKeytab.getPrincipal(principalWithKeytab).rightValue.name should startWith (principalWithKeytab)
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
    KadminUtils.obtainTGT(s"-S $principal", principal, password).rightValueShouldBeUnit()
    kadmin.getPrincipal(principal).rightValue.name should startWith (principal)

    KadminUtils.destroyTickets().rightValueShouldBeUnit()
    kadmin.getPrincipal(principal).leftValue shouldBe a [UnknownError]
  }
}
