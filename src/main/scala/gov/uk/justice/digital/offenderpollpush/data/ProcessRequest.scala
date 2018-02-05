package gov.uk.justice.digital.offenderpollpush.data

import akka.http.scaladsl.model.DateTime

case class ProcessRequest(id: String, cohort: DateTime, deletion: Boolean)
