package de.riskident.upload

import zio._
import zio.console._

object UploadMain extends App {
  def run(args: List[String]) = {
    val program = (for {
      _ <- putStrLn("123")
      _ <- IO.fail("err")
    } yield ())
      .exitCode

    program
  }
}
