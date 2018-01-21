package gov.uk.justice.digital.offenderpollpush.data

import gov.uk.justice.digital.offenderpollpush.traits.ErrorResult

case class PullResult(delta: Seq[SourceOffenderDelta], error: Option[Throwable]) extends ErrorResult
