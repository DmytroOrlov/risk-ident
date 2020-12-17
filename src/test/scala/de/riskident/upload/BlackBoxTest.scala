package de.riskident.upload

import de.riskident.upload.fixtures.{TestDocker, TestDockerSvc}
import distage._
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{EitherValues, OptionValues}
import sttp.client._
import zio._

abstract class BlackBoxTest extends DistageBIOEnvSpecScalatest[ZIO] with OptionValues with EitherValues with TypeCheckedTripleEquals {
  "Uploader" should {
    "upload 50" in {
      (for {
        _ <- Uploader.upload
      } yield ())
        .mapError(_ continue new UploadErr.AsString with HttpErr.AsString {})
    }
  }
}

final class DockerBlackBoxTest extends BlackBoxTest {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[AppCfg].fromEffect { (service: TestDockerSvc) =>
        import scala.concurrent.duration._
        for {
          downloadUrl <- Task(uri"http://${service.test.hostV4}:${service.test.port}/articles")
          uploadUrl <- Task(uri"http://${service.test.hostV4}:${service.test.port}/products")
        } yield AppCfg(50, downloadUrl.toJavaUri, uploadUrl.toJavaUri, 10.seconds)
      }
    },
    memoizationRoots = Set(
      DIKey.get[TestDocker.Container],
    ),
  )
}
