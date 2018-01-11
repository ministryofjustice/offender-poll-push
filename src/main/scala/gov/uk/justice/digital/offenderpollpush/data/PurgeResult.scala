package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime

case class PurgeResult(cohort: Option[DateTime], error: Option[Throwable])
