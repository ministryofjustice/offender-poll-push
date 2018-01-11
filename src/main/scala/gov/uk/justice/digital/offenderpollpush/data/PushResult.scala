package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.StatusCode

case class PushResult(offender: TargetOffender, result: Option[StatusCode], body: String, error: Option[Throwable])
