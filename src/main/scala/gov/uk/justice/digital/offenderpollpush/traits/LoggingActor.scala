package gov.uk.justice.digital.offenderpollpush.traits

import akka.actor.{Actor, ActorLogging}

trait LoggingActor extends Actor with ActorLogging {

  override def preStart() {

    log.info(s"Starting Akka Actor: ${self.path.name}")
  }

  override def postStop() {

    log.info(s"Stopped Akka Actor: ${self.path.name}")
  }
}
