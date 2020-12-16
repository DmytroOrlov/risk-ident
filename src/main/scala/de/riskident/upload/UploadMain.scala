package de.riskident.upload

import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import sttp.client.{NothingT, SttpBackend}
import zio._
import zio.console._

import java.net.URI
import scala.concurrent.duration.Duration

object UploadMain extends App {
  val program = for {
    implicit0(sttpBackend: SttpBackend[Task, S, NothingT]) <- Sttp.backend
    httpRequest <- Http.request
    _ <- httpRequest.send()
    _ <- Http.request
    cfg <- ZIO.service[AppCfg]
    _ <- putStrLn(cfg.downloadLines.toString)
  } yield ()

  def run(args: List[String]) = {
    val pluginConfig = PluginConfig.cached(
      packagesEnabled = Seq(
        "de.riskident.upload",
      )
    )
    val appModules = PluginLoader().load(pluginConfig)

    Injector()
      .produceGetF[Task, UIO[Unit]](appModules.merge)
      .useEffect
      .exitCode
  }
}

case class AppCfg(
    downloadLines: Int,
    url: URI,
    requestTimeout: Duration,
)
