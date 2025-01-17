package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import cats.syntax.show._
import de.riskident.upload.HttpErr.throwable
import de.riskident.upload.AppErr.{downloadMoreLines, failedUpload}
import de.riskident.upload.models.{DestLine, SourceLine}
import sttp.client.httpclient.zio.BlockingTask
import sttp.client.{NothingT, SttpBackend}
import sttp.model.StatusCode
import zio._
import zio.blocking.Blocking
import zio.macros.accessible
import zio.stream.{Stream, ZStream, ZTransducer}

@accessible
trait AppLogic {
  def processAndUpload(code: StatusCode, downloadBytes: Stream[Throwable, Byte]): IO[Capture[AppErr with HttpErr], Either[String, Chunk[String]]]
}

object AppLogic {
  val destLineTransducer: ZTransducer[Any, Nothing, SourceLine, DestLine] =
    ZTransducer[Any, Nothing, SourceLine, DestLine] {
      def destLines(leftovers: Chunk[SourceLine]) =
        if (leftovers.isEmpty) Chunk.empty
        else
          Chunk.fromIterable({
            leftovers.foldLeft(List.empty[DestLine]) {
              case (Nil, s) if s.stock != 0 =>
                DestLine(s.produktId, s.name, s.description, s.price, s.stock) :: Nil
              case (a :: acc, s) if a.produktId == s.produktId && s.price < a.price && s.stock != 0 =>
                DestLine(s.produktId, s.name, s.description, s.price, s.stock + a.stockSum) :: acc
              case (a :: acc, s) if a.produktId == s.produktId =>
                a.copy(stockSum = a.stockSum + s.stock) :: acc
              case (a :: acc, s) if s.stock != 0 =>
                DestLine(s.produktId, s.name, s.description, s.price, s.stock) ::
                  a :: acc
              case (acc, _) => acc
            }.reverse
          })

      ZRef.makeManaged[Chunk[SourceLine]](Chunk.empty).map(stateRef => {
        case None =>
          stateRef.getAndSet(Chunk.empty).flatMap { leftovers =>
            ZIO.succeed(destLines(leftovers))
          }

        case Some(ss) =>
          stateRef.modify { leftovers =>
            val concat = leftovers ++ ss

            if (concat.map(_.produktId).distinct.length < 2) (Chunk.empty, concat.materialize)
            else {
              val (newLeftovers, toConvert) = concat.partition(_.produktId == concat.last.produktId)
              (destLines(toConvert), newLeftovers.materialize)
            }
          }
      })
    }

  val make = for {
    env <- ZIO.environment[Has[UploadApi] with Has[DownloadApi] with Blocking]
    cfg <- ZIO.service[AppCfg]
  } yield new AppLogic {
    def processAndUpload(code: StatusCode, downloadBytes: Stream[Throwable, Byte]) =
      (for {
        _ <- IO.fail(downloadMoreLines(code))
          .when(!code.isSuccess)
        destLineStream = downloadBytes
          .aggregate(ZTransducer.utf8Decode >>> ZTransducer.splitLines)
          .drop(1)
          .map {
            _.split("\\|").toList match {
              case id :: produktId :: name :: description :: price :: stock :: _ =>
                SourceLine(
                  id,
                  produktId,
                  name,
                  description,
                  BigDecimal(price),
                  stock.toInt
                )
            }
          }
          .aggregate(destLineTransducer)
        byteStream = (Stream.succeed("produktId|name|beschreibung|preis|summeBestand") ++
          destLineStream.map(d => s"\n${d.show}")).mapConcat(_.getBytes)
        (code, uploadResp) <- UploadApi.upload(cfg.downloadLines, byteStream)
        _ <- IO.fail(failedUpload(code, uploadResp.merge))
          .when(!code.isSuccess)
      } yield uploadResp.map(Chunk.single)) provide env
  }
}

trait AppErr[+A] {
  def downloadMoreLines(code: StatusCode): A

  def failedUpload(code: StatusCode, body: String): A

  def message(message: String): A
}

object AppErr extends Constructors[AppErr] {
  def message(message: String) =
    Capture[AppErr](_.message(message))

  def downloadMoreLines(code: StatusCode) =
    Capture[AppErr](_.downloadMoreLines(code))

  def failedUpload(code: StatusCode, body: String) =
    Capture[AppErr](_.failedUpload(code, body))

  trait AsThrowable extends AppErr[Throwable] {
    def downloadMoreLines(code: StatusCode) =
      new RuntimeException(s"not all entries could be processed, download more lines, http code: $code")

    def failedUpload(code: StatusCode, body: String) =
      new RuntimeException(s"failed upload: code: $code, body: $body")

    def message(message: String) = new RuntimeException(message)
  }

  trait AsString extends AppErr[String] {
    def downloadMoreLines(code: StatusCode) =
      (s"not all entries could be processed, download more lines, http code: $code")

    def failedUpload(code: StatusCode, body: String) =
      (s"failed upload: code: $code, body: $body")

    def message(message: String) = (message)
  }

}
