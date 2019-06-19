package org.bartelborth.serialmonitor

import java.io.{InputStream, OutputStream}

import cats.effect._
import com.fazecast.jSerialComm.SerialPort
import org.bartelborth.serialmonitor.serial._

import scala.concurrent.duration._

case class PortStreams private (port: SerialPort, is: InputStream, os: OutputStream)
object PortStreams {
  def from(p: SerialPort)(implicit t: Timer[IO]): Resource[IO, PortStreams] = {
    def acquirePort: IO[SerialPort] = {
      def go(retries: Int): IO[SerialPort] =
        for {
          opened <- IO(p.openPort(0))
          port <- if (!opened) for {
                   _ <- IO(println(s"unable to open port, number of retries is now $retries"))
                   _ <- if (retries <= 1) IO.raiseError(new Exception(s"""unable to open port "${p.systemPortName}""""))
                       else IO.unit
                   _   <- IO.sleep(1.second)
                   res <- go(retries - 1)
                 } yield res
                 else IO.pure(p)
        } yield port
      go(5)
    }
    def releasePort(p: SerialPort): IO[Unit] = {
      def go(retries: Int): IO[Unit] =
        for {
          closed <- IO(p.closePort())
          _ = if (!closed) for {
            _ <- if (retries <= 1) IO.raiseError(new Exception(s"""unable to close port "${p.systemPortName}""""))
                else IO.unit
            _   <- IO.sleep(1.second)
            res <- go(retries - 1)
          } yield res
          else IO.unit
        } yield ()
      go(5)
    }

    val portResource: Resource[IO, SerialPort] = Resource.make[IO, SerialPort](acquirePort)(releasePort)
    for {
      port <- portResource
      is   <- Resource.fromAutoCloseable(port.inputStream)
      os   <- Resource.fromAutoCloseable(port.outputStream)
    } yield PortStreams(port, is, os)
  }
}
