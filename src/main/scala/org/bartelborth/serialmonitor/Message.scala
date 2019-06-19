package org.bartelborth.serialmonitor

import cats.effect._
import com.fazecast.jSerialComm.SerialPort

// DEMO: MESSAGE
sealed trait Message
case class AddPort(port: SerialPort)                    extends Message
case class RemovePort(id: PortId)                       extends Message
case object Scan                                        extends Message
case object ToggleScanner                               extends Message
case class ShouldScan(yes: Boolean)                     extends Message
case class SetScanner(value: Fiber[IO, Unit])           extends Message
case object UnsetScanner                                extends Message
case class SelectPort(id: PortId)                       extends Message
case object DeselectPort                                extends Message
case class Connect(port: SerialPort)                    extends Message
case class Disconnecting(id: PortId)                    extends Message
case class Disconnect(id: PortId)                       extends Message
case class IngestData(id: PortId, bytes: Array[Byte])   extends Message
case class SetIngesting(id: PortId, f: Fiber[IO, Unit]) extends Message
case class UpdateConfiguration(conf: PortConfiguration) extends Message

object Message {
  val update: UI.Dispatcher => UI.Updater = dispatch =>
    model => {
      case AddPort(p)                => IO(Model.addAvailablePort(p)(model))
      case RemovePort(id)            => IO(Model.removeAvailablePort(id)(model))
      case ShouldScan(yes)           => IO(Model.scanning.set(yes)(model))
      case SetScanner(scanner)       => IO(Model.scanner.set(Some(scanner))(model))
      case UnsetScanner              => IO(Model.scanner.set(None)(model))
      case SelectPort(id)            => IO(Model.selectedId.set(Some(id))(model))
      case DeselectPort              => IO(Model.selectedId.set(None)(model))
      case Connect(p)                => IO(Model.connectPort(p)(model))
      case Disconnecting(id)         => IO(Model.setDisconnecting(id)(model))
      case Disconnect(id)            => IO(Model.disconnectPort(id)(model))
      case UpdateConfiguration(conf) => IO(Model.portConfiguration.set(conf)(model))
      case IngestData(id, bs)        => IO(Model.ingestData(id, bs)(model))
      case SetIngesting(id, f)       => IO(Model.setIngesting(id, f)(model))
      case ToggleScanner             => dispatch(ShouldScan(!model.scanning)).map(_ => model)
      case Scan                      => Model.scanAvailablePorts(p => dispatch(AddPort(p)), id => dispatch(RemovePort(id)))(model)
  }
}
