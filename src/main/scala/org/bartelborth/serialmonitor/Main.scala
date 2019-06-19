package org.bartelborth.serialmonitor

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp}
import fs2.concurrent.Queue
import javax.swing.{JFrame, UIManager, WindowConstants}
import org.bartelborth.serialmonitor.swing._
import org.pushingpixels.substance.api.skin.SubstanceGraphiteGoldLookAndFeel

object Main extends IOApp {
  def startUI: IO[Unit] =
    for {
      _        <- IO(JFrame.setDefaultLookAndFeelDecorated(true))
      sec      <- IO(SwingExecutionContext.fromEdtEc)
      contexts <- IO(new Contexts[IO](contextShift, sec, timer))
      _        <- contexts.evalOnEdt(IO(UIManager.setLookAndFeel(new SubstanceGraphiteGoldLookAndFeel())))
      initial  <- IO(UI.State())
      state    <- Ref.of[IO, UI.State](initial)
      messages <- Queue.bounded[IO, Message](20000)
      ui       = new UI(initial.model, state, messages, Message.update)
      _        <- ui.run(contexts).start
      frame    <- contexts.evalOnEdt(MainFrame())
      _        <- IO(frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE))
      panel    <- contexts.evalOnEdt(MainPanel(ui))
      _        <- contexts.evalOnEdt(IO(frame.setContentPane(panel)))
      _        <- frame.addWindowOpenedListenerIO(ui.publish(ShouldScan(true)))
      _        <- contexts.evalOnEdt(IO(frame.setVisible(true)))
      _        <- PortScanner(ui)
      _        <- PortCommunicator(ui)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- IO(Thread.setDefaultUncaughtExceptionHandler((_: Thread, e: Throwable) => e.printStackTrace()))
      _ <- startUI
    } yield ExitCode.Success
}
