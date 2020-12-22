package de.riskident.upload

import de.riskident.upload.AppPlugin.Program
import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import zio._

import java.net.URI
import scala.concurrent.duration.Duration

object AppMain extends App {
  val program = for {
    (code, downloadBytes) <- DownloadApi.download
    res <- AppLogic.processAndUpload(code, downloadBytes)
  } yield res

  def run(args: List[String]) = {
    val pluginConfig = PluginConfig.cached(
      packagesEnabled = Seq(
        "de.riskident.upload",
      )
    )
    val appModules = PluginLoader().load(pluginConfig)

    val app = Injector()
      .produceGetF[Task, Program](appModules.merge)
      .useEffect

    app.exitCode
  }
}

case class AppCfg(
    downloadLines: Int,
    downloadUrl: URI,
    uploadUrl: URI,
    requestTimeout: Duration,
)
