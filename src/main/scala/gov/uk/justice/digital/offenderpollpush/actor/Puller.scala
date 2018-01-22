package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.model.DateTime
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data.{AllIdsRequest, AllIdsResult, BuildRequest}
import gov.uk.justice.digital.offenderpollpush.traits.BulkSource
import scala.concurrent.ExecutionContext.Implicits.global

class Puller @Inject() (source: BulkSource,
                        @Named("allPullPageSize") allPullPageSize: Int) extends Actor with ActorLogging {

  private def paging = context.actorSelection("/user/Paging")

  override def receive: Receive = {

    case AllIdsRequest(page) =>

      log.info(s"Pulling Offender Ids page $page with page size $allPullPageSize ...")

      source.pullAllIds(allPullPageSize, page).pipeTo(self)


    case allIdsResult @ AllIdsResult(page, offenders, _) =>

      log.info(s"Pulled page $page of ${offenders.length} Offender ID(s)")

      allIdsResult match {  // Poller State is not updated, so outstanding remains 0, so deleteCohort is correctly never called

        case AllIdsResult(_, _, Some(error)) => log.warning(s"PULL ERROR: ${error.getMessage}")

        case AllIdsResult(_, _, None) =>

          for (request <- offenders.map(BuildRequest(_, DateTime.now))) paging ! request

          self ! AllIdsRequest(page + 1)
      }
  }
}

//@TODO: Perform poison pill once finished to exit for all IDs
