package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.{BuildRequest, BuildResult, PushResult}
import gov.uk.justice.digital.offenderpollpush.traits.SingleSource
import scala.concurrent.ExecutionContext.Implicits.global

class Builder @Inject() (source: SingleSource) extends Actor with ActorLogging {

  private def paging = context.actorSelection("/user/Paging")
  private def pusher = context.actorSelection("/user/Pusher")

  override def receive: Receive = {

    case BuildRequest(id, cohort) =>

      log.info(s"Pulling Offender ID: $id of Delta Cohort: $cohort")
      source.pull(id, cohort).pipeTo(self)


    case buildResult @ BuildResult(offender, _) =>

      buildResult.error match {

        case Some(error) =>

          log.warning(s"BUILD ERROR: ${error.getMessage}")
          paging ! PushResult(offender, None, "", None)     // Inform Poller that future Push attempt has been account for

        case None =>

          log.info(s"Pulled Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} with ${offender.json.length} JSON chars")
          pusher ! offender
      }
  }
}
