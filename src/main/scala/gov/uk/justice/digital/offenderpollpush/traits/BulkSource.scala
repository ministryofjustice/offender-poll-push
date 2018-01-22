package gov.uk.justice.digital.offenderpollpush.traits

import akka.http.scaladsl.model.DateTime
import gov.uk.justice.digital.offenderpollpush.data.{AllIdsResult, PullResult, PurgeResult}
import scala.concurrent.Future

trait BulkSource {

  def pullDeltas: Future[PullResult]

  def pullAllIds(pageSize: Int, page: Int): Future[AllIdsResult]

  def deleteCohort(cohort: DateTime): Future[PurgeResult]
}
