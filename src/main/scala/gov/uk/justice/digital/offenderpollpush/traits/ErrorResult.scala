package gov.uk.justice.digital.offenderpollpush.traits

trait ErrorResult {

  def error: Option[Throwable]
}
