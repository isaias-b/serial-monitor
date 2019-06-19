package org.bartelborth.serialmonitor

import cats.effect._
import cats.implicits._
import com.fazecast.jSerialComm.{SerialPort, SerialPortDataListener, SerialPortEvent}
import org.bartelborth.serialmonitor.serial._

import scala.concurrent.duration._

object PortCommunicator {
  def apply(ui: UI)(implicit t: Timer[IO], cs: ContextShift[IO]): IO[Unit] =
    for {
      _ <- ui.subscribe(m => {
            case Disconnecting(id) =>
              for {
                _ <- Model
                      .portConnectionAt(id)
                      .composeLens(PortConnection.port)
                      .modifyF[IO](
                        p =>
                          for {
                            _      <- IO(p.removeDataListener())
                            closed <- IO(p.closePort())
                            _      <- IO(println(s"port $id closed: $closed!"))
                          } yield p
                      )(m)
                _ <- Model
                      .portConnectionAt(id)
                      .composeLens(PortConnection.ingester)
                      .composePrism(monocle.std.option.some)
                      .getOption(m)
                      .traverse(_.cancel)
                _ <- IO(println(s"fiber $id canceled!"))
                _ <- ui.publish(Disconnect(id))
              } yield ()

            case Connect(port) =>
              (for {
                _ <- IO(println(s"opening connection ${port.systemPortName}"))
                createDataListener <- IO {
                                       new SerialPortDataListener {
                                         override def getListeningEvents: Int =
                                           SerialPort.LISTENING_EVENT_DATA_RECEIVED

                                         override def serialEvent(event: SerialPortEvent): Unit =
                                           (Option(event.getReceivedData)
                                             .map(IngestData(port.id, _))
                                             .traverse(ui.publish)
                                             *> IO.unit).unsafeRunAsyncAndForget()
                                       }
                                     }
                _ <- PortStreams.from(port).use { ps =>
                      for {
                        _       <- IO(ps.port.removeDataListener())
                        success <- IO(ps.port.addDataListener(createDataListener))
                        _       <- if (success) IO.never else IO.unit
                      } yield ()
                    }
                _ <- IO(println(s"closing connection ${port.systemPortName}"))
              } yield ()).start.map(SetIngesting(port.id, _)).map(ui.publish) *> IO.unit
          })
    } yield ()

}

object PortScanner {
  private def continuousPortScan(ui: UI)(implicit t: Timer[IO]): IO[Unit] =
    for {
      _ <- ui.publish(Scan)
      _ <- IO.sleep(200.millis)
      _ <- continuousPortScan(ui)
    } yield ()

  def apply(ui: UI)(implicit cs: ContextShift[IO], t: Timer[IO]): IO[Unit] =
    for {
      _ <- ui.subscribe(m => {
            case ShouldScan(scanning) if scanning => continuousPortScan(ui).start.map(SetScanner).flatMap(ui.publish)
            case ShouldScan(_)                    => m.scanner.get.cancel *> ui.publish(UnsetScanner)
          })
    } yield ()
}
