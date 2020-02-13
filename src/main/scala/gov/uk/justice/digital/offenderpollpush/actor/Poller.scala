package gov.uk.justice.digital.offenderpollpush.actor

import akka.http.scaladsl.model.DateTime
import akka.pattern.pipe
import com.google.inject.{Inject, Injector}
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data._
import gov.uk.justice.digital.offenderpollpush.helpers.ExtensionMethods._
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, LoggingActor}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Poller @Inject() (source: BulkSource,
                        @Named("timeout") timeout: Int,
                        @Named("allOffenders") allOffenders: Boolean)(implicit injector: Injector) extends LoggingActor {

  log.info(if (allOffenders) "Poller created to pull all offenders once" else s"Poller created to pull every $timeout seconds ...")

  private case object PullRequest
  private case class State(outstanding: Int, lastCohort: Option[DateTime])

  private val duration = timeout.seconds

  private def paging = context.parent
  private lazy val puller = context.startActor[Puller]

  override def preStart() {

    super.preStart()
    self ! PullRequest
  }

  override def receive: Receive = process(State(0, None))

  private def schedulePullRequest() = context.system.scheduler.scheduleOnce(duration, self, PullRequest)

  private def process(state: State): Receive = {

    case PullRequest =>

      if (allOffenders) {

        puller ! AllIdsRequest(1)

      } else {

        log.info("Pulling Offender Deltas ...")
        source.pullDeltas.pipeTo(self)
      }


    case pullResult @ PullResult(deltas, _) =>

      log.info(s"Pulled ${deltas.length} Offender Delta(s)")

      context become process(
        pullResult match {

          case PullResult(Seq(_, _*), None) =>  // At least one result and no Error

            val cohort = deltas.map(_.dateChanged).max

            def processRequests(uniqueIds: Seq[String], deletion: Boolean = false) {

              for (request <- uniqueIds.map(ProcessRequest(_, cohort, deletion))) {
                paging ! request
              }
            }

            val actionUniqueIds = deltas.groupBy(_.action).mapValues(_.map(_.offenderId).distinct)

            val deleteUniqueIds = actionUniqueIds.getOrElse("DELETE", Seq())
            val upsertUniqueIds = actionUniqueIds.getOrElse("UPSERT", Seq()).filterNot(deleteUniqueIds.contains(_)) // Deletes trump any upserts

            log.info(s"Cohort $cohort contains ${upsertUniqueIds.length} unique UPSERT Offender Delta Id(s)")
            log.info(s"Cohort $cohort contains ${deleteUniqueIds.length} unique DELETE Offender Delta Id(s)")

            processRequests(upsertUniqueIds)
            processRequests(deleteUniqueIds, deletion = true)

            State(state.outstanding + upsertUniqueIds.length + deleteUniqueIds.length, Some(cohort))  // Replace None or older cohort with latest cohort

          case _ => // PullResult(Seq(), None) or PullResult(_, Some(error))

            for (error <- pullResult.error) log.warning(s"PULL ERROR: ${error.getMessage}") // Log error if it exists

            schedulePullRequest()   // For successful empty pulls, or error pulls, schedule a pull request after timeout
            state
        }
      )


    case PushResult(TargetOffender(id, _, cohort, _), _, _, _) =>

      if (state.outstanding > 0) {

        log.info(s"Completed processing for Offender Delta: $id of Cohort: $cohort")

        context become process(state match {

          case State(1, Some(lastCohort)) =>    // Last of cohort has now been processed, so delete Delta Cohort

            source.deleteCohort(lastCohort).pipeTo(self)
            State(0, None)

          case State(outstanding, lastCohort) =>

            State(outstanding - 1, lastCohort)
        })
      }


    case purgeResult @ PurgeResult(cohort, _) =>

      purgeResult.error match {

        case Some(error) => log.warning(s"PURGE ERROR (Cohort: $cohort): ${error.getMessage}")

        case None => log.info(s"Purged Offender Delta Cohort: $cohort")
      }

      schedulePullRequest()   // Now a pull has successfully processed requests and finished, schedule a new pull after timeout
  }
}
