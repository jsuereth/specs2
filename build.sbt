
/** Project */
name := "specs2"

version := "1.11"

organization := "org.specs2"

scalaVersion := "2.9.2"

/** Shell */
shellPrompt := { state => System.getProperty("user.name") + "> " }

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }

/** Dependencies */
resolvers ++= Seq("releases" at "http://oss.sonatype.org/content/repositories/releases",
                  "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots")

libraryDependencies <<= scalaVersion { scala_version => Seq(
  "org.specs2" %% "specs2-scalaz-core" % "6.0.1",
  "org.scala-lang" % "scala-compiler" % scala_version % "optional",
  if (scala_version contains "-1") "org.scalacheck" % "scalacheck_2.9.1" % "1.9" % "optional"
  else                             "org.scalacheck" %% "scalacheck" % "1.9" % "optional",
  "org.scala-tools.testing" % "test-interface" % "0.5" % "optional",
  "org.hamcrest" % "hamcrest-all" % "1.1" % "optional",
  "org.mockito" % "mockito-all" % "1.9.0" % "optional",
  "junit" % "junit" % "4.7" % "optional",
  "org.pegdown" % "pegdown" % "1.0.2" % "optional",
  "org.specs2" % "classycle" % "1.4.1" % "optional"
  )
}

/** Compilation */
javacOptions ++= Seq("-Xmx1812m", "-Xms512m", "-Xss10m")

javaOptions += "-Xmx2G"

scalacOptions ++= Seq("-deprecation", "-unchecked")

maxErrors := 20 

pollInterval := 1000

logBuffered := false

cancelable := true

testOptions := Seq(Tests.Filter(s =>
  Seq("Spec", "Suite", "Unit").exists(s.endsWith(_)) &&
    !(s.endsWith("FeaturesSpec") || s.endsWith("SmellsSpec")) ||
    s.contains("UserGuide") || 
  	s.contains("index") ||
    s.matches("org.specs2.guide.*")))

/** Console */
initialCommands in console := "import org.specs2._"

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("sonatype-snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("sonatype-staging" at nexus   + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>http://specs2.org/</url>
  <licenses>
    <license>
      <name>MIT-style</name>
      <url>http://www.opensource.org/licenses/mit-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>http://github.com/etorreborre/specs2</url>
    <connection>scm:http:http://etorreborre@github.com/etorreborre/specs2.git</connection>
  </scm>
  <developers>
    <developer>
      <id>etorreborre</id>
      <name>Eric Torreborre</name>
      <url>http://etorreborre.blogspot.com/</url>
      </developer>
    </developers>
)

