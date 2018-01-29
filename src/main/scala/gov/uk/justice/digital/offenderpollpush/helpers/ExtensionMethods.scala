package gov.uk.justice.digital.offenderpollpush.helpers

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.google.inject.Injector
import grizzled.slf4j.Logging
import net.codingwell.scalaguice.InjectorExtensions._
import scala.reflect.ClassTag

object ExtensionMethods {

  implicit class InjectedActorFactory(factory: ActorRefFactory)(implicit injector: Injector) extends Logging {

    def startActor[T <: Actor : Manifest]: ActorRef = {

      val actor = factory.actorOf(Props(injector.instance[T]), implicitly[ClassTag[T]].runtimeClass.getName.split('.').last)

      logger.debug(s"Created ${actor.path}")
      actor
    }
  }
}
