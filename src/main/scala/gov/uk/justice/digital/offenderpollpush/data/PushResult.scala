package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.StatusCode
import gov.uk.justice.digital.offenderpollpush.traits.ErrorResult

case class PushResult(offender: TargetOffender, result: Option[StatusCode], body: String, error: Option[Throwable]) extends ErrorResult
