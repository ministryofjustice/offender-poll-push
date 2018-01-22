import akka.actor.ActorSystem
import akka.http.scaladsl.model.DateTime
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import gov.uk.justice.digital.offenderpollpush.data.{PullResult, SourceOffenderDelta, TargetOffender}
import gov.uk.justice.digital.offenderpollpush.helpers.DateTimeSerializer
import gov.uk.justice.digital.offenderpollpush.services.DeliusSource
import org.json4s.{Formats, NoTypeHints}
import org.json4s.native.Serialization
import org.scalatest.{BeforeAndAfterAll, FunSpec, GivenWhenThen, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class DeliusSpec extends FunSpec with BeforeAndAfterAll with GivenWhenThen with Matchers {

  implicit val formats: Formats = Serialization.formats(NoTypeHints) + DateTimeSerializer
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  describe("Pull from Delius API") {

// check pull performs a login
// check pull works too - an offender pull that has a correct id

    it("GET Offender uses POST logon (with user name as body text) body text response as Bearer Token authorisation") {

      val testPort = 8081

      configureFor(testPort)
      val api = new WireMockServer(options.port(testPort))
      val source = new DeliusSource(s"http://localhost:$testPort/api", "NationalUser")

      Given("the source API")
      api.start()

      When("an Offender is pulled from the API")
      val result = Await.result(source.pull("123", DateTime.now), 5.seconds)

      Then("the API receives a HTTP GET call with Authorization and returns the Offender Delta Ids and timestamps")
      verify(
        postRequestedFor(
          urlEqualTo("/api/logon")).
          withRequestBody(equalTo("NationalUser"))
      )
      verify(
        getRequestedFor(
          urlEqualTo("/api/offenders/offenderId/123")).
          withHeader("Authorization", equalTo("Bearer ABCDEFGHIJKLMNOP"))
      )
      result.error.get.toString should include("404")

      api.stop()
    }

    it("GETs Offender from the API") {

      val testPort = 8081

      configureFor(testPort)
      val api = new WireMockServer(options.port(testPort))
      val source = new DeliusSource(s"http://localhost:$testPort/api", "NationalUser")

      Given("the source API")
      api.start()
      val dateNow = DateTime.now

      When("Offender Delta Id and timestamps are pulled from the API")
      val result = Await.result(source.pull("456", dateNow), 5.seconds)

      Then("the API receives a HTTP GET call with Authorization and returns the Offender Delta Ids and timestamps")
      result.error shouldBe None
      result.offender shouldBe TargetOffender("456", "{\"some\":\"json\"}", dateNow)

      api.stop()
    }

//@TODO: CHcek DeliusSource with wrong usename produces 401 unath for pull id. Also check pull id with correct one works
// Use id 000

    it("GETs Offender Delta Ids and timestamps from the API") {

      val testPort = 8083

      configureFor(testPort)
      val api = new WireMockServer(options.port(testPort))
      val source = new DeliusSource(s"http://localhost:$testPort/api", "NationalUser")

      Given("the source API")
      api.start()

      When("Offender Delta Id and timestamps are pulled from the API")
      val result = Await.result(source.pullDeltas, 5.seconds)

      Then("the API receives a HTTP GET call with Authorization and returns the Offender Delta Ids and timestamps")
      verify(
        getRequestedFor(
          urlEqualTo("/api/offenderDeltaIds")).
          withHeader("Authorization", equalTo("Bearer ABCDEFGHIJKLMNOP"))
      )

      result shouldBe PullResult(Seq(
        SourceOffenderDelta("2500160158", DateTime.fromIsoDateTimeString("2018-01-18T15:35:13").get),
        SourceOffenderDelta("2500160156", DateTime.fromIsoDateTimeString("2018-01-18T15:35:35").get),
        SourceOffenderDelta("2500160158", DateTime.fromIsoDateTimeString("2018-01-18T15:36:36").get)
      ), None)

      api.stop()
    }

    it("reports a failure HTTP response code as an error") {

      val testPort = 8084

      configureFor(testPort)
      val api = new WireMockServer(options.port(testPort))
      val source = new DeliusSource(s"http://localhost:$testPort/internalError", "NationalUser")

      Given("the source API returns an 500 Internal Error")
      api.start()

      When("an Offender Delta IDs pull from the API is attempted")
      val result = Await.result(source.pullDeltas, 5.seconds)

      Then("the 500 error is reported")
      result.error.get.toString should include("500")

      api.stop()
    }
  }

  it("GETs Offenders Page from the API") {

    val testPort = 8085

    configureFor(testPort)
    val api = new WireMockServer(options.port(testPort))
    val source = new DeliusSource(s"http://localhost:$testPort/api", "NationalUser")

    Given("the source API")
    api.start()

    When("the first page of the full Offender list is pulled from API")
    val result = Await.result(source.pullAllIds(5, 1), 5.seconds)

    Then("the first page of Offender Ids are retrieved")
    result.error shouldBe None
    result.page shouldBe 1
    result.offenders shouldBe Seq("2500000501", "2500000502", "2500000503", "2500000504", "2500000505")

    api.stop()
  }

  //@TODO: Test Pull All Ids as well, And Logon as well

  override def afterAll() {

    system.terminate()
  }
}
