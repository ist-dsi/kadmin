import ExtraReleaseKeys._

organization := "pt.tecnico.dsi"
name := "kadmin"

// ======================================================================================================================
// ==== Compile Options =================================================================================================
// ======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "2.12.1"
scalacOptions ++= Seq(
  "-deprecation",                   //Emit warning and location for usages of deprecated APIs.
  "-encoding", "UTF-8",             //Use UTF-8 encoding. Should be default.
  "-feature",                       //Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions",  //Explicitly enables the implicit conversions feature
  "-unchecked",                     //Enable detailed unchecked (erasure) warnings
  "-Xfatal-warnings",               //Fail the compilation if there are any warnings.
  "-Xlint",                         //Enable recommended additional warnings.
  "-Yno-adapted-args",              //Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code",               //Warn when dead code is identified.
  "-Ywarn-inaccessible",            //Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",               //Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",        //Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",            //Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",           //Warn when numerics are widened.
  "-Ywarn-unused",                  //Warn when local and private vals, vars, defs, and types are unused.
  "-Ywarn-unused-import"            //Warn when imports are unused.
)

// These lines ensure that in sbt console or sbt test:console the -Ywarn* and the -Xfatal-warning are not bothersome.
// https://stackoverflow.com/questions/26940253/in-sbt-how-do-you-override-scalacoptions-for-console-in-all-configurations
scalacOptions in (Compile, console) ~= (_ filterNot { option =>
  option.startsWith("-Ywarn") || option == "-Xfatal-warnings"
})
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value

// ======================================================================================================================
// ==== Dependencies ====================================================================================================
// ======================================================================================================================
libraryDependencies ++= Seq(
  // Logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  // Testing
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  // Configurationrage
  "com.typesafe" % "config" % "1.3.1",
  // Time and dates
  "joda-time" % "joda-time" % "2.9.9",
  //"work.martins.simon" %% "scala-expect" % "6.0.1-SNAPSHOT"
  "work.martins.simon" %% "scala-expect" % "6.0.0"
)

// ======================================================================================================================
// ==== Scaladoc ========================================================================================================
// ======================================================================================================================
autoAPIMappings := true // Tell scaladoc to look for API documentation of managed dependencies in their metadata.
scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",    // Create inheritance diagrams for classes, traits and packages.
  "-groups",      // Group similar functions together (based on the @group annotation)
  "-implicits",   // Document members inherited by implicit conversions.
  "-doc-source-url", s"${homepage.value.get}/tree/v${latestReleasedVersion.value}€{FILE_PATH}.scala",
  "-sourcepath", (baseDirectory in ThisBuild).value.getAbsolutePath
)
// Define the base URL for the Scaladocs for your library. This will enable clients of your library to automatically
// link against the API documentation using autoAPIMappings.
apiURL := Some(url(s"${homepage.value.get}/${latestReleasedVersion.value}/api/"))

site.settings
site.includeScaladoc()
ghpages.settings
git.remoteRepo := s"git@github.com:ist-dsi/${name.value}.git"

// ======================================================================================================================
// ==== Deployment ======================================================================================================
// ======================================================================================================================
publishMavenStyle := true
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
publishArtifact in Test := false
sonatypeProfileName := organization.value

pomIncludeRepository := { _ => false }
licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/ist-dsi/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, git.remoteRepo.value))
pomExtra :=
  <developers>
    <developer>
      <id>Lasering</id>
      <name>Simão Martins</name>
      <url>https://github.com/Lasering</url>
    </developer>
  </developers>


lazy val writeVersions: ReleaseStep = { st: State =>
  import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys.versions
  import sbtrelease.Utilities._
  import sbtrelease.ReleaseStateTransformations.reapply
  
  val vs = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
  val (releasedVersion, nextVersion) = vs
  
  
  st.log.info("Setting version to '%s'." format nextVersion)
  
  val useGlobal = st.extract.get(releaseUseGlobalVersion)
  val global = if (useGlobal) "in ThisBuild " else ""
  val versionStr = s"""import ExtraReleaseKeys._
                      |version $global := "$nextVersion"
                      |latestReleasedVersion $global := "$releasedVersion"""".stripMargin
  
  val file = st.extract.get(releaseVersionFile)
  IO.writeLines(file, Seq(versionStr))
  
  reapply(Seq(
    if (useGlobal) {
      version in ThisBuild := nextVersion
    } else {
      version := nextVersion
    },
    if (useGlobal) {
      latestReleasedVersion in ThisBuild := releasedVersion
    } else {
      latestReleasedVersion := releasedVersion
    }
  ), st)
}

// Will fail a build if updates for the dependencies are found
dependencyUpdatesFailBuild := true

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  releaseStepCommand("dependencyUpdates"),
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
  writeVersions
)