package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import de.riskident.upload.HttpErr._
import de.riskident.upload.UploadErr.downloadMoreLines
import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import sttp.client.{NothingT, Response, SttpBackend}
import sttp.model.StatusCode
import zio._
import zio.stream.{Stream, ZSink, ZTransducer}

import java.net.URI
import scala.concurrent.duration.Duration

object UploadMain extends App {
  val program = for {
    implicit0(sttpBackend: SttpBackend[Task, Stream[Throwable, Byte], NothingT]) <- Sttp.backend
    httpRequest <- Http.request
    (code, bytes) <- (httpRequest.send(): Task[Response[Stream[Throwable, Byte]]]).bimap(throwable("httpRequest.send"), r => r.code -> r.body)
    _ <- IO.fail(downloadMoreLines(code))
      .when(!code.isSuccess)
    _ <- bytes
      .aggregate(ZTransducer.utf8Decode >>> ZTransducer.splitLines)
      .drop(1)
      .foreach { s =>
        UIO(println(s">>> $s"))
      }.mapError(throwable("splitLines.foreach"))
  } yield ()

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
    url: URI,
    requestTimeout: Duration,
)

trait UploadErr[+A] {
  def downloadMoreLines(code: StatusCode): A

  def message(message: String): A
}

object UploadErr extends Constructors[UploadErr] {
  def message(message: String) =
    Capture[UploadErr](_.message(message))

  def downloadMoreLines(code: StatusCode) =
    Capture[UploadErr](_.downloadMoreLines(code))

  trait AsThrowable extends UploadErr[Throwable] {
    def downloadMoreLines(code: StatusCode) =
      new RuntimeException(s"not all entries could be processed, download more lines, http code: $code")

    def message(message: String) = new RuntimeException(message)
  }

}