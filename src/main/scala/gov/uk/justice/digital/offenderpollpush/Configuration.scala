package gov.uk.justice.digital.offenderpollpush

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.AbstractModule
import gov.uk.justice.digital.offenderpollpush.injection._
import gov.uk.justice.digital.offenderpollpush.services.{DeliusSource, ElasticsearchTarget}
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget}
import net.codingwell.scalaguice.ScalaModule
import org.elasticsearch.client.{RestClientBuilder, RestHighLevelClient}
import org.json4s.Formats
import scala.util.Properties

class Configuration extends AbstractModule with ScalaModule {

  private def envOrDefault(key: String) = Properties.envOrElse(key, envDefaults(key))

  private def bindNamedValue[T: Manifest](name: String, value: T) { bind[T].annotatedWithName(name).toInstance(value) }

  private def bindConfiguration[T: Manifest](map: Map[String, String], transform: String => T) {

    for ((name, value) <- map.mapValues(envOrDefault).mapValues(transform)) bindNamedValue(name, value)
  }

  protected def envDefaults = Map(
    "DEBUG_LOG" -> "false",
    "INDEX_ALL_OFFENDERS" -> "false",
    "DELIUS_API_BASE_URL" -> "http://localhost:8080/api",
    "DELIUS_API_USERNAME" -> "unknown",
    "ELASTIC_SEARCH_HOST" -> "localhost",
    "ELASTIC_SEARCH_PORT" -> "9200",
    "PROCESS_PAGE_SIZE" -> "10",
    "POLL_SECONDS" -> "5"
  )

  override final def configure() {

    bindConfiguration(
      Map(
        "apiBaseUrl" -> "DELIUS_API_BASE_URL",
        "apiUsername" -> "DELIUS_API_USERNAME",
        "searchHost" -> "ELASTIC_SEARCH_HOST"
      ),
      identity
    )

    bindConfiguration(
      Map(
        "timeout" -> "POLL_SECONDS",
        "pageSize" -> "PROCESS_PAGE_SIZE",
        "searchPort" -> "ELASTIC_SEARCH_PORT"
      ),
      s => s.toInt
    )

    bindConfiguration(
      Map(
        "debugLog" -> "DEBUG_LOG",
        "allOffenders" -> "INDEX_ALL_OFFENDERS"
      ),
      s => s.toBoolean
    )

    bind[Formats].toProvider[FormatsProvider]
    bind[ActorMaterializer].toProvider[ActorMaterializerProvider]
    bind[RestClientBuilder].toProvider[RestClientBuilderProvider]
    bind[RestHighLevelClient].toProvider[RestHighLevelClientProvider]

    bind[ActorSystem].toProvider[ActorSystemProvider].asEagerSingleton()

    configureOverridable()
  }

  protected def configureOverridable() {

    bind[BulkSource].to[DeliusSource]
    bind[SingleSource].to[DeliusSource]
    bind[SingleTarget].to[ElasticsearchTarget]
  }
}
