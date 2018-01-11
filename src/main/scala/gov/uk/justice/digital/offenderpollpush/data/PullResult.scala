package gov.uk.justice.digital.offenderpollpush.data

case class PullResult(events: Seq[SourceOffenderDelta], error: Option[Throwable])
