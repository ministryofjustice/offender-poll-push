package gov.uk.justice.digital.offenderpollpush

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import gov.uk.justice.digital.offenderpollpush.injection._
import gov.uk.justice.digital.offenderpollpush.services.{AwsSnsTarget, DeliusSource, ElasticsearchTarget}
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource, SingleTarget, SingleTargetPublisher}
import net.codingwell.scalaguice.ScalaModule
import org.elasticsearch.client.{RestClientBuilder, RestHighLevelClient}
import org.json4s.Formats

import scala.util.Properties

class Configuration extends ScalaModule {

  private def envOrDefault(key: String) = Properties.envOrElse(key, envDefaults(key))

  private def bindNamedValue[T: Manifest](name: String, value: T) { bind[T].annotatedWithName(name).toInstance(value) }

  private def bindConfiguration[T: Manifest](map: Map[String, String], transform: String => T) {

    for ((name, value) <- map.mapValues(envOrDefault).mapValues(transform)) bindNamedValue(name, value)
  }

  def allSettings: Map[String, String] = envDefaults.keys.map { key => (key, envOrDefault(key)) }.toMap

  protected def envDefaults = Map(
    "DEBUG_LOG" -> "false",
    "INDEX_ALL_OFFENDERS" -> "false",
    "INGESTION_PIPELINE" -> "pnc-pipeline",
    "DELIUS_API_BASE_URL" -> "http://localhost:8080/api",
    "DELIUS_API_USERNAME" -> "unknown",
    "ELASTIC_SEARCH_SCHEME" -> "http",
    "ELASTIC_SEARCH_HOST" -> "localhost",
    "ELASTIC_SEARCH_PORT" -> "9200",
    "ELASTIC_SEARCH_AWS_REGION" -> "eu-west-2",
    "ELASTIC_SEARCH_AWS_SERVICENAME" -> "es",
    "ELASTIC_SEARCH_AWS_SIGNREQUESTS" -> "false",
    "SNS_REGION" -> "eu-west-2",
    "SNS_SERVICE_NAME" -> "sns",
    "SNS_ARN_TOPIC" -> "arn:aws:sns:us-east-1:000000000000:offender_topic",
    "SNS_PORT" -> "4575",
    "SNS_ENDPOINT" -> "http://localhost:4575",
    "SNS_ACCESS_KEY_ID" -> "foo",
    "SNS_SECRET_ACCESS_KEY" -> "foo",
    "SNS_MSG_EVENT_TYPE" -> "offender-change",
    "SNS_MSG_SOURCE" -> "delius",
    "SNS_MSG_SUBJECT" -> "offender changes message",
    "ALL_PULL_PAGE_SIZE" -> "1000",
    "PROCESS_PAGE_SIZE" -> "10",
    "POLL_SECONDS" -> "5"
  )

  override final def configure() {

    bindConfiguration(
      Map(
        "apiBaseUrl" -> "DELIUS_API_BASE_URL",
        "apiUsername" -> "DELIUS_API_USERNAME",
        "searchHost" -> "ELASTIC_SEARCH_HOST",
        "searchScheme" -> "ELASTIC_SEARCH_SCHEME",
        "searchAWSRegion" -> "ELASTIC_SEARCH_AWS_REGION",
        "searchAWSServiceName" -> "ELASTIC_SEARCH_AWS_SERVICENAME",
        "ingestionPipeline" -> "INGESTION_PIPELINE",
        "snsEndpoint" -> "SNS_ENDPOINT",
        "snsRegion" -> "SNS_REGION",
        "snsArnTopic" -> "SNS_ARN_TOPIC",
        "awsAccessKeyId" -> "SNS_ACCESS_KEY_ID",
        "awsSecretAccessKey" -> "SNS_SECRET_ACCESS_KEY",
        "snsMsgEventType" -> "SNS_MSG_EVENT_TYPE",
        "snsMsgSource" -> "SNS_MSG_SOURCE",
        "snsMsgSubject" -> "SNS_MSG_SUBJECT"),
      identity
    )

    bindConfiguration(
      Map(
        "timeout" -> "POLL_SECONDS",
        "pageSize" -> "PROCESS_PAGE_SIZE",
        "searchPort" -> "ELASTIC_SEARCH_PORT",
        "allPullPageSize" -> "ALL_PULL_PAGE_SIZE"
      ),
      s => s.toInt
    )

    bindConfiguration(
      Map(
        "debugLog" -> "DEBUG_LOG",
        "allOffenders" -> "INDEX_ALL_OFFENDERS",
        "signSearchRequests" -> "ELASTIC_SEARCH_AWS_SIGNREQUESTS"
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
    bind[SingleTargetPublisher].to[AwsSnsTarget]
  }
}
