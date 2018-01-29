package gov.uk.justice.digital.offenderpollpush

import akka.actor.ActorSystem
import com.google.inject.{Guice, Injector, Module}
import gov.uk.justice.digital.offenderpollpush.actor._
import gov.uk.justice.digital.offenderpollpush.helpers.ExtensionMethods._
import grizzled.slf4j.{Logger, Logging}
import net.codingwell.scalaguice.InjectorExtensions._

object Server extends App with Logging {

  logger.info(s"Started Offender PollPush Service [Version ${getClass.getPackage.getImplementationVersion}] ...")

  def run(config: Module = new Configuration) = {

    implicit val injector: Injector = Guice.createInjector(config)
    val system = injector.instance[ActorSystem]

    Logger.rootLogger.enableDebugIfRequired()

    system.startActor[Paging]
    system
  }

  run()
}
