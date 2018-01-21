package gov.uk.justice.digital.offenderpollpush.injection

import akka.actor.ActorSystem
import com.google.inject.Provider

class ActorSystemProvider extends Provider[ActorSystem] {

  override def get = ActorSystem("offenderpollpush")
}
