package gov.uk.justice.digital.offenderpollpush.helpers

import org.elasticsearch.action.ActionListener

class FutureListener[T] extends ActionListener[T] with FutureAdapter[T] {

  override def onFailure(e: Exception) { promise.failure(e) }

  override def onResponse(response: T) { promise.success(response) }
}

object FutureListener {

  def apply[T] = new FutureListener[T]
}
