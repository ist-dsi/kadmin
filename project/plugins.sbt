logLevel := Level.Warn

addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "0.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")
resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.2.1")
