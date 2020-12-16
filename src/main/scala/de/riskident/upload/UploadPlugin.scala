package de.riskident.upload

import com.typesafe.config.ConfigFactory
import de.riskident.upload.UploadMain.program
import distage.config.ConfigModuleDef
import distage.{HasConstructor, ProviderMagnet, Tag}
import izumi.distage.config.AppConfigModule
import izumi.distage.plugins.PluginDef
import zio._
import zio.console._

object UploadPlugin extends PluginDef with ConfigModuleDef {
  def provideHas[R: HasConstructor, A: Tag](fn: R => A): ProviderMagnet[A] =
    HasConstructor[R].map(fn)

  include(AppConfigModule(ConfigFactory.defaultApplication()))

  makeConfig[AppCfg]("app")
  make[Sttp].fromResource(Sttp.make)
  make[Http].from(Http.make _)
  make[Console.Service].fromHas(Console.live)
  make[Task[Unit]].from(
    provideHas(
      program
        .mapError(_ continue new HttpErr.AsThrowable with UploadErr.AsThrowable {})
        .provide
    )
  )
}
