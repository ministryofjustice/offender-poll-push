package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.traits.SingleTarget

class Pusher @Inject() (target: SingleTarget) extends Actor with ActorLogging {

  override def receive: Receive = ???
}
