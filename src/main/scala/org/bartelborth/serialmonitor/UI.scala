package org.bartelborth.serialmonitor

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Queue
import monocle.macros.Lenses
import org.bartelborth.serialmonitor.UI._
import org.bartelborth.serialmonitor.swing._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class Contexts[F[_]](cs: ContextShift[F], sec: SwingExecutionContext, timer: Timer[F]) extends ContextShift[F] {
  object implicits {
    implicit val catsEffectTimer: Timer[F]                    = timer
    implicit val swingExecutionContext: SwingExecutionContext = sec
  }
  override def shift: F[Unit]                                  = cs.shift
  override def evalOn[A](ec: ExecutionContext)(fa: F[A]): F[A] = cs.evalOn(ec)(fa)
  def evalOnEdt[A]: F[A] => F[A]                               = cs.evalOn[A](sec.edtEc)
}

object UI {
  type View       = Model => PartialFunction[Message, IO[Unit]]
  type Dispatcher = Message => IO[Unit]
  type Updater    = Model => Message => IO[Model]
  @Lenses case class State(model: Model = Model(), views: List[View] = List.empty)
}

class UI(initial: Model, ref: Ref[IO, UI.State], messages: Queue[IO, Message], updater: Dispatcher => Updater) {
  import UI._
  val publish: Dispatcher                   = messages.enqueue1
  val read: (Model => IO[Unit]) => IO[Unit] = f => ref.get.map(_.model).flatMap(f)
  def init[A]: (Model => IO[A]) => IO[A]    = f => f(initial)
  private val update: Updater               = updater(publish)

  private val addView: View => State => State = v => s => State.views.set(s.views :+ v)(s)
  def subscribe(view: View): IO[Unit]         = ref.update(addView(view))

  // DEMO: RUN
  def run(contexts: Contexts[IO]): IO[Unit] =
    for {
      message <- messages.dequeue1
      _ <- if (message == Scan || message.isInstanceOf[IngestData]) IO.unit
          else IO(println(s"${Thread.currentThread().getName} got message $message"))
      s            <- ref.get
      views        = s.views
      currentModel = s.model
      execution    <- IO(update(currentModel)(message)).attempt
      _            <- execution.left.toOption.traverse(t => IO(t.printStackTrace()))
      nextModel    <- execution.toOption.get
      _            <- ref.set(State.model.set(nextModel)(s))
      _            <- contexts.evalOnEdt(views.flatMap(_.apply(nextModel).lift(message)).sequence)
      _            <- run(contexts)
    } yield ()
}
