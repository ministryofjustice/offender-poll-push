package gov.uk.justice.digital.offenderpollpush.data

import gov.uk.justice.digital.offenderpollpush.traits.ErrorResult

case class BuildResult(offender: TargetOffender, error: Option[Throwable]) extends ErrorResult
