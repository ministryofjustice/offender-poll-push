package configuration

import gov.uk.justice.digital.offenderpollpush.Configuration
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget}

case class MockedConfiguration(bulkSource: BulkSource, singleSource: SingleSource, singleTarget: SingleTarget, allPullPageSize: Option[Int] = None) extends Configuration {

  override protected def envDefaults: Map[String, String] = {

    val overrides = allPullPageSize.map(pageSize => Seq("ALL_PULL_PAGE_SIZE" -> pageSize.toString, "INDEX_ALL_OFFENDERS" -> true.toString)).getOrElse(Seq())

    super.envDefaults ++ overrides
  }

  override protected def configureOverridable() {

    bind[BulkSource].toInstance(bulkSource)
    bind[SingleSource].toInstance(singleSource)
    bind[SingleTarget].toInstance(singleTarget)
  }
}
