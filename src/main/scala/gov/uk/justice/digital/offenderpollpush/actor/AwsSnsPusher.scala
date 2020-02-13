package gov.uk.justice.digital.offenderpollpush.actor

import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.TargetOffender
import gov.uk.justice.digital.offenderpollpush.traits.{LoggingActor, SingleTargetPublisher}

class AwsSnsPusher @Inject()(target: SingleTargetPublisher) extends LoggingActor {

  override def receive: Receive = {

    case targetOffender @ TargetOffender(id, _, _, _) =>

      log.info(s"Pushing Offender ID: $id to SNS topic")
      target.publish(targetOffender);

    case _ =>
      log.info(s"Pushing Got result to SNS topic")

  }

}
