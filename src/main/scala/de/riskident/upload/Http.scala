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
import zio.stream.{Stream, ZStream}
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
    httpRequest <- HttpDownloader.request
  } yield new Downloader {
    val download =
      (httpRequest.send(): Task[Response[Stream[Throwable, Byte]]])
        .bimap(throwable(s"httpRequest.send ${cfg.downloadUrl}"), r => r.code -> r.body)
  }
}

@accessible
trait HttpDownloader {
  def request: UIO[RequestT[Identity, Stream[Throwable, Byte], Stream[Throwable, Byte]]]
}

object HttpDownloader {
  def make(cfg: AppCfg) = new HttpDownloader {
    val request = IO.succeed {
      basicRequest
        .get(uri"${cfg.downloadUrl}/${cfg.downloadLines}")
        .response(ResponseAsStream[Stream[Throwable, Byte], Stream[Throwable, Byte]]())
        .readTimeout(cfg.requestTimeout)
    }
  }
}

@accessible
trait HttpUploader {
  def request(lines: Long): UIO[RequestT[Identity, Either[String, String], Nothing]]
}

object HttpUploader {
  def make(cfg: AppCfg) =
    new HttpUploader {
      def request(lines: Long) = IO.succeed {
        basicRequest
          .contentType(MediaType.TextCsv)
          .put(uri"${cfg.uploadUrl}/$lines")
      }
    }
}

trait HttpErr[+A] {
  def throwable(message: String)(e: Throwable): A
}

object HttpErr extends Constructors[HttpErr] {
  def throwable(message: String)(e: Throwable) =
    Capture[HttpErr](_.throwable(message)(e))

  val asThrowable = new AsThrowable {}

  trait AsThrowable extends HttpErr[Throwable] {
    def throwable(message: String)(e: Throwable) = new RuntimeException(s"$message: ${e.getMessage}")
  }


  trait AsString extends HttpErr[String] {
    def throwable(message: String)(e: Throwable) = s"$message: ${e.getMessage}"
  }

}
