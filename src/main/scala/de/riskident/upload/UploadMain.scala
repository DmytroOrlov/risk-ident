package de.riskident.upload

import capture.Capture
import capture.Capture.Constructors
import cats.syntax.show._
import de.riskident.upload.HttpErr._
import de.riskident.upload.UploadErr.downloadMoreLines
import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import sttp.client.{NothingT, Response, SttpBackend}
import sttp.model.StatusCode
import zio._
import zio.stream.{Stream, ZTransducer}

import java.net.URI
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
    implicit0(sttpBackend: SttpBackend[Task, Stream[Throwable, Byte], NothingT]) <- Sttp.backend
    httpRequest <- HttpDownloader.request
    (code, bytes) <- (httpRequest.send(): Task[Response[Stream[Throwable, Byte]]]).bimap(throwable("httpRequest.send"), r => r.code -> r.body)
    _ <- IO.fail(downloadMoreLines(code))
      .when(!code.isSuccess)
    _ <- (Stream.succeed("produktId|name|beschreibung|preis|summeBestand") ++
      bytes
        .aggregate(ZTransducer.utf8Decode >>> ZTransducer.splitLines)
        .drop(1)
        .bimap(throwable("splitLines SourceLine"), {
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
        })
        .aggregate(destLineTransducer)
        .map(_.show))
      .foreach { l =>
        UIO(println(l))
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