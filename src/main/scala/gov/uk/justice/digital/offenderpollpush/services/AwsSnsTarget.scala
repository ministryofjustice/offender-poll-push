package gov.uk.justice.digital.offenderpollpush.services

import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.TargetOffender
import gov.uk.justice.digital.offenderpollpush.injection.AwsSnsPublisher
import gov.uk.justice.digital.offenderpollpush.traits.SingleTargetPublisher
import grizzled.slf4j.Logging

class AwsSnsTarget @Inject()(snsPublisher: AwsSnsPublisher) extends SingleTargetPublisher with Logging {

  override def publish(offender: TargetOffender)= {

    logger.debug(s"SNS TARGET ID TO PUBLISH: ${offender.id}")
    snsPublisher.run(offender.json)
  }
}

