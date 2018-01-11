package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime

case class TargetOffender(id: String, json: String, cohort: DateTime)
