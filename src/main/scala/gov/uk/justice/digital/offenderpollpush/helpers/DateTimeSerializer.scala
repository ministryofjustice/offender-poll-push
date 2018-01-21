package gov.uk.justice.digital.offenderpollpush.helpers

import akka.http.scaladsl.model.DateTime
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

object DateTimeSerializer extends CustomSerializer[DateTime](_ => (
  {
    case JString(dateTime) => DateTime.fromIsoDateTimeString(dateTime).get
  },
  {
    case dateTime: DateTime => JString(dateTime.toIsoDateString)
  }
))
