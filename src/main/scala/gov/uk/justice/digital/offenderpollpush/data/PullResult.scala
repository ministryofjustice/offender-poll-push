package gov.uk.justice.digital.offenderpollpush.data

case class PullResult(delta: Seq[SourceOffenderDelta], error: Option[Throwable])
