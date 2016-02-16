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

import minitest.TestSuite
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.streams.Observable
import monix.streams.exceptions.DummyException
import monix.streams.observers.Subscriber
import scala.concurrent.Future
import scala.concurrent.duration._

object DoOnErrorSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  val dummy = DummyException("ex")

  test("should work for synchronous subscribers") { implicit s =>
    var wasTriggered: Throwable = null
    var wasCompleted = 0

    Observable.now(1).endWithError(dummy).doOnError(ex => wasTriggered = ex)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Continue
        def onError(ex: Throwable): Unit = wasCompleted += 1
        def onComplete(): Unit = ()
      })

    assertEquals(wasCompleted, 1)
    assertEquals(wasTriggered, dummy)
  }

  test("should work for asynchronous subscribers") { implicit s =>
    var wasTriggered: Throwable = null
    var wasCompleted = 0

    Observable.now(1).endWithError(dummy).doOnError(ex => wasTriggered = ex)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s
        def onNext(elem: Int) = Future(Continue)
        def onError(ex: Throwable): Unit = wasCompleted += 1
        def onComplete(): Unit = ()
      })

    s.tick()
    assertEquals(wasCompleted, 1)
    assertEquals(wasTriggered, dummy)
  }

  test("should stream onComplete") { implicit s =>
    var wasTriggered = 0
    var wasCompleted = 0

    Observable.range(0,10).doOnError(_ => wasTriggered += 1)
      .unsafeSubscribeFn(new Subscriber[Long] {
        val scheduler = s
        def onNext(elem: Long): Future[Continue] =
          if (elem % 2 == 0) Continue else Future(Continue)

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit =
          wasCompleted += 1
      })

    s.tick()
    assertEquals(wasTriggered, 0)
    assertEquals(wasCompleted, 1)
  }

  test("should be cancelable") { implicit s =>
    var wasTriggered = 0
    val cancelable = Observable.now(1)
      .delayOnNext(1.second)
      .endWithError(dummy)
      .doOnError(ex => wasTriggered += 1)
      .subscribe()

    s.tick()
    assert(s.state.get.tasks.nonEmpty, "tasks.nonEmpty")
    cancelable.cancel()
    assert(s.state.get.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(wasTriggered, 0)
  }

  test("should protect against user code") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")
    var errorThrown: Throwable = null

    Observable.now(1).endWithError(dummy2).doOnError(_ => throw dummy1)
      .unsafeSubscribeFn(new Subscriber[Int] {
        val scheduler = s

        def onNext(elem: Int) = Continue
        def onError(ex: Throwable) = errorThrown = ex
        def onComplete() = ()
      })

    s.tick()
    assertEquals(s.state.get.lastReportedError, dummy1)
    assertEquals(errorThrown, dummy2)
  }
}