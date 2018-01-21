package gov.uk.justice.digital.offenderpollpush.injection

import com.google.inject.Provider
import gov.uk.justice.digital.offenderpollpush.helpers.DateTimeSerializer
import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}

class FormatsProvider extends Provider[Formats] {

  override def get: Formats = Serialization.formats(NoTypeHints) + DateTimeSerializer
}
