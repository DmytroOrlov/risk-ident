package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import sttp.client._
import sttp.client.asynchttpclient.ziostreams.AsyncHttpClientZioStreamsBackend
import zio.macros.accessible
import zio.{IO, Task, UIO}

@accessible
trait Sttp {
  def backend: UIO[SttpBackend[Task, S, NothingT]]
}

object Sttp {
  val make =
    AsyncHttpClientZioStreamsBackend.managed().map { impl =>
      new Sttp {
        val backend = IO.succeed(impl)
      }
    }
}

@accessible
trait Http {
  def request: UIO[RequestT[Identity, S, S]]
}

object Http {
  def make(cfg: AppCfg) = new Http {
    val request = IO.succeed {
      basicRequest
        .get(uri"${cfg.url}/${cfg.downloadLines}")
        .response(ResponseAsStream[S, S]())
        .readTimeout(cfg.requestTimeout)
    }
  }
}

trait HttpErr[+A] {
  def throwable(message: String)(e: Throwable): A

  def message(message: String): A
}

object HttpErr extends Constructors[HttpErr] {
  def throwable(message: String)(e: Throwable) =
    Capture[HttpErr](_.throwable(message)(e))

  def message(message: String) =
    Capture[HttpErr](_.message(message))

  val asThrowable = new AsThrowable {}

  trait AsThrowable extends HttpErr[Throwable] {
    def throwable(message: String)(e: Throwable) = new RuntimeException(s"$message: ${e.getMessage}")

    def message(message: String) = new RuntimeException(message)
  }


  trait AsString extends HttpErr[String] {
    def throwable(message: String)(e: Throwable) = s"$message: ${e.getMessage}"

    def message(message: String) = message
  }

}
