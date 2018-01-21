package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.data.{BuildRequest, PushResult, TargetOffender}

class Paging @Inject() (@Named("pageSize") pageSize: Int) extends Actor with ActorLogging {

  private case class State(currentPage: Int, waitingPages: Seq[Seq[BuildRequest]])

  private def poller = context.actorSelection("/user/Poller")
  private def builder = context.actorSelection("/user/Builder")

  override def receive: Receive = process(State(0, Seq()))

//@TODO: Do lots of logging about paging here //@TODO: Add paging TESTS

  private def process(state: State): Receive = {

    case request @ BuildRequest(_, _) =>

      context become process(if (state.waitingPages.isEmpty) {    // Current page only and nothing queued

        if (state.currentPage < pageSize) {      // Under max concurrent page, so add to now. Could be decremented later

          builder ! request
          State(state.currentPage + 1, Seq())

        } else {  // page limit reached, start to queue, will be sent when currentPage drops to zero ...

          log.info("Creating the first waiting page of build requests")

          State(state.currentPage, Seq(Seq(request))) // Add to waitingPages - first one!
        }

      } else {    // Already have waiting pages (state.waitingPages.nonEmpty), what now based on currentPage

        State(state.currentPage, if (state.waitingPages.last.length < pageSize) { // Add to existing last non-full waiting page

          state.waitingPages.init :+ (state.waitingPages.last :+ request)

        } else { // All waiting pages full, start a new one

          log.info(s"Creating an new waiting page in addition to ${state.waitingPages.length} pages of $pageSize requests")

          state.waitingPages :+ Seq(request)
        })
      })

    case result @ PushResult(TargetOffender(id, _, cohort), _, _, _) =>

      poller ! result

      context become process(if (state.currentPage > 1) {  // Others of current page still in flight, so let them complete

        State(state.currentPage - 1, state.waitingPages)

      } else { // Was only one outstanding, so current page has completed. Now send all of next page if it exists, and change state

        if (state.waitingPages.nonEmpty) {

          log.info(s"Sending a page of $pageSize build requests with ${state.waitingPages.length - 1} pages remaining")

          for (request <- state.waitingPages.head) builder ! request

          State(state.waitingPages.head.length, state.waitingPages.tail)

        } else {  // No current page left and none waiting

          State(0, Seq())
        }
      })
  }
}

// add to waiting pages if in apprproate place
// after, if current page is 0 and have a full waiting page then send
// this won't work, only want to page after x requests outstanfinf .. rethinf
// allow a build up first, then once hit a full page (which may be decremented in mean time), wait for page to finish - add to waiting - and send all next page in meantime
// when adding to pages keep to full page size and start a new page.
// all logic will be on build request and push results. we dont; deal with build requests, only push results when completes

