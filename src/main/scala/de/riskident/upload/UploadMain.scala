package de.riskident.upload

import distage.{Tag, _}
import zio._
import zio.console._

object UploadMain extends App {
  def run(args: List[String]) = {
    val program = for {
      _ <- putStrLn("123")
    } yield ()

    def provideHas[R: HasConstructor, A: Tag](fn: R => A): Functoid[A] =
      HasConstructor[R].map(fn)

    val definition = new ModuleDef {
      make[UIO[Unit]].from(provideHas(program.provide))
    }

    Injector[Task]()
      .produceGet[UIO[Unit]](definition)
      .useEffect
      .exitCode
  }
}
