package gov.uk.justice.digital.offenderpollpush.services

import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.{PushResult, TargetOffender}
import gov.uk.justice.digital.offenderpollpush.helpers.FutureListener
import gov.uk.justice.digital.offenderpollpush.traits.SingleTarget
import grizzled.slf4j.Logging
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ElasticsearchTarget @Inject() (elasticSearchClient: RestHighLevelClient) extends SingleTarget with Logging {

  override def push(offender: TargetOffender): Future[PushResult] = {

    val listener = FutureListener[IndexResponse]
    val request = new IndexRequest("offender", "document", offender.id).source(offender.json, XContentType.JSON)

    logger.debug(s"Sending to Elastic Search: ${offender.json}")

    elasticSearchClient.indexAsync(request, listener)

    listener.future.map { response =>

      PushResult(offender, Some(response.status.getStatus), response.toString, None)

    }.recover { case t: Throwable => PushResult(offender, None, "", Some(t)) }
  }
}

//@TODO: Tests
