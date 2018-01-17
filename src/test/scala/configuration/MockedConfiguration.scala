package configuration

import gov.uk.justice.digital.offenderpollpush.Configuration
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget}

case class MockedConfiguration(bulkSource: BulkSource, singleSource: SingleSource, singleTarget: SingleTarget, timeout: Int) extends Configuration {

  override protected def envDefaults: Map[String, String] = super.envDefaults + ("POLL_SECONDS" -> timeout.toString)

  override protected def configureOverridable() {

    bind[BulkSource].toInstance(bulkSource)
    bind[SingleSource].toInstance(singleSource)
    bind[SingleTarget].toInstance(singleTarget)
  }
}
