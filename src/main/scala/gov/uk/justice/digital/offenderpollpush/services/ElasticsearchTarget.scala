package gov.uk.justice.digital.offenderpollpush.services

import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data.{PushResult, TargetOffender}
import gov.uk.justice.digital.offenderpollpush.helpers.FutureListener
import gov.uk.justice.digital.offenderpollpush.traits.SingleTarget
import grizzled.slf4j.Logging
import org.elasticsearch.action.delete.{DeleteRequest, DeleteResponse}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ElasticsearchTarget @Inject() (elasticSearchClient: RestHighLevelClient,
                                     @Named("ingestionPipeline") ingestionPipeline: String) extends SingleTarget with Logging {

  override def push(offender: TargetOffender): Future[PushResult] = {

    (if (offender.deletion) {

      val listener = FutureListener[DeleteResponse]
      val request = new DeleteRequest("offender", "document", offender.id)

      logger.debug(s"Deleting from Elastic Search: ${offender.id}")

      elasticSearchClient.deleteAsync(request, listener)
      listener

    } else {

      val listener = FutureListener[IndexResponse]
      val request = new IndexRequest("offender", "document", offender.id).
        setPipeline(ingestionPipeline).source(offender.json, XContentType.JSON)

      logger.debug(s"Sending to Elastic Search: ${offender.json}")

      elasticSearchClient.indexAsync(request, listener)
      listener

    }).future.map { response =>

      PushResult(offender, Some(response.status.getStatus), response.toString, None)

    }.recover { case t: Throwable => PushResult(offender, None, "", Some(t)) }
  }
}

//@TODO: End to end Tests
