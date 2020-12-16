package de.riskident.upload

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
