package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import de.riskident.upload.HttpErr.throwable
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.httpclient.zio.{BlockingTask, HttpClientZioBackend}
import sttp.model.{MediaType, StatusCode}
import zio.blocking.Blocking
import zio.macros.accessible
import zio.stream.{Stream, UStream, ZStream}
import zio.{IO, Task, UIO, ZIO}

@accessible
trait Sttp {
  def asyncBackend: UIO[SttpBackend[Task, Stream[Throwable, Byte], NothingT]]

  def noContentTypeCharsetBackend: UIO[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]]
}

object Sttp {
  val make = for {
    async <- AsyncHttpClientZioBackend.managed()
    blocking <- HttpClientZioBackend.managed()
  } yield new Sttp {
    val asyncBackend = IO.succeed(async)
    val noContentTypeCharsetBackend = IO.succeed(blocking)
  }
}

@accessible
trait Downloader {
  def download: IO[Capture[HttpErr], (StatusCode, Stream[Throwable, Byte])]
}

object Downloader {
  val make = for {
    implicit0(sttpBackend: SttpBackend[Task, Stream[Throwable, Byte], NothingT]) <- Sttp.asyncBackend
    cfg <- ZIO.service[AppCfg]
    downloadRequest <- HttpRequests.downloadRequest
  } yield new Downloader {
    val download =
      downloadRequest
        .send()
        .bimap(throwable(s"httpRequest.send ${cfg.downloadUrl}"), r => r.code -> r.body)
  }
}

@accessible
trait Uploader {
  def upload(bytes: Stream[Throwable, Byte]): IO[Capture[HttpErr], (StatusCode, Either[String, String])]
}

object Uploader {
  val make = for {
    implicit0(sttpBackend: SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]) <- Sttp.noContentTypeCharsetBackend
    env <- ZIO.environment[Blocking]
    uploadRequest <- HttpRequests.uploadRequest
  } yield new Uploader {
    def upload(bytes: Stream[Throwable, Byte]) =
      (for {
        res <- uploadRequest
          .streamBody(bytes)
          .send()
          .bimap(throwable("uploadRequest.send"), r => r.code -> r.body)
      } yield res) provide env
  }
}

@accessible
trait HttpRequests {
  def downloadRequest: UIO[RequestT[Identity, Stream[Throwable, Byte], Stream[Throwable, Byte]]]

  def uploadRequest: UIO[RequestT[Identity, Either[String, String], Nothing]]
}

object HttpRequests {
  def make(cfg: AppCfg) =
    new HttpRequests {
      val downloadRequest = IO.succeed {
        basicRequest
          .get(uri"${cfg.downloadUrl}/${cfg.downloadLines}")
          .response(ResponseAsStream[Stream[Throwable, Byte], Stream[Throwable, Byte]]())
          .readTimeout(cfg.requestTimeout)
      }
      val uploadRequest = IO.succeed {
        basicRequest
          .put(uri"${cfg.uploadUrl}/${cfg.downloadLines}")
          .contentType(MediaType.TextCsv)
          .readTimeout(cfg.requestTimeout)
      }
    }
}

trait HttpErr[+A] {
  def throwable(message: String)(e: Throwable): A
}

object HttpErr extends Constructors[HttpErr] {
  def throwable(message: String)(e: Throwable) =
    Capture[HttpErr](_.throwable(message)(e))

  trait AsThrowable extends HttpErr[Throwable] {
    def throwable(message: String)(e: Throwable) =
      new RuntimeException(s"$message: ${e.getMessage}")
  }


  trait AsString extends HttpErr[String] {
    def throwable(message: String)(e: Throwable) =
      s"$message: ${e.getMessage}"
  }

}
