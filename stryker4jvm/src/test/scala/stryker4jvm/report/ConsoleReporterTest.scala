package stryker4jvm.report

import fansi.Color.*
import fs2.Stream
import mutationtesting.*
import stryker4jvm.config.Config
import stryker4jvm.reporting.{FinishedRunEvent, MutantTestedEvent}
import stryker4jvm.reporting.reporters.ConsoleReporter
import stryker4jvm.scalatest.LogMatchers
import stryker4jvm.testutil.Stryker4jvmIOSuite

import scala.concurrent.duration.*

class ConsoleReporterTest extends Stryker4jvmIOSuite with LogMatchers {

  describe("mutantTested") {
    implicit val config: Config = Config.default

    it("should log progress") {
      val sut = new ConsoleReporter()

      mutantTestedStream(2).through(sut.mutantTested).compile.drain.asserting { _ =>
        "Tested mutant 1/2 (50%)" shouldBe loggedAsInfo
        "Tested mutant 2/2 (100%)" shouldBe loggedAsInfo
      }
    }

    it("should round decimal numbers") {
      val sut = new ConsoleReporter()

      mutantTestedStream(3).through(sut.mutantTested).compile.drain.asserting { _ =>
        "Tested mutant 1/3 (33%)" shouldBe loggedAsInfo
        "Tested mutant 3/3 (100%)" shouldBe loggedAsInfo
      }
    }
    def mutantTestedStream(size: Int) = Stream.constant(()).take(size.toLong).as(MutantTestedEvent(size))
  }

