package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import sttp.client._
import sttp.client.asynchttpclient.zio._
import zio.macros.accessible
import zio.stream.Stream
import zio.{IO, Task, UIO}

@accessible
trait Sttp {
  def backend: UIO[SttpBackend[Task, Stream[Throwable, Byte], NothingT]]
}

object Sttp {
  val make =
    AsyncHttpClientZioBackend.managed().map { impl =>
      new Sttp {
        val backend = IO.succeed(impl)
      }
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
  def request(lines: Long): UIO[RequestT[Identity, Either[String, String], Stream[Nothing, String]]]
}

object HttpUploader {
  def make(cfg: AppCfg) =
    new HttpUploader {
      def request(lines: Long) = IO.succeed {
        basicRequest
          .streamBody(Stream.succeed("produktId|name|beschreibung|preis|summeBestand"))
          .contentType("text/csv")
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
