package org.bartelborth.serialmonitor

import cats.Traverse
import cats.effect.IO
import cats.implicits._

object TraverseTest {
  val op1: String => IO[Seq[String]] = ???
  val op2: String => IO[Unit]        = ???

  val effect: IO[Unit] = for {
    files <- op1("asdf")
    _     <- files.toList.traverse(op2)
  } yield ()

  implicit def seqTraversable: Traverse[Seq] = ???
  val list: List[Int]                        = ???
  val seq: Seq[Int]                          = ???
  val operation: Int => IO[Long]             = ???

  // toTraverseOps
  toTraverseOps(list).traverse(operation)
  seq.traverse(operation)
}
