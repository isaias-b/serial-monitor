package org.bartelborth.serialmonitor

import java.awt.{BorderLayout, Color, Dimension, Graphics, Graphics2D}

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import javax.swing._
import org.bartelborth.BuildInfo
import org.bartelborth.serialmonitor.serial._
import org.bartelborth.serialmonitor.swing._

import scala.util.Try

// DEMO: CONNECT
object ConnectButton {
  def apply(ui: UI): IO[JButton] = ui.init { init =>
    for {
      button <- IO(new JButton("Connect"))
      _      <- IO(button.setEnabled(init.selectedId.exists(Model.isConnectableAt(_)(init))))
      _ <- button.addActionListenerIO {
            ui.read { m =>
              Model
                .selectedPort(m)
                .map(Connect.apply)
                .traverse_(ui.publish)
            }
          }
      _ <- ui.subscribe(m => {
            case DeselectPort   => IO(button.setEnabled(false))
            case Connect(_)     => IO(button.setEnabled(false))
            case Disconnect(_)  => IO(button.setEnabled(true))
            case SelectPort(id) => IO(button.setEnabled(!m.connectedPorts.contains(id)))
          })
    } yield button
  }
}

object DisconnectButton {
  def apply(ui: UI): IO[JButton] = ui.init { init =>
    for {
      button <- IO(new JButton("Disconnect"))
      _      <- IO(button.setEnabled(init.selectedId.exists(Model.isDisconnectableAt(_)(init))))
      _ <- button.addActionListenerIO(
            ui.read { m =>
              Model
                .selectedPort(m)
                .map(_.id)
                .map(Disconnecting.apply)
                .traverse(ui.publish) *> IO.unit
            }
          )
      _ <- ui.subscribe(m => {
            case DeselectPort     => IO(button.setEnabled(false))
            case Connect(_)       => IO(button.setEnabled(true))
            case Disconnect(_)    => IO(button.setEnabled(false))
            case Disconnecting(_) => IO(button.setEnabled(false))
            case SelectPort(id)   => IO(button.setEnabled(m.connectedPorts.contains(id)))
          })
    } yield button
  }
}

object DataModeRadioButtons {
  def apply(ui: UI): IO[List[JRadioButton]] =
    for {
      text       <- IO(new JRadioButton("Text"))
      binary     <- IO(new JRadioButton("Binary"))
      buttonsMap = Map(DataMode.Text -> text, DataMode.Binary -> binary)
      buttons    = buttonsMap.values.toList
      group      <- IO(new ButtonGroup())
      _          <- buttons.traverse(b => IO(group.add(b)))
      _          <- IO(text.setSelected(true))
      _ <- buttonsMap.toList.traverse {
            case (mode, button) =>
              button.addActionListenerIO {
                ui.read { model =>
                  ui.publish(UpdateConfiguration(PortConfiguration.dataMode.set(mode)(model.portConfiguration)))
                }
              }
          }
      _ <- ui.subscribe(m => {
            case DeselectPort => buttons.traverse(b => IO(b.setEnabled(true))) *> IO.unit
            case Connect(_)   => buttons.traverse(b => IO(b.setEnabled(false))) *> IO.unit
            case Disconnect(_) =>
              for {
                currentMode <- IO(Some(Model.portConfiguration.get(m)))
                _           <- IO(println(s"currentMode: $currentMode"))
                _ <- buttonsMap.toList.traverse {
                      case (mode, b) =>
                        for {
                          _ <- IO(b.setEnabled(true))
                          _ <- IO(b.setSelected(currentMode.contains(mode)))
                        } yield ()
                    }
              } yield ()

            case SelectPort(id) =>
              for {
                currentMode <- IO(
                                Model
                                  .portConnectionAt(id)
                                  .composeLens(PortConnection.configuration)
                                  .composeLens(PortConfiguration.dataMode)
                                  .getOption(m)
                              )
                _ <- buttonsMap.toList.traverse {
                      case (mode, b) =>
                        for {
                          _ <- IO(b.setEnabled(!m.connectedPorts.contains(id)))
                          _ <- IO(b.setSelected(currentMode.contains(mode)))
                        } yield ()
                    }
              } yield ()
          })
    } yield buttons

}

object BufferSizeSpinner {
  def apply(ui: UI): IO[JSpinner] = ui.init { init =>
    for {
      model   <- IO(new SpinnerNumberModel(init.portConfiguration.bufferSize, 1, 4096, 1))
      spinner <- IO(new JSpinner(model))
      _ <- ui.subscribe(m => {
            case DeselectPort   => IO(spinner.setEnabled(true))
            case Connect(_)     => IO(spinner.setEnabled(false))
            case Disconnect(_)  => IO(spinner.setEnabled(true))
            case SelectPort(id) => IO(spinner.setEnabled(!m.connectedPorts.contains(id)))
          })
      _ <- spinner.addChangeListenerIO(
            ui.read { m =>
              ui.publish(
                UpdateConfiguration(
                  PortConfiguration.bufferSize
                    .modify(_ => spinner.getValue.asInstanceOf[Int])(m.portConfiguration)
                )
              )
            }
          )
    } yield spinner
  }
}

