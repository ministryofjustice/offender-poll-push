package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.{PushResult, TargetOffender}
import gov.uk.justice.digital.offenderpollpush.traits.SingleTarget
import scala.concurrent.ExecutionContext.Implicits.global

class Pusher @Inject() (target: SingleTarget) extends Actor with ActorLogging {

  private def paging = context.actorSelection("/user/Paging")

  override def receive: Receive = {

    case targetOffender @ TargetOffender(id, _, cohort) =>

      log.info(s"Pushing Offender ID: $id of Delta Cohort: $cohort")
      target.push(targetOffender).pipeTo(self)


    case pushResult @ PushResult(offender, _, body, _) =>

      (pushResult.result, pushResult.error) match {

        case (_, Some(error)) => log.warning(s"Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} PUSH ERROR: ${error.getMessage}")

        case (Some(result), None) => log.info(s"Push for Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} returned ${result.value} $body")

        case _ => log.warning("PUSH ERROR: No result or error")
      }

      paging ! pushResult
  }
}
