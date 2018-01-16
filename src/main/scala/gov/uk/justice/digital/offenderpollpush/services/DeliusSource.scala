package gov.uk.justice.digital.offenderpollpush.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data._
import gov.uk.justice.digital.offenderpollpush.traits.{BulkSource, SingleSource}
import grizzled.slf4j.Logging
import org.json4s.Formats
import org.json4s.native.Serialization._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeliusSource @Inject() (@Named("apiBaseUrl") apiBaseUrl: String, @Named("apiUsername") apiUsername: String)
                             (implicit val formats: Formats,
                              implicit val system: ActorSystem,
                              implicit val materializer: ActorMaterializer) extends BulkSource with SingleSource with Logging {

  private val http = Http()

  private def stringUnmarshaller(range: ContentTypeRange) = Unmarshaller.stringUnmarshaller.forContentTypes(range).map { string =>

    logger.debug(s"Received from Delius API: $string")
    string
  }

  private def jsonUnmarshaller = stringUnmarshaller(MediaTypes.`application/json`)

  private implicit val rawJsonUnmarshaller: Unmarshaller[HttpEntity, String] = jsonUnmarshaller
  private implicit val seqJsonUnmarshaller: Unmarshaller[HttpEntity, Seq[SourceOffenderDelta]] = jsonUnmarshaller.map(read[Seq[SourceOffenderDelta]])
  private implicit val seqIdsUnmarshaller: Unmarshaller[HttpEntity, Seq[String]] = jsonUnmarshaller.map(read[Seq[String]])

  private implicit val plainTextUnmarshaller: Unmarshaller[HttpEntity, String] = stringUnmarshaller(MediaTypes.`text/plain`)


  private def makeRequest[T, R](headers: List[HttpHeader],
                                uri: String,
                                success: T => R,
                                failure: Throwable => R,
                                method: HttpMethod = HttpMethods.GET,
                                body: RequestEntity = HttpEntity.Empty) = {

    val request = HttpRequest(method, Uri(uri), headers, body)

    logger.debug(s"Requesting from Delius API: $uri")

    http.singleRequest(request).flatMap {

      case response @ HttpResponse(statusCode, _, _, _) if statusCode.isFailure =>

        response.discardEntityBytes()         //@TODO: Necessary? Test
        throw new Exception(statusCode.value)

      case HttpResponse(_, _, entity, _) =>

        Unmarshal(entity).to[T].map(success)

    }.recover { case error: Throwable => failure(error) }
  }

  private def logon = //@TODO: Keep old one while valid for a while ... no need to logon each time

    makeRequest[String, List[HttpHeader]](
      List(),
      s"$apiBaseUrl/logon",
      bearerToken => List(Authorization(OAuth2BearerToken(bearerToken))),
      _ => List(),
      HttpMethods.POST,
      apiUsername
    )


  override def pullDeltas: Future[PullResult] =

    logon.flatMap { authHeaders =>

      makeRequest[Seq[SourceOffenderDelta], PullResult](
        authHeaders,
        s"$apiBaseUrl/offenderDeltaIds",
        PullResult(_, None),
        error => PullResult(Seq(), Some(error))
      )
    }

  override def pullAllIds: Future[AllIdsResult] =

    logon.flatMap { authHeaders =>

      makeRequest[Seq[String], AllIdsResult](
        authHeaders,
        s"$apiBaseUrl/allOffenderIds",
        AllIdsResult(_, None),
        error => AllIdsResult(Seq(), Some(error))
      )
    }

  override def pull(id: String, cohort: DateTime): Future[BuildResult] =

    logon.flatMap { authHeaders =>

      makeRequest[String, BuildResult](
        authHeaders,
        s"$apiBaseUrl/offender/id/$id",
        json => BuildResult(TargetOffender(id, json, cohort), None),
        error => BuildResult(TargetOffender(id, "", cohort), Some(error))
      )
    }

  override def deleteCohort(cohort: DateTime): Future[PurgeResult] =

    logon.flatMap { authHeaders =>

      makeRequest[String, PurgeResult](
        authHeaders,
        s"$apiBaseUrl/offenderDeltaIds/olderThan/${cohort.toString}",
        _ => PurgeResult(cohort, None),
        error => PurgeResult(cohort, Some(error)),
        HttpMethods.DELETE
      )
    }
}
