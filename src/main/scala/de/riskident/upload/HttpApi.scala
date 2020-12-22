package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import de.riskident.upload.HttpErr.throwable
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.httpclient.zio.{BlockingTask, HttpClientZioBackend}
import sttp.model.{MediaType, StatusCode}
import zio._
import zio.blocking.Blocking
import zio.macros.accessible
import zio.stream.{Stream, ZStream}

@accessible
trait DownloadApi {
  def download: IO[Capture[HttpErr], (StatusCode, Stream[Throwable, Byte])]
}

object DownloadApi {
  val make = for {
    implicit0(asyncBackend: SttpBackend[Task, Stream[Throwable, Byte], NothingT]) <- Sttp.asyncBackend
    cfg <- ZIO.service[AppCfg]
    downloadReq <- HttpRequests.downloadReq
  } yield new DownloadApi {
    val download =
      downloadReq
        .send()
        .bimap(throwable(s"httpRequest.send ${cfg.downloadUrl}"), r => r.code -> r.body)
  }
}

@accessible
trait UploadApi {
  def upload(lines: Int, bytes: Stream[Throwable, Byte]): IO[Capture[HttpErr], (StatusCode, Either[String, String])]
}

object UploadApi {
  val make = for {
    implicit0(blockingBackend: SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]) <- Sttp.noContentTypeCharsetBackend
    env <- ZIO.environment[Blocking with Has[HttpRequests]]
  } yield new UploadApi {
    def upload(lines: Int, bytes: Stream[Throwable, Byte]) =
      for {
        uploadReq <- HttpRequests.uploadReq(lines) provide env
        res <- uploadReq
          .streamBody(bytes)
          .send()
          .bimap(throwable("uploadRequest.send"), r => r.code -> r.body)
          .provide(env)
      } yield res
  }
}

@accessible
trait HttpRequests {
  def downloadReq: UIO[RequestT[Identity, Stream[Throwable, Byte], Stream[Throwable, Byte]]]

  def uploadReq(lines: Int): UIO[RequestT[Identity, Either[String, String], Nothing]]
}

object HttpRequests {
  def make(cfg: AppCfg) =
    new HttpRequests {
      val downloadReq = IO.succeed {
        val lines = cfg.downloadLines
        basicRequest
          .get(uri"${cfg.downloadUrl}/$lines")
          .response(ResponseAsStream[Stream[Throwable, Byte], Stream[Throwable, Byte]]())
          .readTimeout(cfg.requestTimeout)
      }

      def uploadReq(lines: Int) = IO.succeed {
        basicRequest
          .put(uri"${cfg.uploadUrl}/$lines")
          .contentType(MediaType.TextCsv)
          .readTimeout(cfg.requestTimeout)
      }
    }
}

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
