package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.model.DateTime
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data._
import gov.uk.justice.digital.offenderpollpush.traits.BulkSource
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Poller @Inject() (source: BulkSource,
                        @Named("timeout") timeout: Int,
                        @Named("allOffenders") allOffenders: Boolean) extends Actor with ActorLogging {

  log.info(if (allOffenders) "Poller created to pull all offenders once" else s"Poller created to pull every $timeout seconds ...")

  private case object PullRequest
  private case class State(outstanding: Int, lastCohort: Option[DateTime])

  private val duration = timeout.seconds
  private def builder = context.actorSelection("/user/Builder")

  override def preStart { self ! PullRequest }
  override def receive: Receive = process(State(0, None))

  private def process(state: State): Receive = {

    case PullRequest =>

      if (allOffenders) {

        log.info("Pulling Offender Ids ...")
        source.pullAllIds.pipeTo(self)

      } else {

        log.info("Pulling Offender Deltas ...")
        source.pullDeltas.pipeTo(self)
      }


    case allIdsResult @ AllIdsResult(offenders, _) =>

      log.info(s"Pulled ${offenders.length} Offender ID(s)")

      allIdsResult match {  // State is not updated, so outstanding remains 0, so deleteCohort is correctly never called

        case AllIdsResult(_, Some(error)) => log.warning(s"PULL ERROR: ${error.getMessage}")

        case AllIdsResult(_, None) => for (request <- offenders.map(BuildRequest(_, DateTime.now))) builder ! request
      }


    case pullResult @ PullResult(deltas, _) =>

      log.info(s"Pulled ${deltas.length} Offender Delta(s)")

      val newState = pullResult match {

        case PullResult(_, Some(error)) =>

          log.warning(s"PULL ERROR: ${error.getMessage}")
          state

        case PullResult(Seq(), None) => state

        case PullResult(Seq(_*), None) =>

          val cohort = deltas.map(_.date).max
          val uniqueIds = deltas.map(_.id).distinct

          log.info(s"Cohort $cohort contains ${uniqueIds.length} unique Offender Delta Id(s)")

          for (request <- uniqueIds.map(BuildRequest(_, cohort))) builder ! request

          State(state.outstanding + uniqueIds.length, Some(cohort))  // Replace None or older cohort with latest cohort
      }

      context.system.scheduler.scheduleOnce(duration, self, PullRequest)
      context become process(newState)


    case PushResult(TargetOffender(id, _, cohort), _, _, _) =>

      if (state.outstanding > 0) {

        log.info(s"Completed processing for Offender Delta: $id of Cohort: $cohort")

        context become process(state match {

          case State(1, Some(lastCohort)) =>    // Last of cohort has now been processed, so delete Delta Cohort

            source.deleteCohort(lastCohort).pipeTo(self)
            State(0, None)

          case State(outstanding, lastCohort) => State(outstanding - 1, lastCohort)
        })
      }


    case PurgeResult(cohort, Some(error)) => log.warning(s"PURGE ERROR (Cohort: $cohort): ${error.getMessage}")

    case PurgeResult(cohort, None) => log.info(s"Purged Offender Delta Cohort: $cohort")
  }
}


// test multiple overlapping pulls. test receieving completion back and calling delete.

// Poller doesn't need protecting from pull REST starvation, as will just have another go in scheduled x seconds anyway

// a completed message will decrement, and if reaches zero, willl delete from lastcohort older, and reset to none
// If two pulls come in, numbers are mainatained, and newwest cohot stored,


//@TESTS - DOCKER compose etc
