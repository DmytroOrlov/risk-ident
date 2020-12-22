package de.riskident.upload

import de.riskident.upload.AppPlugin.Program
import de.riskident.upload.fixtures.{TestDocker, TestDockerSvc}
import distage._
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues}
import sttp.client._
import sttp.model.StatusCode
import zio._
import zio.blocking.Blocking
import zio.stream._

abstract class BlackBoxTest extends DistageBIOEnvSpecScalatest[ZIO] with Matchers with OptionValues with EitherValues with TypeCheckedTripleEquals {
  "Program" should {
    "successfully download and upload all entries" in { program: Program =>
      for {
        res <- program
        _ <- IO {
          assert(res.right.value.toList === List("Finished reading data, result is correct"))
        }
      } yield ()
    }
  }
}

final class DummyBlackBoxTest extends BlackBoxTest {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[DownloadApi].fromHas(for {
        env <- ZIO.environment[Blocking]
        res = Stream.fromResource("200.csv") provide env
      } yield new DownloadApi {
        def download = IO.succeed(StatusCode.Ok -> res)
      })

      make[UploadApi].fromHas(for {
        env <- ZIO.environment[Blocking]
        path = "200-resp.csv"
        reference <- Stream.fromResource(path).aggregate(ZTransducer.utf8Decode >>> ZTransducer.splitLines).run(Sink.collectAll) provide env
      } yield new UploadApi {
        def upload(bytes: Stream[Throwable, Byte]) =
          (for {
            res <- bytes.aggregate(ZTransducer.utf8Decode >>> ZTransducer.splitLines).run(Sink.collectAll)
            _ <- IO {
              res.toList must contain theSameElementsAs reference.toList
            }
          } yield StatusCode.Ok -> Right("Finished reading data, result is correct"))
            .mapError(HttpErr.throwable(s"compare with $path"))
      })
    }
  )
}

final class DockerBlackBoxTest extends BlackBoxTest {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[AppCfg].fromHas { (service: TestDockerSvc) =>
        for {
          downloadUrl <- Task(uri"http://${service.test.hostV4}:${service.test.port}/articles")
          uploadUrl <- Task(uri"http://${service.test.hostV4}:${service.test.port}/products")
          _ <- {
            import zio.duration._
            ZIO.sleep(5.seconds)
          }
        } yield {
          import scala.concurrent.duration._
          AppCfg(200, downloadUrl.toJavaUri, uploadUrl.toJavaUri, 10.seconds)
        }
      }
    },
    memoizationRoots = Set(
      DIKey.get[TestDocker.Container],
    ),
  )
}

