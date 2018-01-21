name := "offenderpollpush"

organization := "gov.uk.justice.digital"

version := "0.1.00"

scalaVersion := "2.12.4"

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-native" % "3.5.3",
  "org.clapper" %% "grizzled-slf4j" % "1.3.2",
  "net.codingwell" %% "scala-guice" % "4.1.1",
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.12",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.9.1",
  "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "6.1.1",

  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.mockito" % "mockito-core" % "2.13.0" % "test",
  "com.github.tomakehurst" % "wiremock" % "2.13.0" % "test",
)

assemblyJarName in assembly := "offenderPollPush-" + version.value + ".jar"
