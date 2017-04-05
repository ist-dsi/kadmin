package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.AsyncFlatSpec

/**
  * $assumptions
  */
class KeytabSpec extends AsyncFlatSpec with TestUtils {
  import fullPermissionsKadmin._
  import settings.keytabsLocation

  "obtain keytab" should "return KeytabDoesNotExist if there is no keytab" in {
    val principal = "obtainKeytab"
  
    for {
      _ <- deletePrincipal(principal).rightValueShouldBeUnit()
      resultingFuture <- obtainKeytab(principal) shouldBe Left(KeytabDoesNotExist)
    } yield resultingFuture
  }
  it should "succeed if a keytab exists" in {
    val principal = "obtainKeytab"
  
    for {
      _ <- addPrincipal("", principal, randKey = true).rightValueShouldBeUnit()
      _ <- createKeytab("", principal).rightValueShouldBeUnit()
      resultingFuture <- obtainKeytab(principal).toOption.value.length should be > 0
    } yield resultingFuture
  }

  "create keytab" should "succeed" in {
    val principal = "createKeytab"
    val kadmin = new Kadmin(ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"
      principal = "$principal"
      keytab = "$keytabsLocation/$principal.keytab"
    }"""))
    
    for {
      _ <- addPrincipal("", principal, randKey = true).rightValueShouldBeUnit()
    
      // This also changes the principal password
      _ <- createKeytab("", principal).rightValueShouldBeUnit()
    
      // This will test whether the keytab was successfully created
      resultingFuture <- kadmin.getPrincipal(principal).rightValue (_.name should startWith (principal))
    } yield resultingFuture
  }
}
