import akka.actor.ActorSystem
import akka.http.scaladsl.model.{DateTime, StatusCodes}
import configuration.MockedConfiguration
import gov.uk.justice.digital.offenderpollpush.Server
import gov.uk.justice.digital.offenderpollpush.data._
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget, SingleTargetPublisher}
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

  describe("Offender Delta Processing") {

    it("pulls the full Offender Id paged list, builds, and pushes to the target") {

      val allIdPages = List(Seq("101", "102"), Seq("103", "104"), Seq("105"))

      Given("the source system has 3 pages of All Ids")
      when(mockBulkSource.pullAllIds(any[Int], any[Int])).thenAnswer { invocation => Future {

        val page = invocation.getArgument[Int](1)

        if (page > allIdPages.length)
          AllIdsResult(page, Seq(), Some(new Exception(StatusCodes.NotFound.value)))
        else
          AllIdsResult(page, allIdPages(page - 1), None)
        }
      }
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()

      When("the service runs with a pull all page size of 2")
      runServerWithMockedServices(Some(2))

      Then("four pages of IDs are pulled containing 5 offenders which are build and pushed to target, and no deletions occur")
      eventually(tenSecondTimeout) {

        verify(mockBulkSource, atLeast(4)).pullAllIds(any[Int], any[Int])
        verify(mockSingleSource, times(5)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(5)).push(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }
    }

    it("pulls an empty Offender Delta set from source and performs no processing") {

      Given("the source system has no offender deltas")
      mockBulkSourcePullDeltasOk(Seq())

      When("the service runs")
      runServerWithMockedServices()

      Then("the empty set is pulled and no further processing occurs")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, never()).pull(any[String], any[DateTime])
        verify(mockSingleTarget, never()).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, never()).publish(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }
    }

    it("pulls an empty Offender Delta set from source repeatedly and performs no processing") {

      Given("the source system has no offender deltas")
      mockBulkSourcePullDeltasOk(Seq())

      When("the service runs")
      runServerWithMockedServices()

      Then("the empty set is pulled twice over time and no further processing occurs")
      eventually(tenSecondTimeout) {

        verify(mockBulkSource, times(2)).pullDeltas
        verify(mockSingleSource, never()).pull(any[String], any[DateTime])
        verify(mockSingleTarget, never()).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, never()).publish(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }
    }

    it("pulls an Offender Delta from source, builds, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDelta = SourceOffenderDelta("123", dateTimeNow, "UPSERT")

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
        verify(mockSingleSource, times(1)).pull(offenderDelta.offenderId, dateTimeNow)
        verify(mockSingleTarget, times(1)).push(offenderDelta.targetOffender())
        verify(mockSingleTargetPublisher, times(1)).publish(offenderDelta.targetOffender())
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls two Offender Deltas from source, builds them, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDelta1 = SourceOffenderDelta("123", dateTimeNow, "UPSERT")
      val offenderDelta2 = SourceOffenderDelta("456", dateTimeNow, "UPSERT")

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
        verify(mockSingleSource, times(1)).pull(offenderDelta1.offenderId, dateTimeNow)
        verify(mockSingleSource, times(1)).pull(offenderDelta2.offenderId, dateTimeNow)
        verify(mockSingleTarget, times(2)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(2)).publish(any[TargetOffender])
        verify(mockSingleTarget, times(1)).push(offenderDelta1.targetOffender())
        verify(mockSingleTarget, times(1)).push(offenderDelta2.targetOffender())
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)

      }
    }

    it("pulls six Offender Deltas from source containing four unique ids, builds them, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDeltas = Seq("123", "456", "789", "456", "456", "000").map(SourceOffenderDelta(_, dateTimeNow, "UPSERT"))

      Given("the source system has six offender deltas of which four are unique")
      mockBulkSourcePullDeltasOk(offenderDeltas)
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the four unique offenders are built, copied to the target system, and the source cohort deleted")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(4)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(4)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(4)).publish(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls three Offender Deltas from source containing two unique ids including a DELETE, builds UPSERTS only, and pushes to target") {

      val dateTimeNow = DateTime.now
      val offenderDeltas = Seq(("123", "UPSERT"), ("456", "UPSERT"), ("123", "DELETE")).map(d => SourceOffenderDelta(d._1, dateTimeNow, d._2))

      Given("the source system has three offender deltas of which two are unique and one id has an UPSERT and a DELETE")
      mockBulkSourcePullDeltasOk(offenderDeltas)
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("one offender is built, two are pushed to target including a delete, and the source cohort deleted")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(1)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(2)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(1)).publish(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("pulls three Offender Deltas, processes some, then doesn't pull the next delta until the first delta is processed") {

      var pullDeltaCount = 0
      val dateTimeFirst = DateTime.now
      var dateTimeSecond: DateTime = null
      val slowFirstDeltaBuildResult = Promise[BuildResult]
      val firstDeltaIds = Seq("123", "456", "789")
      val firstDeltaSet = firstDeltaIds.map(SourceOffenderDelta(_, dateTimeFirst, "UPSERT"))
      val secondDeltaIds = Seq("234", "567", "890", "123")
      val slowFirstDeltaTargetOffender = TargetOffender("456", "", dateTimeFirst, deletion = false)

      Given("the source system contains a first Delta of 3 Offenders of which 1 Offender taks a long time to build")
      when(mockBulkSource.pullDeltas).thenAnswer { _ => Future {

        pullDeltaCount = pullDeltaCount + 1

        PullResult(if (pullDeltaCount > 1) {

          dateTimeSecond = DateTime.now

          secondDeltaIds.foreach(mockSingleSourcePullIdOk(_, dateTimeSecond))
          secondDeltaIds.map(SourceOffenderDelta(_, dateTimeSecond, "UPSERT"))

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
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(3)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(2)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(2)).publish(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }

      Then("the slow Offender build from the first Delta finally completes and is deleted, and then the second Delta is processed")
      slowFirstDeltaBuildResult.success(BuildResult(slowFirstDeltaTargetOffender, None))
      eventually(tenSecondTimeout) {

        verify(mockSingleTarget).push(slowFirstDeltaTargetOffender)
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeFirst)
        verify(mockSingleSource, times(7)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(7)).push(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeSecond)
      }
    }

    it("pulls Offender Deltas from source multiple times over time, builds them, and pushes to target") {

      Given("the source system has two offender deltas")
      when(mockBulkSource.pullDeltas).thenAnswer { _ => Future { PullResult(Seq("345", "678").map(SourceOffenderDelta(_, DateTime.now, "UPSERT")), None) }}
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the offenders are built, copied to the target system, and the source cohort deleted for each Delta (twice)")
      eventually(tenSecondTimeout) {

        verify(mockBulkSource, times(2)).pullDeltas
        verify(mockSingleSource, times(4)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(4)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(4)).publish(any[TargetOffender])
        verify(mockBulkSource, times(2)).deleteCohort(any[DateTime])
      }
    }

    it("pulls Offender Deltas with different dates and uses the latest date as the Delta Cohort") {

      val dateTimeNow = DateTime.now
      val offenderDelta1 = SourceOffenderDelta("123", dateTimeNow.minus(2500), "UPSERT")
      val offenderDelta2 = SourceOffenderDelta("456", dateTimeNow, "UPSERT")
      val offenderDelta3 = SourceOffenderDelta("789", dateTimeNow.minus(5000), "UPSERT")

      Given("the source system has three offender deltas with different date times")
      mockBulkSourcePullDeltasOk(Seq(offenderDelta1, offenderDelta2, offenderDelta3))
      mockSingleSourcePullAnyOk()
      mockSingleTargetPushAnyOk()
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("the offenders are built, copied to the target system, and the source cohort is the latest date time of all the deltas")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(3)).pull(any[String], any[DateTime])
        verify(mockSingleTarget, times(3)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(3)).publish(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }

    it("logs and ignores an error when pulling a delta cohort, and pulls again later") {

      Given("the source system has no offender deltas")
      mockBulkSourcePullDeltasError(new Exception("error"))

      When("the service runs")
      runServerWithMockedServices()

      Then("the empty set is pulled twice over time and no further processing occurs")
      eventually(tenSecondTimeout) {

        verify(mockBulkSource, times(2)).pullDeltas
        verify(mockSingleSource, never()).pull(any[String], any[DateTime])
        verify(mockSingleTarget, never()).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, never()).publish(any[TargetOffender])
        verify(mockBulkSource, never()).deleteCohort(any[DateTime])
      }
    }

    it("completes the cohort processing and deletes the cohort if building an offender produces an exception") {

      val dateTimeNow = DateTime.now

      Given("the source system an offender deltas that fails to build")
      mockBulkSourcePullDeltasOk(Seq(SourceOffenderDelta("123", dateTimeNow, "UPSERT")))
      mockSingleSourcePullAnyError(new Exception("Error"))
      mockBulkSourceDeleteCohortAnyOk()

      When("the service runs")
      runServerWithMockedServices()

      Then("pulls the delta, attempt to pull the built offender, does not push due to exception, but does then delete the cohort")
      eventually(fiveSecondTimeout) {

        verify(mockBulkSource, times(1)).pullDeltas
        verify(mockSingleSource, times(1)).pull("123", dateTimeNow)
        verify(mockSingleTarget, times(0)).push(any[TargetOffender])
        verify(mockSingleTargetPublisher, times(0)).publish(any[TargetOffender])
        verify(mockBulkSource, times(1)).deleteCohort(dateTimeNow)
      }
    }
  }

  //@TODO: Pull all set and check and test, change to paged and pull all count etc. Test exceptions ON PUSHER
  //@TODO: Test ElasticTarget. Test End to End integration
  //@TODO: change code to fit pull all paged
  //@TODO: Do some more   ... AND Integration with wiremock to API etc, AND end to end with wire mock etc etc


  private val fiveSecondTimeout = Timeout(Span(5, Seconds))
  private val tenSecondTimeout = Timeout(Span(10, Seconds))

  private var mockBulkSource: BulkSource = _
  private var mockSingleSource: SingleSource = _
  private var mockSingleTarget: SingleTarget = _
  private var mockSingleTargetPublisher: SingleTargetPublisher = _

  private var runningService: ActorSystem = _

  before {
    mockBulkSource = mock[BulkSource]
    mockSingleSource = mock[SingleSource]
    mockSingleTarget = mock[SingleTarget]
    mockSingleTargetPublisher = mock[SingleTargetPublisher]
  }

  after {
    Thread.sleep(1000)              // Allow a second for all logging messages to be delivered
    runningService.terminate()
  }

  private def runServerWithMockedServices(allPullPageSize: Option[Int] = None) {

    runningService = Server.run(MockedConfiguration(mockBulkSource, mockSingleSource, mockSingleTarget, mockSingleTargetPublisher, allPullPageSize))
  }

  private def mockBulkSourcePullDeltasOk(deltas: Seq[SourceOffenderDelta]) = when(mockBulkSource.pullDeltas).thenReturn(

    Future { PullResult(deltas, None) }
  )

  private def mockBulkSourcePullDeltasError(error: Throwable) = when(mockBulkSource.pullDeltas).thenReturn(

    Future { PullResult(Seq(), Some(error)) }
  )

  private def mockSingleSourcePullIdOk(id: String, cohort: DateTime, deletion: Boolean = false) = when(mockSingleSource.pull(id, cohort)).thenReturn(

    Future { BuildResult(TargetOffender(id, "", cohort, deletion), None) }
  )

  private def mockSingleSourcePullAnyOk(deletion: Boolean = false) = when(mockSingleSource.pull(any[String], any[DateTime])).thenAnswer { invocation =>

    Future { BuildResult(TargetOffender(invocation.getArgument[String](0), "", invocation.getArgument[DateTime](1), deletion), None) }
  }

  private def mockSingleSourcePullAnyError(error: Throwable, deletion: Boolean = false) = when(mockSingleSource.pull(any[String], any[DateTime])).thenAnswer { invocation =>

    Future { BuildResult(TargetOffender(invocation.getArgument[String](0), "", invocation.getArgument[DateTime](1), deletion), Some(error)) }
  }

  private def mockSingleTargetPushAnyOk() = when(mockSingleTarget.push(any[TargetOffender])).thenAnswer { invocation =>

    Future { PushResult(invocation.getArgument[TargetOffender](0), Some(StatusCodes.OK), "", None) }
  }

  private def mockBulkSourceDeleteCohortAnyOk() = when(mockBulkSource.deleteCohort(any[DateTime])).thenAnswer { invocation =>

    Future { PurgeResult(invocation.getArgument[DateTime](0), None) }
  }
}
