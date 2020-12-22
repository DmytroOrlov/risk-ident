package de.riskident.upload

import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import zio._

import java.net.URI
import scala.concurrent.duration.Duration

object AppMain extends App {
  val program = AppLogic.downloadUpload

  def run(args: List[String]) = {
    val pluginConfig = PluginConfig.cached(
      packagesEnabled = Seq(
        "de.riskident.upload",
      )
    )
    val appModules = PluginLoader().load(pluginConfig)

    val app = Injector()
      .produceGetF[Task, Task[Unit]](appModules.merge)
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
