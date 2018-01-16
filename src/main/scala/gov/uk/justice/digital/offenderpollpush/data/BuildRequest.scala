package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime

case class BuildRequest(id: String, cohort: DateTime)
