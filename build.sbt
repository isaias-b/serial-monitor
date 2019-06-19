enablePlugins(UniversalPlugin)
enablePlugins(BuildInfoPlugin)
enablePlugins(JavaAppPackaging)

addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)

name := "serial-monitor"
version := "0.0.1"
maintainer := "org.bartelborth"
organization := "org.bartelborth"
scalaVersion := "2.12.8"

val catsEffectVersion  = "1.3.1"
val catsVersion        = "1.6.1"
val fs2Version         = "1.0.4"
val jSerialCommVersion = "2.5.1"
val monocleVersion     = "1.5.1-cats"
val radianceVersion    = "2.0.1"

libraryDependencies ++= Seq(
  "co.fs2"                     %% "fs2-core"          % fs2Version,
  "co.fs2"                     %% "fs2-io"            % fs2Version,
  "com.fazecast"               % "jSerialComm"        % jSerialCommVersion,
  "com.github.julien-truffaut" %% "monocle-core"      % monocleVersion,
  "com.github.julien-truffaut" %% "monocle-law"       % monocleVersion % Test,
  "com.github.julien-truffaut" %% "monocle-macro"     % monocleVersion,
  "org.pushing-pixels"         % "radiance-substance" % radianceVersion,
  "org.typelevel"              %% "cats-core"         % catsVersion,
  "org.typelevel"              %% "cats-effect"       % catsEffectVersion,
)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "org.bartelborth"

mainClass in Compile := Some("org.bartelborth.Main")

scalacOptions += "-Ypartial-unification"
scalacOptions += "-Ywarn-value-discard"

assemblyJarName in assembly := "serial-monitor.jar"
mappings in Universal := {
  val universalMappings = (mappings in Universal).value
  val fatJar            = (assembly in Compile).value
  val filtered = universalMappings filter {
    case (_, fileName) => !fileName.endsWith(".jar")
  }
  filtered :+ (fatJar -> ("lib/" + fatJar.getName))
}

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x                        => MergeStrategy.first
}

scriptClasspath := Seq((assemblyJarName in assembly).value)

fork in run := true
