name := "tmp-camel"

version := "2.0"

scalaVersion := "2.9.1"

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor" % "2.0-SNAPSHOT",
  "com.typesafe.akka" % "akka-slf4j" % "2.0-SNAPSHOT",
  "com.typesafe.akka" % "akka-testkit" % "2.0-SNAPSHOT" % "test",
//  "se.scalablesolutions.akka" % "akka-actor" % "1.3-RC5" withSources(),
  "org.apache.camel" % "camel-core" % "2.7.0",
  "org.scalatest" %% "scalatest" % "1.6.1" % "test",
  "org.mockito" % "mockito-core" % "1.9.0" % "test",
 	"junit" % "junit" % "4.10" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

parallelExecution in Test := true