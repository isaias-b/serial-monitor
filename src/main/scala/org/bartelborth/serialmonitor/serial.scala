package org.bartelborth.serialmonitor

import java.io.{InputStream, OutputStream}

import cats.effect.IO
import com.fazecast.jSerialComm.SerialPort

object serial {
  implicit class RichSerialPort(val p: SerialPort) extends AnyVal {
    def systemPortName: String         = p.getSystemPortName
    def descriptivePortName: String    = p.getDescriptivePortName
    def name: String                   = s"$systemPortName ($descriptivePortName)"
    def id: PortId                     = PortId(p.systemPortName)
    def inputStream: IO[InputStream]   = IO(p.getInputStream)
    def outputStream: IO[OutputStream] = IO(p.getOutputStream)
  }
}
