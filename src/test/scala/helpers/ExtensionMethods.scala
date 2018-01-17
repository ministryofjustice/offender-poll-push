package helpers

import gov.uk.justice.digital.offenderpollpush.data.{SourceOffenderDelta, TargetOffender}

object ExtensionMethods {

  implicit class StubTargetOffender(source: SourceOffenderDelta) {

    def targetOffender = TargetOffender(source.id, "", source.date)
  }

}
