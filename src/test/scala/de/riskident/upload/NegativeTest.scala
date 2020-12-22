package de.riskident.upload

import distage._
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues}
import sttp.model.StatusCode
import zio._
import zio.stream._

final class NegativeTest extends DistageBIOEnvSpecScalatest[ZIO] with Matchers with OptionValues with EitherValues with TypeCheckedTripleEquals {
  "AppLogic.processAndUpload" should {
    "fail if download status is not successful" in {
      for {
        res <- AppLogic.processAndUpload(StatusCode.BadRequest, Stream.empty)
          .mapError(_ continue new AppErr[String] with HttpErr[String] {
            override def downloadMoreLines(code: StatusCode): String = s"downloadMoreLines $code"

            override def failedUpload(code: StatusCode, body: String): String = ???

            override def message(message: String): String = ???

            override def throwable(message: String)(e: Throwable): String = ???
          })
          .either
        _ <- IO {
          assert(res.left.value === "downloadMoreLines 400")
        }
      } yield ()
    }
    "fail if upload status is not successful" in {
      for {
        res <- AppLogic.processAndUpload(StatusCode.Ok, Stream.empty)
          .mapError(_ continue new HttpErr[String] with AppErr[String] {
            override def failedUpload(code: StatusCode, body: String): String = s"failedUpload $code $body"

            override def throwable(message: String)(e: Throwable): String = ???

            override def downloadMoreLines(code: StatusCode): String = ???

            override def message(message: String): String = ???
          })
          .either
        _ <- IO {
          assert(res.left.value === "failedUpload 500 ok")
        }
      } yield ()
    }
  }

  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[UploadApi].from(new UploadApi {
        def upload(lines: Int, bytes: Stream[Throwable, Byte]) =
          for {
            _ <- zio.IO.unit
            res = StatusCode.InternalServerError -> Right("ok")
          } yield res
      })
    }
  )
}
