package gov.uk.justice.digital.offenderpollpush.data

case class BuildResult(offender: TargetOffender, error: Option[Throwable])
