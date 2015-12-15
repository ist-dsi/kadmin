package pt.tecnico.dsi.kadmin

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class LackOfPrivelegesSpec extends FlatSpec with Matchers with ScalaFutures {
  val defaultPatience = PatienceConfig(
    timeout = Span(1, Seconds),
    interval = Span(2, Seconds)
  )

  val authenticatedConfig = ConfigFactory.parseString(s"""
    kadmin {
      realm = "EXAMPLE.COM"

      perform-authentication = true

      authenticating-principal = ""
      authenticating-principal-password = ""

      command-with-authentication = "kadmin -p "$${kadmin.authenticating-principal}
      command-without-authentication = "kadmin.local"
    }""")

  val kerberos = new Kadmin(authenticatedConfig)
  import kerberos._

  //In kadm5.acl have an entry
  //noPermissions@IST.UTL.PT X *
  //This means the principal noPermissions@IST.UTL.PT has no permissions for every principal


  //addPrincipal lack of privilege add

  //deletePrincipal lack of privilege delete

  //modifyPrincipal lack of privilege modify

  //changePassword lack of privilege changepw

  //getPrincipal lack of privilege inquire
}
