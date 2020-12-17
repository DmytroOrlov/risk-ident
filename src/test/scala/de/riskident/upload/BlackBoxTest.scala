package de.riskident.upload

import de.riskident.upload.fixtures.{TestDocker, TestDockerSvc}
import distage._
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{EitherValues, OptionValues}
import sttp.client._
import sttp.model.StatusCode
import zio._
import zio.blocking.Blocking
import zio.stream._

abstract class BlackBoxTest extends DistageBIOEnvSpecScalatest[ZIO] with OptionValues with EitherValues with TypeCheckedTripleEquals {
  "UploaderLogic" should {
    "successfully download and upload all entries" in {
      (for {
        _ <- UploaderLogic.downloadUpload
      } yield ())
        .mapError(_ continue new UploadErr.AsString with HttpErr.AsString {})
    }
  }
}

final class DummyBlackBoxTest extends BlackBoxTest {
  def streamReduce(a: String, b: String): String = a + b

  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[Downloader].fromHas(for {
        env <- ZIO.environment[Blocking]
        res = Stream.fromResource("200.csv") provide env
      } yield new Downloader {
        def download = IO.succeed(StatusCode.Ok -> res)
      })
      make[Uploader].fromHas(for {
        env <- ZIO.environment[Blocking]
        orig <- Stream.fromResource("200-resp.csv").aggregate(ZTransducer.utf8Decode).run(Sink.foldLeft("")(streamReduce)) provide env
      } yield new Uploader {
        def upload(bytes: Stream[Throwable, Byte]) =
          (for {
            res <- bytes.aggregate(ZTransducer.utf8Decode).run(Sink.foldLeft("")(streamReduce))
            _ <- IO {
              assert(res === orig)
            }
          } yield StatusCode.Ok -> Right("ok"))
            .mapError(HttpErr.throwable("res to orig"))
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
