package pt.tecnico.dsi.kadmin

import java.io.File

import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}

/**
  * $assumptions
  */
class TicketsSpec extends AsyncFlatSpec with TestUtils with BeforeAndAfterEach {
  import fullPermissionsKadmin.settings.{password, principal, realm}

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    //TODO: await result
    KadminUtils.destroyTickets().run()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    //TODO: await result
    KadminUtils.destroyTickets().run()
  }

  // Keytab was a wierd value to ensure authentication is performed using keytab and not password
  val kadmin = new Kadmin(realm, principal, keytab = new File("notEmpty"), command = "kadmin -c /tmp/krb5cc_0")

  "obtainTicketGrantingTicket" should "throw IllegalArgumentException if neither password or keytab is specified" in {
    assertThrows[IllegalArgumentException]{
      KadminUtils.obtainTGT("", principal)
    }
  }
  it should "throw IllegalArgumentException if both password and keytab are specified" in {
    assertThrows[IllegalArgumentException]{
      KadminUtils.obtainTGT("", principal, password = Some(password), keytab = Some(new File(".")))
    }
  }
  it should "throw IllegalArgumentException principal is empty" in {
    assertThrows[IllegalArgumentException]{
      KadminUtils.obtainTGT("", "", password = Some(password))
    }
  }
  "obtainTicketGrantingTicket" should "return NoSuchPrincipal if the principal does not exist" in {
    KadminUtils.obtainTGT("", "nonExistingPrincipal", password = Some(password)) leftValueShouldBe NoSuchPrincipal
  }
  it should "succeed with password" in {
    for {
      // obtain a TGT. We pass the -S flag so we can later use kadmin with the obtained ticket
      _ <- KadminUtils.obtainTGT(s"-S $principal", principal, password = Some(password)).rightValueShouldBeUnit()
  
      // Try to access kadmin using the credencial cache created when obtaining the TGT
      resultingFuture <- kadmin.getPrincipal(principal) rightValue (_.name should startWith(principal))
    } yield resultingFuture
  }
  it should "succeed with keytab" in {
    val principalWithKeytab = "test/admin"
    val keytabFile = fullPermissionsKadmin.getKeytabFile(principalWithKeytab)
    val kadminWithKeytab = new Kadmin(realm, principalWithKeytab, keytabFile)

    for {
      // Create the keytab
      _ <- fullPermissionsKadmin.addPrincipal("", principalWithKeytab, randKey = true).rightValueShouldBeUnit()
      _ <- fullPermissionsKadmin.createKeytab("", principalWithKeytab).rightValueShouldBeUnit()
  
      // Obtain a TGT using the keytab. We pass the -S flag so we can later use kadmin with the obtained ticket.
      _ <- KadminUtils.obtainTGT(s"-S $principal", principalWithKeytab, keytab = Some(keytabFile)).rightValueShouldBeUnit()
  
      // Then we try to access kadmin using the keytab
      resultingFuture <- kadminWithKeytab.getPrincipal(principalWithKeytab) rightValue (_.name should startWith(principalWithKeytab))
    } yield resultingFuture
  }

  "listTickets" should "succeed" in {
    for {
      // Obtain a TGT
      _ <- KadminUtils.obtainTGT(principal = principal, password = Some(password)).rightValueShouldBeUnit()
      tickets <- KadminUtils.listTickets().run()
    } yield tickets.exists(_.servicePrincipal == s"krbtgt/$realm@$realm") shouldBe true
  }

  "destroyTickets" should "succeed" in {
    for {
      // Ensure we have the ticket and it is working
      _ <- KadminUtils.obtainTGT(s"-S $principal", principal, password = Some(password)).rightValueShouldBeUnit()
      _ <- kadmin.getPrincipal(principal) rightValue (_.name should startWith(principal))

      _ <- KadminUtils.destroyTickets().run().map(_.shouldBe(()))
      resultingFuture <- kadmin.getPrincipal(principal).leftValue(_ shouldBe a[UnknownError])
    } yield resultingFuture
  }
}
