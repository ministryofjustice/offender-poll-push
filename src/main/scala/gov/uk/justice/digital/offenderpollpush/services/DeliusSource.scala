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

  private def stringUnmarshaller(ranges: ContentTypeRange*) =

    ranges.foldLeft(Unmarshaller.stringUnmarshaller)((unmarshaller, range) => unmarshaller.forContentTypes(range)).map { string =>

      logger.debug(s"Received from Delius API: $string")
      string
    }

  private def jsonUnmarshaller[T](transform: String => T) = stringUnmarshaller(MediaTypes.`application/json`).map(transform)
  private def readUnmarshaller[T: Manifest] = jsonUnmarshaller(read[T])

  private implicit val plainTextUnmarshaller: Unmarshaller[HttpEntity, String] = stringUnmarshaller()

  private implicit val seqIdsUnmarshaller: Unmarshaller[HttpEntity, Seq[String]] = readUnmarshaller[Seq[String]]
  private implicit val seqJsonUnmarshaller: Unmarshaller[HttpEntity, Seq[SourceOffenderDelta]] = readUnmarshaller[Seq[SourceOffenderDelta]]


  private def makeRequest[T, R](transform: Unmarshal[HttpEntity] => Future[T],
                                headers: List[HttpHeader],
                                uri: String,
                                success: T => R,
                                failure: Throwable => R,
                                method: HttpMethod = HttpMethods.GET,
                                body: RequestEntity = HttpEntity.Empty) = {

    val request = HttpRequest(method, Uri(uri), headers, body)

    logger.debug(s"Requesting from Delius API: $uri")

    http.singleRequest(request).flatMap {

      case response @ HttpResponse(statusCode, _, _, _) if statusCode.isFailure =>

        response.discardEntityBytes()
        throw new Exception(statusCode.value)

      case HttpResponse(_, _, entity, _) =>

        transform(Unmarshal(entity)).map(success)

    }.recover { case error: Throwable => failure(error) }
  }

/*
  private var credentials: Future[List[HttpHeader]] = Future(List[HttpHeader]()) // will need sync for this to change it
//credentials.isCompleted

  private def attemptRequestAndLogonIfNeeded[T <: ErrorResult](request: () => Future[T]): Future[T] = {

// Need to wait for any exising credentials to complete then use. quite thorny

// if one in process then append only
// if one finished try, and and if fails append again

    credentials.value match {

      case Some(Success(authHeaders)) => authHeader


    }
    credentials.isCompleted

    request().flatMap { result =>

      result.error match {

        case Some(throwable) if throwable.getMessage == StatusCodes.Unauthorized.value =>

          logon.flatMap { authHeaders =>

            a

          }

          Future { result }


        case _ => Future { result }
      }
    }
  }
*/

  private def logon = //@TODO: Keep old one while valid for a while ... no need to logon each time. Shared state across threads though

    makeRequest[String, List[HttpHeader]](
      _.to[String],
      List(),
      s"$apiBaseUrl/logon",
      bearerToken => List(Authorization(OAuth2BearerToken(bearerToken))),
      error => {

        logger.error(s"Login for $apiUsername failed", error)
        List()
      },
      HttpMethods.POST,
      apiUsername
    )


  override def pullDeltas: Future[PullResult] =

    logon.flatMap { authHeaders =>

      makeRequest[Seq[SourceOffenderDelta], PullResult](
        _.to[Seq[SourceOffenderDelta]],
        authHeaders,
        s"$apiBaseUrl/offenderDeltaIds",
        PullResult(_, None),
        error => PullResult(Seq(), Some(error))
      )
    }

  override def pullAllIds: Future[AllIdsResult] =

    logon.flatMap { authHeaders =>

      makeRequest[Seq[String], AllIdsResult](
        _.to[Seq[String]],
        authHeaders,
        s"$apiBaseUrl/allOffenderIds",
        AllIdsResult(_, None),
        error => AllIdsResult(Seq(), Some(error))
      )
    }

  override def pull(id: String, cohort: DateTime): Future[BuildResult] =

    logon.flatMap { authHeaders =>

      makeRequest[String, BuildResult](
        _.to[String],
        authHeaders,
        s"$apiBaseUrl/offenders/offenderId/$id",
        json => BuildResult(TargetOffender(id, json, cohort), None),
        error => BuildResult(TargetOffender(id, error.getMessage, cohort), Some(error))
      )
    }

  override def deleteCohort(cohort: DateTime): Future[PurgeResult] =

    logon.flatMap { authHeaders =>

      makeRequest[String, PurgeResult](
        _.to[String],
        authHeaders,
        s"$apiBaseUrl/offenderDeltaIds?before=${cohort.toString}",
        _ => PurgeResult(cohort, None),
        error => PurgeResult(cohort, Some(error)),
        HttpMethods.DELETE
      )
    }
}
