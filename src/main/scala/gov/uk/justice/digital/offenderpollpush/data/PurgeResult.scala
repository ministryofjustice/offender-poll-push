package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime
import gov.uk.justice.digital.offenderpollpush.traits.ErrorResult

case class PurgeResult(cohort: DateTime, error: Option[Throwable]) extends ErrorResult
