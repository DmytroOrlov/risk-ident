package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import cats.syntax.show._
import de.riskident.upload.HttpErr.throwable
import de.riskident.upload.UploadErr.{downloadMoreLines, failedUpload}
import sttp.client.httpclient.zio.BlockingTask
import sttp.client.{NothingT, SttpBackend}
import sttp.model.StatusCode
import zio._
import zio.blocking.Blocking
import zio.macros.accessible
import zio.stream.{Stream, ZStream, ZTransducer}

@accessible
trait UploaderLogic {
  def downloadUpload: IO[Capture[UploadErr with HttpErr], Unit]
}

object UploaderLogic {
  val destLineTransducer: ZTransducer[Any, Nothing, SourceLine, DestLine] =
    ZTransducer[Any, Nothing, SourceLine, DestLine] {
      def destLines(leftovers: Chunk[SourceLine]) =
        if (leftovers.isEmpty) Chunk.empty
        else
          Chunk.fromIterable({
            /*
              leftovers.groupBy(_.produktId).values.flatMap { articles =>
                val sum = articles.map(_.stock).sum
                if (sum == 0) Iterable.empty
                else Iterable({
                  val s = articles.filterNot(_.stock == 0).minBy(_.price)
                  DestLine(s.produktId, s.name, s.description, s.price, sum)
                })
              }
            */
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
    env <- ZIO.environment[Has[Uploader] with Has[Downloader] with Blocking]
  } yield new UploaderLogic {
    def downloadUpload =
      (for {
        (code, bytes) <- Downloader.download
        _ <- IO.fail(downloadMoreLines(code))
          .when(!code.isSuccess)
        destLineStream = bytes
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
        (code, body) <- Uploader.upload(byteStream)
        _ <- IO.fail(failedUpload(code, body.toString))
          .when(!code.isSuccess)
      } yield ()) provide env
  }
}

trait UploadErr[+A] {
  def downloadMoreLines(code: StatusCode): A

  def failedUpload(code: StatusCode, body: String): A

  def message(message: String): A
}

object UploadErr extends Constructors[UploadErr] {
  def message(message: String) =
    Capture[UploadErr](_.message(message))

  def downloadMoreLines(code: StatusCode) =
    Capture[UploadErr](_.downloadMoreLines(code))

  def failedUpload(code: StatusCode, body: String) =
    Capture[UploadErr](_.failedUpload(code, body))

  trait AsThrowable extends UploadErr[Throwable] {
    def downloadMoreLines(code: StatusCode) =
      new RuntimeException(s"not all entries could be processed, download more lines, http code: $code")

    def failedUpload(code: StatusCode, body: String) =
      new RuntimeException(s"failed upload: code: $code, body: $body")

    def message(message: String) = new RuntimeException(message)
  }

  trait AsString extends UploadErr[String] {
    def downloadMoreLines(code: StatusCode) =
      (s"not all entries could be processed, download more lines, http code: $code")

    def failedUpload(code: StatusCode, body: String) =
      (s"failed upload: code: $code, body: $body")

    def message(message: String) = (message)
  }

}
