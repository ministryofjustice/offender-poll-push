package gov.uk.justice.digital.offenderpollpush.traits

import gov.uk.justice.digital.offenderpollpush.data.{PushResult, TargetOffender}
import scala.concurrent.Future

trait SingleTarget {

  def push(offender: TargetOffender): Future[PushResult]
}
