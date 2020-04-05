package org.bartelborth.serialmonitor

import java.awt.event.{ActionListener, WindowAdapter, WindowEvent, WindowListener}

import cats.effect.IO
import javax.swing.event.{ChangeListener, ListSelectionEvent, ListSelectionListener}

import scala.language.reflectiveCalls

object swing {
  type AddActionListeneree        = { def addActionListener(al: ActionListener): Unit }
  type AddListSelectionListeneree = { def addListSelectionListener(lsl: ListSelectionListener): Unit }
  type AddChangeListeneree        = { def addChangeListener(cl: ChangeListener): Unit }
  type AddWindowListeneree        = { def addWindowListener(wl: WindowListener): Unit }

  implicit class RichAddWindowListener(c: AddWindowListeneree) {
    def addWindowOpenedListenerIO(action: WindowEvent => IO[Unit]): IO[Unit] =
      IO(c.addWindowListener(new WindowAdapter {
        override def windowOpened(e: WindowEvent): Unit = action(e).unsafeRunAsyncAndForget()
      }))
    def addWindowOpenedListenerIO(action: => IO[Unit]): IO[Unit] =
      c.addWindowOpenedListenerIO(_ => action)
  }

  implicit class RichAddListSelectionListeneree(c: AddListSelectionListeneree) {
    def addListSelectionListenerIO(action: ListSelectionEvent => IO[Unit]): IO[Unit] =
      IO(c.addListSelectionListener(e => action(e).unsafeRunAsyncAndForget()))
    def addListSelectionListenerIO(action: => IO[Unit]): IO[Unit] =
      c.addListSelectionListenerIO(_ => action)
  }

  implicit class RichAddChangeListeneree(c: AddChangeListeneree) {
    def addChangeListenerIO(action: => IO[Unit]): IO[Unit] =
      IO(c.addChangeListener(_ => action.unsafeRunAsyncAndForget()))
  }

  implicit class RichAddActionListeneree(c: AddActionListeneree) {
    def addActionListenerIO(action: => IO[Unit]): IO[Unit] =
      IO(c.addActionListener(_ => action.unsafeRunAsyncAndForget()))
  }

  import javax.swing.SwingUtilities

  import scala.concurrent.ExecutionContext

  case class SwingExecutionContext private (edtEc: ExecutionContext) extends AnyVal

  object SwingExecutionContext {
    def createEdtEc: ExecutionContext = ExecutionContext.fromExecutor { runnable =>
      if (SwingUtilities.isEventDispatchThread) runnable.run()
      else SwingUtilities.invokeLater(runnable)
    }
    def fromEdtEc = new SwingExecutionContext(createEdtEc)
  }
}
