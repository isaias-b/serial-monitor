enablePlugins(UniversalPlugin)
enablePlugins(BuildInfoPlugin)
enablePlugins(JavaAppPackaging)

name := "serial-monitor"
version := "0.0.1"
maintainer := "org.bartelborth"
organization := "org.bartelborth"
scalaVersion := "2.13.1"

val catsEffectVersion  = "2.1.2"
val catsVersion        = "2.1.1"
val fs2Version         = "2.3.0"
val jSerialCommVersion = "2.6.0"
val monocleVersion     = "2.0.4"
val radianceVersion    = "2.5.1"

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

scalacOptions += "-Ymacro-annotations"
scalacOptions += "-Ywarn-value-discard"
scalacOptions += "-deprecation"

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
  case _                        => MergeStrategy.first
}

scriptClasspath := Seq((assemblyJarName in assembly).value)

fork in run := true
