package gov.uk.justice.digital.offenderpollpush.injection

import com.google.inject.Provider
import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}

class FormatsProvider extends Provider[Formats] {

  override def get() = Serialization.formats(NoTypeHints)
}
