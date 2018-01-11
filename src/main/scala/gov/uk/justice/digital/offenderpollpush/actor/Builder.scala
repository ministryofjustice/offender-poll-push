package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.traits.SingleSource

class Builder @Inject() (source: SingleSource) extends Actor with ActorLogging {

  override def receive: Receive = ???

}
