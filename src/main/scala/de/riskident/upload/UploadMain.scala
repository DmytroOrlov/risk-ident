package de.riskident.upload

import com.typesafe.config.ConfigFactory
import distage.config.ConfigModuleDef
import distage.{Tag, _}
import izumi.distage.config.AppConfigModule
import zio._
import zio.console._

import java.net.URI

object UploadMain extends App {
  def run(args: List[String]) = {
    val program = for {
      cfg <- ZIO.service[AppCfg]
      _ <- putStrLn(cfg.downloadLines.toString)
    } yield ()

    def provideHas[R: HasConstructor, A: Tag](fn: R => A): Functoid[A] =
      HasConstructor[R].map(fn)

    val definition = new ConfigModuleDef {
      makeConfig[AppCfg]("app")
      make[UIO[Unit]].from(provideHas(program.provide))
    }

    val defs = Seq(definition, AppConfigModule(ConfigFactory.defaultApplication()))
    Injector[Task]()
      .produceGet[UIO[Unit]](defs.merge)
      .useEffect
      .exitCode
  }
}

case class AppCfg(downloadLines: URI)
