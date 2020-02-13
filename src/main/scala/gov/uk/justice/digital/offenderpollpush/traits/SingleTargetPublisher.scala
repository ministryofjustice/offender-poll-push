package gov.uk.justice.digital.offenderpollpush.traits

import gov.uk.justice.digital.offenderpollpush.data.TargetOffender

trait SingleTargetPublisher {

  def publish(offender: TargetOffender)
}
