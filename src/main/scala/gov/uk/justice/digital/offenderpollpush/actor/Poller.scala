package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import com.google.inject.name.Named
import gov.uk.justice.digital.offenderpollpush.traits.BulkSource

class Poller @Inject() (source: BulkSource,
                        @Named("timeout") timeout: Int,
                        @Named("allOffenders") allOffenders: Boolean) extends Actor with ActorLogging {

  log.info(if (allOffenders) "Poller created to pull all offenders once" else s"Poller created to pull every $timeout seconds ...")


  override def receive: Receive = ???

}



// call pullAll, de-duplicate, and pul out max/highest co-hort date
// Honour all offenders if option chosen - need to implement in BulkSource as well

//@TESTS - DOCKER compose etc