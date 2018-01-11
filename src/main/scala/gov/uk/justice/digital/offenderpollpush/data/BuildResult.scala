package gov.uk.justice.digital.offenderpollpush.data

case class BuildResult(offender: Option[TargetOffender], error: Option[Throwable])
