package de.riskident.upload

import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import zio._

import java.net.URI
import scala.concurrent.duration.Duration

object UploadMain extends App {
  val program = Uploader.upload

  def run(args: List[String]) = {
    val pluginConfig = PluginConfig.cached(
      packagesEnabled = Seq(
        "de.riskident.upload",
      )
    )
    val appModules = PluginLoader().load(pluginConfig)

    Injector()
      .produceGetF[Task, Task[Unit]](appModules.merge)
      .useEffect
      .exitCode
  }
}

case class AppCfg(
    downloadLines: Int,
    downloadUrl: URI,
    uploadUrl: URI,
    requestTimeout: Duration,
)
