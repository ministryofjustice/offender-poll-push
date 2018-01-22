package gov.uk.justice.digital.offenderpollpush.data

import gov.uk.justice.digital.offenderpollpush.traits.ErrorResult

case class AllIdsResult(page: Int, offenders: Seq[String], error: Option[Throwable]) extends ErrorResult
