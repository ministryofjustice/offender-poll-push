package gov.uk.justice.digital.offenderpollpush.helpers

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import ch.qos.logback.classic.Level
import com.google.inject.name.Named
import com.google.inject.{Inject, Injector}
import grizzled.slf4j.{Logger, Logging}
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

  private case class LoggingConfig @Inject() (@Named("debugLog") debugLog: Boolean)

  implicit class InjectedLoggingConfig(rootLogger: Logger)(implicit injector: Injector) {

    def enableDebugIfRequired() {

      (injector.instance[LoggingConfig].debugLog, rootLogger.logger) match { // DEBUG_LOG=true

        case (true, logback: ch.qos.logback.classic.Logger) => logback.setLevel(Level.DEBUG) // Set Logback to DEBUG if required
        case _ =>
      }
    }
  }
}
