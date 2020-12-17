package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import cats.syntax.show._
import de.riskident.upload.HttpErr._
import de.riskident.upload.UploadErr.{downloadMoreLines, failedUpload}
import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import sttp.client.httpclient.zio.BlockingTask
import sttp.client.{NothingT, Response, SttpBackend}
import sttp.model.{MediaType, StatusCode}
import zio._
import zio.blocking.Blocking
import zio.nio.channels.AsynchronousFileChannel
import zio.nio.file.Files
import zio.stream.{Stream, ZStream, ZTransducer}

import java.net.URI
import java.nio.file.{Path, StandardOpenOption}
import scala.concurrent.duration.Duration

object UploadMain extends App {
  val destLineTransducer: ZTransducer[Any, Nothing, SourceLine, DestLine] =
    ZTransducer[Any, Nothing, SourceLine, DestLine] {
      def destLines(leftovers: Chunk[SourceLine]) =
        if (leftovers.isEmpty) Chunk.empty
        else
          Chunk.fromIterable(leftovers.groupBy(_.produktId).values.flatMap { articles =>
            val sum = articles.map(_.stock).sum
            if (sum == 0) Iterable.empty
            else Iterable({
              val res = articles.filterNot(_.stock == 0).minBy(_.price)
              DestLine(res.produktId, res.name, res.description, res.price, sum)
            })
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

  val program = for {
    env <- ZIO.environment[Blocking]
    httpRequest <- HttpDownloader.request
    (code, bytes) <- for {
      implicit0(sttpBackend: SttpBackend[Task, Stream[Throwable, Byte], NothingT]) <- Sttp.asyncBackend
      res <- (httpRequest.send(): Task[Response[Stream[Throwable, Byte]]]).bimap(throwable("httpRequest.send"), r => r.code -> r.body)
    } yield res
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
    _ <- (for {
      tempFile <- Files.createTempFile(prefix = None, fileAttributes = Iterable.empty)
        .mapError(throwable("createTempFile"))
      uploadLines <- AsynchronousFileChannel.open(
        tempFile,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
      ).mapError(throwable(s"write $tempFile")).use { channel =>
        var filePosition = 0
        var lines = -1
        (Stream.succeed("produktId|name|beschreibung|preis|summeBestand") ++
          destLineStream.map(d => s"\n${d.show}"))
          .foreach { s =>
            lines = lines + 1
            channel.writeChunk(Chunk.fromArray(s.getBytes), filePosition)
              .map(written => filePosition += written)
          }.bimap(throwable("destLineStream.foreach"), _ => lines)
      }
    } yield tempFile -> uploadLines)
      .toManaged { case (p, _) => Files.delete(p).ignore }
      .use { case (tempFile, uploadLines) =>
        for {
          implicit0(sttpBackend: SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]) <- Sttp.noContentTypeCharsetBackend
          uploadRequest <- HttpUploader.request(uploadLines)
          (code, body) <- uploadRequest
            .streamBody(Stream.fromFile(Path.of(tempFile.toString)).provide(env))
            .contentType(MediaType.TextCsv.noCharset)
            .send()
            .bimap(throwable("uploadRequest.send"), r => r.code -> r.body)
          _ <- IO.fail(failedUpload(code, body.toString))
            .when(!code.isSuccess)
        } yield ()
      }
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
    downloadUrl: URI,
    uploadUrl: URI,
    requestTimeout: Duration,
)

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

}