organization := "pt.tecnico.dsi"
name := "kadmin"

javacOptions ++= Seq(
  "-Xlint",
  "-encoding", "UTF-8",
  "-Dfile.encoding=utf-8"
)

scalaVersion := "2.12.0"
scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-deprecation",                   //Emit warning and location for usages of deprecated APIs.
  "-encoding", "UTF-8",             //Use UTF-8 encoding. Should be default.
  "-feature",                       //Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions",  //Explicitly enables the implicit conversions feature
  "-unchecked",                     //Enable detailed unchecked (erasure) warnings
  "-Xfatal-warnings",               //Fail the compilation if there are any warnings.
  "-Xlint",                         //Enable recommended additional warnings.
  //"-Yinline-warnings",              //Emit inlining warnings.
  "-Yno-adapted-args",              //Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code"                //Warn when dead code is identified.
)

libraryDependencies ++= Seq(
  //Logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
  //Testing
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  //Configurationrage
  "com.typesafe" % "config" % "1.3.1",
  //Time and dates
  "joda-time" % "joda-time" % "2.9.5",
  //See http://stackoverflow.com/questions/13856266/class-broken-error-with-joda-time-using-scala
  //as to why this library must be included
  "org.joda" % "joda-convert" % "1.8.1",

  "work.martins.simon" %% "scala-expect" % "6.0.0"
)
resolvers += Opts.resolver.sonatypeReleases

autoAPIMappings := true
scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-diagrams")

site.settings
site.includeScaladoc()
ghpages.settings
git.remoteRepo := s"git@github.com:ist-dsi/${name.value}.git"

licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/ist-dsi/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, git.remoteRepo.value))

publishMavenStyle := true
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
publishArtifact in Test := false
sonatypeProfileName := organization.value

pomIncludeRepository := { _ => false }
pomExtra :=
  <developers>
    <developer>
      <id>Lasering</id>
      <name>Simão Martins</name>
      <url>https://github.com/Lasering</url>
    </developer>
  </developers>

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommand("doc"),
  releaseStepCommand("""eval "./test.sh" !"""),
  setReleaseVersion,
  tagRelease,
  releaseStepCommand("ghpagesPushSite"),
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeRelease"),
  pushChanges,
  setNextVersion
)