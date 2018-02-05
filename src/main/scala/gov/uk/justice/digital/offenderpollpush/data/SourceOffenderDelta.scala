package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime

case class SourceOffenderDelta(offenderId: String, dateChanged: DateTime, action: String)
