package gov.uk.justice.digital.offenderpollpush.helpers

import scala.concurrent.{Future, Promise}

trait FutureAdapter[T] {

  protected val promise: Promise[T] = Promise[T]

  def future: Future[T] = promise.future
}
