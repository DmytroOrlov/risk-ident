ThisBuild / organization := "com.example"
ThisBuild / version := "1.1-SNAPSHOT"
ThisBuild / scalacOptions ++= Seq(
  "-Ymacro-annotations",
)

val V = new {
  val catsEffect = "2.3.1"
  val zioInteropCats = "2.2.0.1"
  val zio = "1.0.3"
  val distage = "0.10.19"
  val sttp = "2.2.9"

  val scalacheck = "1.15.1"

  val betterMonadicFor = "0.3.1"
  val kindProjector = "0.11.2"

  val silencer = "1.7.1"
}

val Deps = new {
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
  val zioInteropCats = "dev.zio" %% "zio-interop-cats" % V.zioInteropCats
  val zio = "dev.zio" %% "zio" % V.zio
  val zioStreams = "dev.zio" %% "zio-streams" % V.zio
  val distageFramework = "io.7mind.izumi" %% "distage-framework" % V.distage
  val distageFrameworkDocker = "io.7mind.izumi" %% "distage-framework-docker" % V.distage
  val distageTestkitScalatest = "io.7mind.izumi" %% "distage-testkit-scalatest" % V.distage
  val logstageAdapterSlf4J = "io.7mind.izumi" %% "logstage-adapter-slf4j" % V.distage

  val sttpClientCirce = "com.softwaremill.sttp.client" %% "circe" % V.sttp
  val asyncHttpClientBackendZio = "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % V.sttp
  val httpClientBackendZio = "com.softwaremill.sttp.client" %% "httpclient-backend-zio" % V.sttp

  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck

  val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % V.betterMonadicFor
  val kindProjector = "org.typelevel" %% "kind-projector" % V.kindProjector cross CrossVersion.full
}

val commonSettings = Seq(
  scalaVersion := "2.13.4",
)

lazy val `risk-ident-upload` = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Deps.catsEffect,
      Deps.zioInteropCats,
      Deps.zio,
      Deps.zioStreams,
      Deps.logstageAdapterSlf4J,
      Deps.distageFramework,

      Deps.distageFrameworkDocker % Test,
      Deps.distageTestkitScalatest % Test,
      Deps.scalacheck % Test,

      Deps.sttpClientCirce,
      Deps.asyncHttpClientBackendZio,
      Deps.httpClientBackendZio,
    ),
    addCompilerPlugin(Deps.betterMonadicFor),
    addCompilerPlugin(Deps.kindProjector),
  )
  .dependsOn(macros)

lazy val macros = project
  .disablePlugins(RevolverPlugin)
  .settings(
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % V.silencer cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % V.silencer % Provided cross CrossVersion.full,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "dev.zio" %% "zio-test-sbt" % V.zio % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalacOptions += "-language:experimental.macros",
  )

lazy val `provided-test-service` = project
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .disablePlugins(RevolverPlugin)
  .settings(
    mainClass in Compile := Some("de.riskident.Main"),
    dockerExposedPorts := Seq(8080),
    dockerRepository := None,
  )
