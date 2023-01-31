resolvers += Resolver.mavenLocal

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")
addSbtPlugin("io.stryker-mutator" % "sbt-stryker4s" % "0.14.3")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.4.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")

// Protobuf plugin and its dependencies
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.11"

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")

addSbtPlugin("io.stryker-mutator" % "stryker-aggregate" % "0.0.0-TEST-SNAPSHOT")
