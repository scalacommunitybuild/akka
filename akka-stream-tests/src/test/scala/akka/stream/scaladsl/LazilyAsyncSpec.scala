/*
 * Copyright (C) 2014-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future

import com.github.ghik.silencer.silent
import org.scalatest.concurrent.ScalaFutures

import akka.Done
import akka.stream.testkit.StreamSpec
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.StreamTestKit._
import akka.testkit.DefaultTimeout

@silent("deprecated") // tests deprecated methods
class LazilyAsyncSpec extends StreamSpec with DefaultTimeout with ScalaFutures {

  import system.dispatcher

  "A lazy async source" should {

    "work in happy path scenario" in assertAllStagesStopped {
      val stream = Source
        .lazilyAsync { () =>
          Future(42)
        }
        .runWith(Sink.head)

      stream.futureValue should ===(42)
    }

    "call factory method on demand only" in assertAllStagesStopped {
      val probe = TestSubscriber.probe[Int]()
      val constructed = new AtomicBoolean(false)

      Source
        .lazilyAsync { () =>
          constructed.set(true); Future(42)
        }
        .runWith(Sink.fromSubscriber(probe))
      probe.cancel()

      constructed.get() should ===(false)
    }

    "fail materialized value when downstream cancels without ever consuming any element" in assertAllStagesStopped {
      val materialization = Source
        .lazilyAsync { () =>
          Future(42)
        }
        .toMat(Sink.cancelled)(Keep.left)
        .run()

      intercept[RuntimeException] {
        materialization.futureValue
      }
    }

    "materialize when the source has been created" in assertAllStagesStopped {
      val probe = TestSubscriber.probe[Int]()

      val materialization: Future[Done] =
        Source
          .lazilyAsync { () =>
            Future(42)
          }
          .mapMaterializedValue(_.map(_ => Done))
          .to(Sink.fromSubscriber(probe))
          .run()

      materialization.value shouldEqual None
      probe.request(1)
      probe.expectNext(42)
      materialization.futureValue should ===(Done)

      probe.cancel()
    }

    "propagate failed future from factory" in assertAllStagesStopped {
      val probe = TestSubscriber.probe[Int]()
      val failure = new RuntimeException("too bad")
      Source
        .lazilyAsync { () =>
          Future.failed(failure)
        }
        .to(Sink.fromSubscriber(probe))
        .run()

      probe.request(1)
      probe.expectError(failure)
    }
  }
}
