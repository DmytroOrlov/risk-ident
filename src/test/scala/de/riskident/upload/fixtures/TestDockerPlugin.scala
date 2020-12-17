package de.riskident.upload.fixtures

import buildinfo.BuildInfo.version
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{ContainerConfig, DockerPort}

object TestDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(8080)

  override def config: Config = {
    ContainerConfig(
      image = s"dmytroorlov/provided-test-service:$version",
      ports = Seq(primaryPort),
    )
  }
}

import izumi.distage.docker.Docker.AvailablePort
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.model.definition.Id
import izumi.distage.model.definition.StandardAxis.Env
import izumi.distage.plugins.PluginDef
import zio.Task

class TestDockerSvc(val test: AvailablePort@Id("test"))

object TestDockerPlugin extends DockerSupportModule[Task] with PluginDef {
  make[TestDocker.Container].fromResource {
    TestDocker.make[Task]
  }

  make[AvailablePort].named("test").tagged(Env.Test).from {
    dn: TestDocker.Container =>
      dn.availablePorts.first(TestDocker.primaryPort)
  }

  make[TestDockerSvc]
}
