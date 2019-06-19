package org.bartelborth.serialmonitor

import cats.effect.{Fiber, IO}
import cats.instances.list._
import cats.syntax.traverse._
import com.fazecast.jSerialComm.SerialPort
import monocle.Monocle._
import monocle.macros.Lenses
import monocle.std.option
import monocle.{Lens, Optional}
import org.bartelborth.serialmonitor.serial._

// DEMO: MODEL
@Lenses case class PortId(name: String)

@Lenses case class Model(
  availablePorts: Map[PortId, SerialPort] = Map.empty,
  connectedPorts: Map[PortId, PortConnection] = Map.empty,
  scanner: Option[Fiber[IO, Unit]] = None,
  scanning: Boolean = false,
  portConfiguration: PortConfiguration = PortConfiguration(),
  selectedId: Option[PortId] = None
)

@Lenses case class PortConnection(
  port: SerialPort,
  configuration: PortConfiguration,
  buffer: Vector[Array[Byte]],
  ingester: Option[Fiber[IO, Unit]] = None,
  disconnecting: Boolean = false
)

sealed trait DataMode
object DataMode {
  case object Text   extends DataMode
  case object Binary extends DataMode
}

@Lenses case class PortConfiguration(
  bufferSize: Int = 256,
  dataMode: DataMode = DataMode.Text
)

object Model {
  // DEMO: AT
  def availablePortAt(id: PortId): Lens[Model, Option[SerialPort]] =
    availablePorts.composeLens(at(id))
  def portConnectionAt(id: PortId): Optional[Model, PortConnection] =
    connectedPorts.composeLens(at(id)).composePrism(option.some)
  def configurationAt(id: PortId): Optional[Model, PortConfiguration] =
    portConnectionAt(id).composeLens(PortConnection.configuration)

  def addAvailablePort(p: SerialPort): Model => Model = availablePortAt(p.id).set(Some(p))
  def removeAvailablePort(id: PortId): Model => Model = availablePortAt(id).set(None)
  def isConnectableAt(id: PortId): Model => Boolean = m => {
    val connected = portConnectionAt(id).getOption(m).isDefined
    val available = availablePortAt(id).get(m).isDefined
    available && !connected
  }
  def isDisconnectableAt(id: PortId): Model => Boolean = m => {
    val connected = portConnectionAt(id).getOption(m).isDefined
    val selected  = selectedPort(m).map(_.id).contains(id)
    selected && connected
  }
  def scanAvailablePorts(
    announcePortAdded: SerialPort => IO[Unit],
    announcePortRemoved: PortId => IO[Unit]
  ): Model => IO[Model] = {

    val scanPorts: Map[PortId, SerialPort] => IO[Map[PortId, SerialPort]] = currentPorts =>
      for {
        available      <- IO(SerialPort.getCommPorts.toList)
        availablePorts = available.map(p => (p.id, p)).toMap
        addedPorts     = availablePorts.filterNot { case (id, _) => currentPorts.contains(id) }
        _              <- addedPorts.values.toList.traverse(announcePortAdded(_))
        removedPorts   = currentPorts.filterNot { case (id, _) => availablePorts.contains(id) }
        _              <- removedPorts.keys.toList.traverse(announcePortRemoved(_))
      } yield availablePorts
    availablePorts.modifyF(scanPorts)
  }

  def setIngesting(id: PortId, f: Fiber[IO, Unit]): Model => Model =
    portConnectionAt(id).composeLens(PortConnection.ingester).set(Some(f))
  def setDisconnecting(id: PortId): Model => Model =
    (portConnectionAt(id) composeLens PortConnection.disconnecting).set(true)(_)
  def connectPort(p: SerialPort): Model => Model =
    m => connectedPorts.composeLens(at(p.id)).set(Some(PortConnection(p, m.portConfiguration, Vector.empty)))(m)
  def disconnectPort(id: PortId): Model => Model =
    connectedPorts.composeLens(at(id)).set(None)
  def selectedPort: Model => Option[SerialPort] =
    m => m.selectedId.flatMap(id => availablePortAt(id).get(m))
  def selectedConnection: Model => Option[PortConnection] =
    m => m.selectedId.flatMap(id => portConnectionAt(id).getOption(m))
  def ingestData(id: PortId, bytes: Array[Byte]): Model => Model =
    portConnectionAt(id).modify { pc =>
      PortConnection.buffer.modify(b => (b :+ bytes) takeRight pc.configuration.bufferSize)(pc)
    }

}
