import akka.actor.ActorSystem
import akka.http.scaladsl.model.{DateTime, StatusCodes}
import configuration.MockedConfiguration
import gov.uk.justice.digital.offenderpollpush.Server
import gov.uk.justice.digital.offenderpollpush.data._
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget}
import helpers.ExtensionMethods._
import org.scalatest.{BeforeAndAfter, FunSpec, GivenWhenThen, Matchers}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class ServerSpec extends FunSpec with MockitoSugar with BeforeAndAfter with GivenWhenThen with Eventually { // with Matchers { // @TODO: Need all?

  //@TODO: Pull all set and check and test, change to paged and pull all count etc

  describe("Offender Delta Processing") {

    it("pulls an Offender Delta from source, builds, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDelta = SourceOffenderDelta("123", dateTimeNow)

      Given("the source system has an offender deltas")
      mockBulkSourcePullDeltasOk(Seq(offenderDelta))
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the offender is built, copied to the target system, and the source cohort deleted")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(1)).pull(offenderDelta.id, dateTimeNow)
        verify(mockSingleTarget, times(1)).push(offenderDelta.targetOffender)
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls two Offender Deltas from source, builds them, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDelta1 = SourceOffenderDelta("123", dateTimeNow)
      val offenderDelta2 = SourceOffenderDelta("456", dateTimeNow)

      Given("the source system has two offender deltas")
      mockBulkSourcePullDeltasOk(Seq(offenderDelta1, offenderDelta2))
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the offenders are built, copied to the target system, and the source cohort deleted")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(2)).pull(any[String], any[DateTime])
        verify(mockSingleSource, times(1)).pull(offenderDelta1.id, dateTimeNow)
        verify(mockSingleSource, times(1)).pull(offenderDelta2.id, dateTimeNow)
        verify(mockSingleTarget, times(2)).push(any[TargetOffender])
        verify(mockSingleTarget, times(1)).push(offenderDelta1.targetOffender)
        verify(mockSingleTarget, times(1)).push(offenderDelta2.targetOffender)
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls six Offender Deltas from source containing four unique ids, builds them, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDeltas = Seq("123", "456", "789", "456", "456", "000").map(SourceOffenderDelta(_, dateTimeNow))

      Given("the source system has six offender deltas of which four are unique")
      mockBulkSourcePullDeltasOk(offenderDeltas)
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the four uninque offenders are built, copied to the target system, and the source cohort deleted")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(4)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(4)).push(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls three Offender Deltas, processes some, then pulls the next delta before the first delta is processed") {

      var pullDeltaCount = 0
      val dateTimeFirst = DateTime.now
      var dateTimeSecond: DateTime = null
      val slowFirstDeltaBuildResult = Promise[BuildResult]
      val firstDeltaIds = Seq("123", "456", "789")
      val firstDeltaSet = firstDeltaIds.map(SourceOffenderDelta(_, dateTimeFirst))
      val secondDeltaIds = Seq("234", "567", "890", "123")
      val slowFirstDeltaTargetOffender = TargetOffender("456", "", dateTimeFirst)

      Given("the source system contains a first Delta of 3 Offenders of which 1 Offender taks a long time to build")
      when(mockBulkSource.pullDeltas).thenAnswer { _ => Future {

        pullDeltaCount = pullDeltaCount + 1

        PullResult(if (pullDeltaCount > 1) {

          dateTimeSecond = DateTime.now

          secondDeltaIds.foreach(mockSingleSourcePullIdOk(_, dateTimeSecond))
          secondDeltaIds.map(SourceOffenderDelta(_, dateTimeSecond))

        } else {

          firstDeltaSet

        }, None)
      }}
      mockSingleSourcePullIdOk("123", dateTimeFirst)
      when(mockSingleSource.pull("456", dateTimeFirst)).thenReturn(slowFirstDeltaBuildResult.future)
      mockSingleSourcePullIdOk("789", dateTimeFirst)
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs and pulls a second Delta of 4 Offenders and processes before the first Delta has finished processing")
      runServerWithMockedServices()
      eventually(tenSecondTimeout) {

        verify(mockBulkSource, times(2)).pullDeltas
        verify(mockSingleSource, times(7)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(6)).push(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }

      Then("the slow Offender build from the first Delta finally completes and everying older than the first Cohort is deleted in one call")
      slowFirstDeltaBuildResult.success(BuildResult(slowFirstDeltaTargetOffender, None))
      eventually(fiveSecondTimeout) {

        verify(mockSingleTarget).push(slowFirstDeltaTargetOffender)
        verify(mockBulkSource, never()).deleteCohort(dateTimeFirst)
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeSecond)
      }
    }

  }

//@TODO: Do some more   ... AND Integration with wiremock to API etc, AND end to end with wire mock etc etc
// Check pull again with diff date while not finished procesing has on 1 with new date. Use Promise to control and force

//  // test an empty one PUllResult too, and pulled twice
// Test Pulls occur twice after 5 to 10 seconds


  private val fiveSecondTimeout = Timeout(Span(5, Seconds))
  private val tenSecondTimeout = Timeout(Span(10, Seconds))

  private var mockBulkSource: BulkSource = _
  private var mockSingleSource: SingleSource = _
  private var mockSingleTarget: SingleTarget = _

  private var runningService: ActorSystem = _

  before {
    mockBulkSource = mock[BulkSource]
    mockSingleSource = mock[SingleSource]
    mockSingleTarget = mock[SingleTarget]
  }

  after {
    Thread.sleep(1000)              // Allow a second for all logging messages to be delivered
    runningService.terminate()
  }

  private def runServerWithMockedServices(timeout: Int = 5) {

    runningService = Server.run(MockedConfiguration(mockBulkSource, mockSingleSource, mockSingleTarget, timeout))
  }

  private def mockBulkSourcePullDeltasOk(deltas: Seq[SourceOffenderDelta]) = when(mockBulkSource.pullDeltas).thenReturn(Future {

    PullResult(deltas, None)
  })

  private def mockBulkSourcePullDeltasError(error: Throwable) = when(mockBulkSource.pullDeltas).thenReturn(Future {

    PullResult(Seq(), Some(error))
  })

  private def mockSingleSourcePullIdOk(id: String, cohort: DateTime) = when(mockSingleSource.pull(id, cohort)).thenReturn(

    Future { BuildResult(TargetOffender(id, "", cohort), None) }
  )

  private def mockSingleSourcePullAnyOk() = when(mockSingleSource.pull(any[String], any[DateTime])).thenAnswer { invocation =>

    Future { BuildResult(TargetOffender(invocation.getArgument[String](0), "", invocation.getArgument[DateTime](1)), None) }
  }

  private def mockSingleTargetPushAnyOk() = when(mockSingleTarget.push(any[TargetOffender])).thenAnswer { invocation =>

    Future { PushResult(invocation.getArgument[TargetOffender](0), Some(StatusCodes.OK), "", None) }
  }

  private def mockBulkSourceDeleteCohortAnyOk() = when(mockBulkSource.deleteCohort(any[DateTime])).thenAnswer { invocation =>

    Future { PurgeResult(invocation.getArgument[DateTime](0), None) }
  }

}