ThisBuild / organization := "com.chuchalov"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.0"

scalacOptions ++= Seq("-target:jvm-1.8")

lazy val root = (project in file("."))
  .settings(
    name := "geoservice",
    assembly / mainClass := Some("com.chuchalov.GeoService"),
    assembly / assemblyJarName := "geoservice.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  ).
  enablePlugins(AssemblyPlugin)

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "22.1.0",
)

