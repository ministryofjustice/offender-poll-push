package gov.uk.justice.digital.offenderpollpush.traits

import akka.http.scaladsl.model.DateTime
import gov.uk.justice.digital.offenderpollpush.data.{PurgeResult, PullResult}
import scala.concurrent.Future

trait BulkSource {

  def pullDeltas: Future[PullResult]

  //@TODO: Pull all - All result - only Seq[Strings] ids, no times, used in one-off allOffenders call

  def deleteCohort(cohort: DateTime): Future[PurgeResult]
}
