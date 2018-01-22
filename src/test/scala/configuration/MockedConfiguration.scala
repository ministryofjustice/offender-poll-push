package configuration

import gov.uk.justice.digital.offenderpollpush.Configuration
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget}

case class MockedConfiguration(bulkSource: BulkSource, singleSource: SingleSource, singleTarget: SingleTarget, allPullPageSize: Int) extends Configuration {

  override protected def envDefaults: Map[String, String] = super.envDefaults + ("ALL_PULL_PAGE_SIZE" -> allPullPageSize.toString)

  override protected def configureOverridable() {

    bind[BulkSource].toInstance(bulkSource)
    bind[SingleSource].toInstance(singleSource)
    bind[SingleTarget].toInstance(singleTarget)
  }
}
