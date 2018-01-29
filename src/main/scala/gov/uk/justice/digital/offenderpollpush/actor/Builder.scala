package gov.uk.justice.digital.offenderpollpush.actor

import akka.pattern.pipe
import com.google.inject.{Inject, Injector}
import gov.uk.justice.digital.offenderpollpush.data.{BuildRequest, BuildResult, PushResult}
import gov.uk.justice.digital.offenderpollpush.helpers.ExtensionMethods._
import gov.uk.justice.digital.offenderpollpush.traits.{LoggingActor, SingleSource}
import scala.concurrent.ExecutionContext.Implicits.global

class Builder @Inject() (source: SingleSource)(implicit injector: Injector) extends LoggingActor {

  private def paging = context.parent
  private val pusher = context.startActor[Pusher]

  override def receive: Receive = {

    case BuildRequest(id, cohort) =>

      log.info(s"Pulling Offender ID: $id of Delta Cohort: $cohort")
      source.pull(id, cohort).pipeTo(self)


    case buildResult @ BuildResult(offender, _) =>

      buildResult.error match {

        case Some(error) =>

          log.warning(s"BUILD ERROR: ${error.getMessage}")
          paging ! PushResult(offender, None, "", None)     // Inform Poller that future Push attempt has been account for

        case None =>

          log.info(s"Pulled Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} with ${offender.json.length} JSON chars")
          pusher ! offender
      }
  }
}
