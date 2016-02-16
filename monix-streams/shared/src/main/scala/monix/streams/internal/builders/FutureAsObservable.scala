/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.streams.internal.builders

import monix.execution.{CancelableFuture, Cancelable}
import monix.streams.Observable
import monix.streams.observers.Subscriber
import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Converts any `Future` into an observable */
private[streams] final class FutureAsObservable[T](source: Future[T])
  extends Observable[T] {

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    import subscriber.{scheduler => s}

    source.value match {
      case Some(Success(value)) =>
        subscriber.onNext(value)
        subscriber.onComplete()
        Cancelable.empty
      case Some(Failure(ex)) =>
        subscriber.onError(ex)
        Cancelable.empty
      case None =>
        source.onComplete {
          case Success(value) =>
            subscriber.onNext(value)
            subscriber.onComplete()
          case Failure(ex) =>
            subscriber.onError(ex)
        }

        source match {
          case c: CancelableFuture[_] => c
          case _ => Cancelable.empty
        }
    }
  }
}