object ScanButton {
  def apply(ui: UI): IO[JButton] =
    for {
      button <- IO(new JButton("Scan"))
      _      <- IO(button.addActionListener(_ => ui.publish(ToggleScanner).unsafeRunAsyncAndForget()))
      _ <- ui.subscribe(_ => {
            case ShouldScan(running) => IO(button.setText(if (running) "Stop Scan" else "Scan"))
          })
    } yield button
}

object PortsList {
  def apply(ui: UI): IO[JList[String]] =
    for {
      listModel <- IO(new DefaultListModel[String])
      listView  <- IO(new JList[String](listModel))
      _ <- listView.addListSelectionListenerIO { e =>
            if (!e.getValueIsAdjusting) ui.publish {
              Option(listView.getSelectedValue).map(PortId.apply).fold[Message](DeselectPort)(SelectPort.apply)
            } else IO.unit
          }
      _ <- ui.subscribe(_ => {
            case AddPort(p)     => IO(listModel.addElement(p.systemPortName))
            case RemovePort(id) => IO.delay(listModel.removeElement(id.name)) *> IO.unit
          })
    } yield listView
}

object ButtonsPanel {
  def apply(ui: UI): IO[JPanel] =
    for {
      panel            <- IO(new JPanel())
      layout           <- IO(new BoxLayout(panel, BoxLayout.LINE_AXIS))
      _                <- IO(panel.setLayout(layout))
      connectButton    <- ConnectButton(ui)
      bufferSpinner    <- BufferSizeSpinner(ui)
      dataModeButtons  <- DataModeRadioButtons(ui)
      disconnectButton <- DisconnectButton(ui)
      scanButton       <- ScanButton(ui)
      _                <- IO(panel.add(connectButton))
      _                <- IO(panel.add(Box.createRigidArea(new Dimension(5, 0))))
      _                <- IO(panel.add(disconnectButton))
      _                <- IO(panel.add(Box.createHorizontalGlue()))
      _                <- IO(panel.add(new JLabel("Data Mode:")))
      _                <- dataModeButtons.traverse(b => IO(panel.add(b)))
      _                <- IO(panel.add(Box.createRigidArea(new Dimension(5, 0))))
      _                <- IO(panel.add(Box.createHorizontalGlue()))
      _                <- IO(panel.add(new JLabel("Buffer Size:")))
      _                <- IO(panel.add(Box.createRigidArea(new Dimension(5, 0))))
      _                <- IO(panel.add(bufferSpinner))
      _                <- IO(panel.add(Box.createHorizontalGlue()))
      _                <- IO(panel.add(scanButton))
    } yield panel
}

object GraphPanel {
  case class Point(x: Double, y: Double)
  type Interpreter = Array[Byte] => Vector[Int]
  val interpretBinary: Interpreter = bs => bs.map(_.toInt).toVector
  val interpretText: Interpreter = bs =>
    bs.map(_.toChar).mkString.split("\t").flatMap(word => Try(word.trim.toInt).toOption).toVector
  val colors = {
    import Color._
    Vector(cyan, blue, red, orange, green, magenta)
  }
  def apply(ui: UI): IO[JPanel] =
    for {
      buffer <- Ref.of[IO, Vector[Vector[Int]]](Vector.empty)
      painter <- IO { (g: Graphics2D, s: Dimension) =>
                  for {
                    _         <- IO(g.fillRect(0, 0, s.width - 1, s.height - 1))
                    height    = s.height - 3
                    width     = s.width - 2
                    _         <- IO(g.setColor(Color.black))
                    _         <- IO(g.drawString(s"w: $width / h: $height", 10, 22))
                    columns   <- buffer.get
                    entries   = columns.length
                    minLength = Try(columns.map(_.length).min).toOption
                    min       = Try(columns.map(_.min).min).toOption.getOrElse(0)
                    max       = Try(columns.map(_.max).max).toOption.getOrElse(1)
                    rows = Try(columns.zipWithIndex.map {
                      case (row, index) =>
                        row.take(minLength.getOrElse(0)).map { value =>
                          val y = Try((1 - (value.toDouble - min) / (max - min)) * height).toOption.getOrElse(0.0)
                          val x = (index.toDouble / entries) * width
                          Point(x, y)
                        }
                    }).toOption
                    _ <- rows.traverse(_.transpose.zip(colors).traverse {
                          case (row, color) =>
                            IO(g.setColor(color)) *> row.traverse(p => IO(g.drawRect(p.x.toInt, p.y.toInt, 1, 1)))
                        })
                  } yield ()
                }
      panel <- IO(new JPanel {
                override def paintComponent(g: Graphics): Unit =
                  painter(g.asInstanceOf[Graphics2D], getSize()).unsafeRunAsyncAndForget()
              })
      _ <- ui.subscribe(m => {
            case IngestData(id, bytes) if m.selectedId.contains(id) =>
              for {
                _ <- IO(panel.repaint())
                bufferSize = Model
                  .configurationAt(id)
                  .composeLens(PortConfiguration.bufferSize)
                  .getOption(m)
                _ <- bufferSize.traverse(
                      bs => buffer.update(values => (values :+ interpretText(bytes)).takeRight(bs))
                    )
              } yield ()
            case SelectPort(_) => IO(panel.repaint())
            case DeselectPort  => IO(panel.repaint())
          })
    } yield panel
}

