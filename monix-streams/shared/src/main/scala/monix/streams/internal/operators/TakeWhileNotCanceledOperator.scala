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

package monix.streams.internal.operators

import monix.execution.Ack
import monix.execution.Ack.Cancel
import monix.execution.cancelables.BooleanCancelable
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber

import scala.concurrent.Future
import scala.util.control.NonFatal

private[streams] final class TakeWhileNotCanceledOperator[A](c: BooleanCancelable)
  extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isActive = true

      def onNext(elem: A): Future[Ack] =
        if (!isActive) Cancel else {
          var streamError = true
          try {
            val isCanceled = c.isCanceled
            streamError = false

            if (!isCanceled)
              out.onNext(elem)
            else {
              isActive = false
              out.onComplete()
              Cancel
            }
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
              Cancel
          }
        }

      def onComplete(): Unit =
        if (isActive) {
          isActive = false
          out.onComplete()
        }

      def onError(ex: Throwable): Unit =
        if (isActive) {
          isActive = false
          out.onError(ex)
        }
    }
}