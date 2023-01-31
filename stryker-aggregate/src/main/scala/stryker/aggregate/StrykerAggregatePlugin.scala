package stryker.aggregate

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import mutationtesting.Thresholds
import sbt.Keys.commands
import sbt.{AutoPlugin, Command, Def}

// Copied from https://github.com/stryker-mutator/stryker4s/issues/938
import java.nio.file.{Files, Paths, StandardCopyOption}

// scalastyle:off

object Aggregator {
  def aggregate(first: MutationReport, second: MutationReport): MutationReport =
    MutationReport(
      `$schema` = first.`$schema`,
      schemaVersion = first.schemaVersion,
      thresholds = first.thresholds,
      projectRoot = first.projectRoot,
      files = first.files ++ second.files,
      config = first.config
    )
}

case class MutationReport(
    `$schema`: String,
    schemaVersion: String,
    thresholds: Thresholds,
    projectRoot: String,
    files: Map[String, Object],
    config: Map[String, Object]
)

/** Adds command to aggregate Stryker reports for sub-modules
  */
object StrykerAggregatePlugin extends AutoPlugin {
  override def trigger = allRequirements

  def strykerAggregate = Command.command("strykerAggregate") { state =>
    val baseDir = Paths.get("./target/stryker4s-report")
    val reportDirs = baseDir.toFile.listFiles().filter(f => Files.isDirectory(f.toPath))
    val reports = reportDirs
      .map(_.toPath.resolve("report.json"))
      .filter(Files.exists(_))
      .map(_.toFile)

    val mapper = new ObjectMapper()
      .registerModule(DefaultScalaModule)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val report = reports
      .map { r =>
        println(s"Reading $r")
        mapper.readValue(r, classOf[MutationReport])
      }
      .reduce(Aggregator.aggregate)

    reportDirs.head
      .listFiles()
      .filterNot(_.toString.endsWith("report.json"))
      .filterNot(_.toString.endsWith("report.js"))
      .foreach { src =>
        val dst = baseDir.resolve(src.getName)
        println(s"Copying $src to $dst")
        Files.copy(src.toPath, baseDir.resolve(src.getName), StandardCopyOption.REPLACE_EXISTING)
      }

    val data = mapper.writeValueAsString(report)
    Files.write(
      baseDir.resolve("report.js"),
      s"document.querySelector('mutation-test-report-app').report = $data".getBytes
    )

    state
  }

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    commands ++= Seq(strykerAggregate)
  )
}
