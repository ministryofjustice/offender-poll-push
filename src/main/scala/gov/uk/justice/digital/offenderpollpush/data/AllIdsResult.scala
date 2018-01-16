package gov.uk.justice.digital.offenderpollpush.data

case class AllIdsResult(offenders: Seq[String], error: Option[Throwable])
