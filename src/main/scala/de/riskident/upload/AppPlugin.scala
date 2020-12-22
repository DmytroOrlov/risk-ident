package de.riskident.upload

import com.typesafe.config.ConfigFactory
import de.riskident.upload.AppMain.program
import distage.config.ConfigModuleDef
import distage.{HasConstructor, ProviderMagnet, Tag}
import izumi.distage.config.AppConfigModule
import izumi.distage.effect.modules.ZIODIEffectModule
import izumi.distage.plugins.PluginDef
import zio._

object AppPlugin extends PluginDef with ConfigModuleDef with ZIODIEffectModule {
  def provideHas[R: HasConstructor, A: Tag](fn: R => A): ProviderMagnet[A] =
    HasConstructor[R].map(fn)

  include(AppConfigModule(ConfigFactory.defaultApplication()))

  make[Sttp].fromHas(Sttp.make)

  make[HttpDownloader].from(HttpDownloader.make _)
  make[HttpUploader].from(HttpUploader.make _)

  make[Downloader].fromHas(Downloader.make)
  make[Uploader].fromHas(Uploader.make)
  make[AppLogic].fromHas(AppLogic.make)

  makeConfig[AppCfg]("app")
  make[Task[Unit]].from(provideHas(
    program
      .mapError(_ continue new HttpErr.AsThrowable with UploadErr.AsThrowable {})
      .provide
  ))
}