  describe("reportFinishedRun") {
    it("should report killed mutants as debug") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()
      val results = MutationTestResult(
        thresholds = mutationtesting.Thresholds(80, 60),
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "<!=",
            mutants = Seq(
              MutantResult("0", "BinaryOperator", "==", Location(Position(1, 2), Position(1, 4)), MutantStatus.Killed)
            )
          )
        )
      )
      val metrics = Metrics.calculateMetrics(results)
      sut
        .onRunFinished(FinishedRunEvent(results, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          "Mutation run finished! Took 15 seconds" shouldBe loggedAsInfo
          s"Total mutants: ${Cyan("1")}, detected: ${Green("1")}, undetected: ${Red("0")}" shouldBe loggedAsInfo
          s"""Detected mutants:
             |0. [${Magenta("Killed")}] [${LightGray("BinaryOperator")}]
             |${Blue("stryker4s.scala")}:${Yellow("1")}:${Yellow("2")}
             |${Red("-\t!=")}
             |${Green("+\t==")}
             |""".stripMargin shouldBe loggedAsDebug
        }
    }

    it("should report a finished run with multiple mutants") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()
      val results = MutationTestResult(
        thresholds = mutationtesting.Thresholds(80, 60),
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "<!=",
            mutants = Seq(
              MutantResult("0", "BinaryOperator", ">", Location(Position(1, 1), Position(1, 2)), MutantStatus.Survived),
              MutantResult("1", "BinaryOperator", "==", Location(Position(1, 2), Position(1, 4)), MutantStatus.Killed)
            )
          ),
          "subPath/stryker4s.scala" -> FileResult(
            source = "1",
            mutants = Seq(
              MutantResult("2", "BinaryOperator", "0", Location(Position(1, 1), Position(1, 2)), MutantStatus.Survived)
            )
          )
        )
      )
      val metrics = Metrics.calculateMetrics(results)
      sut
        .onRunFinished(FinishedRunEvent(results, metrics, 1.minute, config.baseDir.toNioPath))
        .asserting { _ =>
          "Mutation run finished! Took 1 minute" shouldBe loggedAsInfo
          s"Total mutants: ${Cyan("3")}, detected: ${Green("1")}, undetected: ${Red("2")}" shouldBe loggedAsInfo
          s"""Undetected mutants:
             |0. [${Magenta("Survived")}] [${LightGray("BinaryOperator")}]
             |${Blue("stryker4s.scala")}:${Yellow("1")}:${Yellow("1")}
             |${Red("-\t<")}
             |${Green("+\t>")}
             |
             |2. [${Magenta("Survived")}] [${LightGray("BinaryOperator")}]
             |${Blue("subPath/stryker4s.scala")}:${Yellow("1")}:${Yellow("1")}
             |${Red("-\t1")}
             |${Green("+\t0")}
             |""".stripMargin shouldBe loggedAsInfo
        }
    }

    it("should log mutants sorted by id") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()
      val results = MutationTestResult(
        thresholds = mutationtesting.Thresholds(80, 60),
        files = Map(
          "subPath/stryker4s.scala" -> FileResult(
            source = "1",
            mutants = Seq(
              MutantResult("2", "BinaryOperator", "0", Location(Position(1, 1), Position(1, 2)), MutantStatus.Survived)
            )
          ),
          "stryker4s.scala" -> FileResult(
            source = "<!=",
            mutants = Seq(
              MutantResult(
                "1",
                "BinaryOperator",
                "==",
                Location(Position(1, 2), Position(1, 4)),
                MutantStatus.Survived
              ),
              MutantResult("0", "BinaryOperator", ">", Location(Position(1, 1), Position(1, 2)), MutantStatus.Survived)
            )
          )
        )
      )
      sut
        .onRunFinished(
          FinishedRunEvent(results, Metrics.calculateMetrics(results), 15.seconds, config.baseDir.toNioPath)
        )
        .asserting { _ =>
          s"Total mutants: ${Cyan("3")}, detected: ${Green("0")}, undetected: ${Red("3")}" shouldBe loggedAsInfo
          s"""Undetected mutants:
             |0. [${Magenta("Survived")}] [${LightGray("BinaryOperator")}]
             |${Blue("stryker4s.scala")}:${Yellow("1")}:${Yellow("1")}
             |${Red("-\t<")}
             |${Green("+\t>")}
             |
             |1. [${Magenta("Survived")}] [${LightGray("BinaryOperator")}]
             |${Blue("stryker4s.scala")}:${Yellow("1")}:${Yellow("2")}
             |${Red("-\t!=")}
             |${Green("+\t==")}
             |
             |2. [${Magenta("Survived")}] [${LightGray("BinaryOperator")}]
             |${Blue("subPath/stryker4s.scala")}:${Yellow("1")}:${Yellow("1")}
             |${Red("-\t1")}
             |${Green("+\t0")}
             |""".stripMargin shouldBe loggedAsInfo
        }
    }

    it("should report two line mutants properly") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()
      val results = MutationTestResult(
        thresholds = mutationtesting.Thresholds(80, 60),
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "foo\nbar\nbaz",
            mutants = Seq(
              MutantResult(
                "0",
                "StringLiteral",
                "qux\nfoo",
                Location(Position(2, 1), Position(3, 4)),
                MutantStatus.Survived
              )
            )
          )
        )
      )
      val metrics = Metrics.calculateMetrics(results)
      sut
        .onRunFinished(FinishedRunEvent(results, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"""Undetected mutants:
             |0. [${Magenta("Survived")}] [${LightGray("StringLiteral")}]
             |${Blue("stryker4s.scala")}:${Yellow("2")}:${Yellow("1")}
             |${Red("-\tbar\n\tbaz")}
             |${Green("+\tqux\n\tfoo")}
             |""".stripMargin shouldBe loggedAsInfo
        }
    }

    it("should report multiline mutants properly") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()
      val results = MutationTestResult(
        thresholds = mutationtesting.Thresholds(80, 60),
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "foo\nbar\nbaz",
            mutants = Seq(
              MutantResult(
                "0",
                "StringLiteral",
                "ux\nqux\nfoo",
                Location(Position(1, 2), Position(3, 4)),
                MutantStatus.Survived
              )
            )
          )
        )
      )
      val metrics = Metrics.calculateMetrics(results)
      sut
        .onRunFinished(FinishedRunEvent(results, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"Total mutants: ${Cyan("1")}, detected: ${Green("0")}, undetected: ${Red("1")}" shouldBe loggedAsInfo
          s"""Undetected mutants:
             |0. [${Magenta("Survived")}] [${LightGray("StringLiteral")}]
             |${Blue("stryker4s.scala")}:${Yellow("1")}:${Yellow("2")}
             |${Red("-\too\n\tbar\n\tbaz")}
             |${Green("+\tux\n\tqux\n\tfoo")}
             |""".stripMargin shouldBe loggedAsInfo
        }
    }

    it("should round decimal mutation scores") {
      implicit val config: Config = Config(thresholds = stryker4jvm.config.Thresholds(break = 48, low = 49, high = 50))
      val sut = new ConsoleReporter()
      val threeReport = MutationTestResult(
        thresholds = Thresholds(80, 60), // These thresholds are not used
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "foo\nbar\nbaz",
            mutants = Seq(
              MutantResult("0", "", "bar\nbaz\nqu", Location(Position(1, 1), Position(2, 2)), MutantStatus.Survived),
              MutantResult("1", "", "==", Location(Position(1, 1), Position(1, 3)), MutantStatus.Killed),
              MutantResult("2", "", ">=", Location(Position(1, 1), Position(1, 3)), MutantStatus.Killed)
            )
          )
        )
      )

      sut
        .onRunFinished(
          FinishedRunEvent(threeReport, Metrics.calculateMetrics(threeReport), 15.seconds, config.baseDir.toNioPath)
        )
        .asserting { _ =>
          s"Mutation score: ${Green("66.67")}%" shouldBe loggedAsInfo
        }
    }

    it("should log NaN correctly as n/a") {
      implicit val config: Config = Config.default
      val sut = new ConsoleReporter()

      val report = MutationTestResult(
        thresholds = Thresholds(80, 60),
        files = Map(
          "stryker4s.scala" -> FileResult(
            source = "foo\nbar\nbaz",
            mutants = Seq(
              MutantResult("0", "", "bar\nbaz\nqu", Location(Position(1, 1), Position(2, 2)), MutantStatus.CompileError)
            )
          )
        )
      )

      sut
        .onRunFinished(FinishedRunEvent(report, Metrics.calculateMetrics(report), 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"Mutation score: ${LightGray("n/a")}" shouldBe loggedAsInfo
        }
    }

    // 1 killed, 1 survived, mutation score 50
    val report = MutationTestResult(
      thresholds = Thresholds(80, 60), // These thresholds are not used
      files = Map(
        "stryker4s.scala" -> FileResult(
          source = "foo\nbar\nbaz",
          mutants = Seq(
            MutantResult("0", "", "bar\nbaz\nqu", Location(Position(1, 1), Position(2, 2)), MutantStatus.Survived),
            MutantResult("1", "", "==", Location(Position(1, 1), Position(1, 3)), MutantStatus.Killed)
          )
        )
      )
    )
    val metrics = Metrics.calculateMetrics(report)

    it("should report the mutation score when it is info") {
      implicit val config: Config = Config(thresholds = stryker4jvm.config.Thresholds(break = 48, low = 49, high = 50))
      val sut = new ConsoleReporter()

      sut
        .onRunFinished(FinishedRunEvent(report, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"Mutation score: ${Green("50.0")}%" shouldBe loggedAsInfo
        }
    }

    it("should report the mutation score when it is warning") {
      implicit val config: Config = Config(thresholds = stryker4jvm.config.Thresholds(break = 49, low = 50, high = 51))
      val sut = new ConsoleReporter()

      sut
        .onRunFinished(FinishedRunEvent(report, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"Mutation score: ${Yellow("50.0")}%" shouldBe loggedAsWarning
        }
    }

    it("should report the mutation score when it is dangerously low") {
      implicit val config: Config = Config(thresholds = stryker4jvm.config.Thresholds(break = 50, low = 51, high = 52))
      val sut = new ConsoleReporter()

      sut
        .onRunFinished(FinishedRunEvent(report, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          "Mutation score dangerously low!" shouldBe loggedAsError
          s"Mutation score: ${Red("50.0")}%" shouldBe loggedAsError
        }
    }

    it("should log when below threshold") {
      implicit val config: Config = Config(thresholds = stryker4jvm.config.Thresholds(break = 51, low = 52, high = 53))
      val sut = new ConsoleReporter()

      sut
        .onRunFinished(FinishedRunEvent(report, metrics, 15.seconds, config.baseDir.toNioPath))
        .asserting { _ =>
          s"Mutation score below threshold! Mutation score: ${Red("50.0")}%. Threshold: 51%" shouldBe loggedAsError
        }
    }
  }
}
