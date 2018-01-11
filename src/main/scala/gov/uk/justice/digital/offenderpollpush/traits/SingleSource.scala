package gov.uk.justice.digital.offenderpollpush.traits

import akka.http.scaladsl.model.DateTime
import gov.uk.justice.digital.offenderpollpush.data.{BuildResult, TargetOffender}
import scala.concurrent.Future

trait SingleSource {

  def pull(id: String, cohort: DateTime): Future[BuildResult]
}