object DataLogPanel {
  type Interpreter = Vector[Array[Byte]] => String
  val interpretBinary: Interpreter = b => b.map(a => a.mkString(" ") + "\n").mkString
  val interpretText: Interpreter   = v => v.map(bs => bs.map(_.toChar).mkString).mkString

  def apply(ui: UI): IO[JPanel] =
    for {
      panel      <- IO(new JPanel(new BorderLayout()))
      _          <- IO(panel.setPreferredSize(new Dimension(300, 100)))
      textArea   <- IO(new JTextArea)
      _          <- IO(textArea.setEnabled(false))
      _          <- IO(textArea.setEditable(false))
      scrollPane <- IO(new JScrollPane(textArea))
      _          <- IO(panel.add(scrollPane, BorderLayout.CENTER))
      _ <- ui.subscribe(m => {
            case DeselectPort                                   => IO(textArea.setText(""))
            case SelectPort(id)                                 => setText(textArea, m, id)
            case IngestData(id, _) if m.selectedId.contains(id) => setText(textArea, m, id)
          })
    } yield panel
  private def setText(textArea: JTextArea, m: Model, id: PortId): IO[Unit] =
    for {
      currentMode <- IO(
                      Model
                        .configurationAt(id)
                        .composeLens(PortConfiguration.dataMode)
                        .getOption(m)
                        .getOrElse(m.portConfiguration.dataMode)
                    )
      interpeter = currentMode match {
        case DataMode.Text   => interpretText
        case DataMode.Binary => interpretBinary
      }
      _ <- IO(
            textArea.setText(
              Model
                .portConnectionAt(id)
                .composeLens(PortConnection.buffer)
                .getOption(m)
                .fold("Not Connected")(interpeter)
            )
          )
    } yield ()
}

object DetailPanel {
  def apply(ui: UI): IO[JPanel] =
    for {
      panel        <- IO(new JPanel(new BorderLayout()))
      label        <- IO(new JLabel)
      dataLogPanel <- DataLogPanel(ui)
      graphPanel   <- GraphPanel(ui)
      _            <- IO(panel.add(label, BorderLayout.NORTH))
      _            <- IO(panel.add(dataLogPanel, BorderLayout.EAST))
      _            <- IO(panel.add(graphPanel, BorderLayout.CENTER))
      _ <- ui.subscribe(_ => {
            case DeselectPort   => IO(label.setText("No port selected"))
            case SelectPort(id) => IO(label.setText(s"<html><font size=20>${id.name}</font></html>"))
          })

    } yield panel
}

object MasterPanel {
  def apply(ui: UI): IO[JPanel] =
    for {
      panel     <- IO(new JPanel(new BorderLayout))
      _         <- IO(panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20)))
      portsList <- PortsList(ui)
      _         <- IO(panel.add(new JLabel("<html><font size=20>Ports:</font></html>"), BorderLayout.NORTH))
      _         <- IO(panel.add(new JScrollPane(portsList), BorderLayout.CENTER))
    } yield panel
}

object MainPanel {
  def apply(ui: UI): IO[JPanel] =
    for {
      panel        <- IO(new JPanel(new BorderLayout()))
      _            <- IO(panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)))
      buttonsPanel <- ButtonsPanel(ui)
      selection    <- DetailPanel(ui)
      portsPanel   <- MasterPanel(ui)
      _            <- IO(panel.add(buttonsPanel, BorderLayout.NORTH))
      _            <- IO(panel.add(portsPanel, BorderLayout.WEST))
      _            <- IO(panel.add(selection, BorderLayout.CENTER))
    } yield panel
}

object MainFrame {
  def apply(): IO[JFrame] =
    for {
      frame <- IO(new JFrame(s"Serial Monitor ${BuildInfo.version}"))
      _     <- IO(frame.setSize(800, 600))
    } yield frame
}
