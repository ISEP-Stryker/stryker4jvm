package stryker4s.mutants.findmutants

import scala.util.{Failure, Try}

import better.files.File
import stryker4s.config.Config
import stryker4s.run.process.{Command, ProcessRunner}
import stryker4s.scalatest.{FileUtil, LogMatchers}
import stryker4s.testutil.stubs.TestProcessRunner
import stryker4s.testutil.{MockitoSuite, Stryker4sSuite}

class FileCollectorTest extends Stryker4sSuite with MockitoSuite with LogMatchers {
  private val filledDirPath: File = FileUtil.getResource("fileTests/filledDir")
  private val basePath: File = filledDirPath / "src/main/scala"

  assume(filledDirPath.exists(), "Filled test dir does not exist")
  assume(basePath.exists(), "Basepath dir does not exist")

  describe("collect files to mutate") {
    describe("on empty dir") {
      val emptyDir = FileUtil.getResource("fileTests/emptyDir")
      assume(emptyDir.exists(), "Empty test dir does not exist")

      it("should not collect the baseDir") {
        implicit val config: Config = Config.default.copy(baseDir = emptyDir)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should be(empty)
      }
    }

    describe("on filled dir") {
      it("should find all scala files and not the non-scala files with default config") {
        implicit val config: Config = Config.default.copy(baseDir = filledDirPath)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 4
        results should (
          contain.only(
            basePath / "fileInRootSourceDir.scala",
            basePath / "package" / "someFile.scala",
            basePath / "package" / "secondFile.scala",
            basePath / "package" / "target.scala"
          )
        )
      }

      it("should find matching files with custom config match pattern") {
        implicit val config: Config =
          Config.default.copy(mutate = Seq("src/**/second*.scala"), baseDir = filledDirPath)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()
        val onlyResult = results.loneElement

        onlyResult should equal(basePath / "package" / "secondFile.scala")
      }

      it("should find no matches with a non-matching glob") {
        implicit val config: Config =
          Config.default.copy(mutate = Seq("**/noMatchesToBeFoundHere.scala"), baseDir = filledDirPath)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should be(empty)
      }

      it("should match on multiple globs") {
        implicit val config: Config =
          Config.default.copy(mutate = Seq("**/someFile.scala", "**/secondFile.scala"), baseDir = filledDirPath)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 2
        results should contain.only(basePath / "package" / "someFile.scala", basePath / "package" / "secondFile.scala")
      }

      it("should only add a glob once even when it matches twice") {
        implicit val config: Config =
          Config.default.copy(mutate = Seq("**/someFile.scala", "src/main/scala/**/*.scala"), baseDir = filledDirPath)
        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 3
        results should (contain.only(
          basePath / "package" / "someFile.scala",
          basePath / "package" / "secondFile.scala",
          basePath / "package" / "target.scala"
        ))
      }

      it("should not find a file twice when the patterns match on the same file twice") {
        implicit val config: Config =
          Config.default.copy(
            mutate = Seq("**/someFile.scala", "**/secondFile.scala", "!**/*.scala", "!**/someFile.scala"),
            baseDir = filledDirPath
          )

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should be(empty)
      }

      it("Should exclude the file specified in the excluded files config") {
        implicit val config: Config = Config.default.copy(
          mutate = Seq("**/someFile.scala", "**/secondFile.scala", "!**/someFile.scala"),
          baseDir = filledDirPath
        )

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 1
        results should contain.only(basePath / "package" / "secondFile.scala")
      }

      it("Should exclude all files specified in the excluded files config") {
        implicit val config: Config =
          Config.default.copy(
            mutate = Seq("**/someFile.scala", "**/secondFile.scala", "!**/someFile.scala", "!**/secondFile.scala"),
            baseDir = filledDirPath
          )

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should be(empty)
      }

      it("Should exclude all files based on a wildcard") {
        implicit val config: Config = Config.default.copy(
          mutate = Seq("**/someFile.scala", "**/secondFile.scala", "!**/*.scala"),
          baseDir = filledDirPath
        )

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should be(empty)
      }

      it("Should exclude all files from previous runs in the target folder") {
        implicit val config: Config = Config.default.copy(baseDir = filledDirPath)

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 4
        results should (
          contain.only(
            basePath / "fileInRootSourceDir.scala",
            basePath / "package" / "someFile.scala",
            basePath / "package" / "secondFile.scala",
            basePath / "package" / "target.scala"
          )
        )
      }

      it("Should not exclude a non existing file") {
        implicit val config: Config = Config.default.copy(
          mutate = Seq("**/someFile.scala", "**/secondFile.scala", "!**/nonExistingFile.scala"),
          baseDir = filledDirPath
        )

        val sut = new FileCollector(TestProcessRunner())

        val results = sut.collectFilesToMutate()

        results should have size 2
        results should contain.only(basePath / "package" / "someFile.scala", basePath / "package" / "secondFile.scala")
      }
    }
  }

  describe("Collect files to copy over to tmp folder") {
    val processRunnerMock: ProcessRunner = mock[ProcessRunner]

    it("Should execute git process to collect files") {
      implicit val config: Config = Config.default.copy(baseDir = filledDirPath)
      val filePath = "src/main/scala/package/someFile.scala"
      val expectedFileList = Seq(config.baseDir / filePath)
      val gitProcessResult = Try(Seq(filePath))
      when(processRunnerMock(Command("git ls-files", "--others --exclude-standard --cached"), config.baseDir))
        .thenReturn(gitProcessResult)

      val sut = new FileCollector(processRunnerMock)

      val results = sut.filesToCopy

      results should contain theSameElementsAs expectedFileList
    }

    it("Should copy over files with target in their name") {
      implicit val config: Config = Config.default.copy(baseDir = filledDirPath)
      val filePath = "src/main/scala/package/target.scala"
      val expectedFileList = Seq(config.baseDir / filePath)
      val gitProcessResult = Try(Seq(filePath))
      when(processRunnerMock(Command("git ls-files", "--others --exclude-standard --cached"), config.baseDir))
        .thenReturn(gitProcessResult)

      val sut = new FileCollector(processRunnerMock)

      val results = sut.filesToCopy

      results should contain theSameElementsAs expectedFileList
    }

    it("Should copy the files from the files config key") {
      implicit val config: Config =
        Config.default.copy(baseDir = filledDirPath, files = Some(Seq("**/main/scala/**/*.scala")))
      val expectedFileList = Seq(
        basePath / "package" / "someFile.scala",
        basePath / "package" / "secondFile.scala",
        basePath / "package" / "target.scala"
      )

      val sut = new FileCollector(processRunnerMock)

      val results = sut.filesToCopy

      results should contain theSameElementsAs expectedFileList
    }

    it(
      "Should copy files out of the target folders when no files config key is found and target repo is not a git repo"
    ) {
      implicit val config: Config = Config.default.copy(baseDir = basePath / "package", files = None)
      val expectedFileList =
        Seq(
          basePath / "package" / "someFile.scala",
          basePath / "package" / "secondFile.scala",
          basePath / "package" / "otherFile.notScala",
          basePath / "package" / "target.scala"
        )
      val gitProcessResult = Failure(new Exception("Exception"))
      when(processRunnerMock(any[Command], any[File])).thenReturn(gitProcessResult)

      val sut = new FileCollector(processRunnerMock)

      val results = sut.filesToCopy

      results should have size 4
      results should contain theSameElementsAs expectedFileList
    }

    it("should filter out files that don't exist") {
      implicit val config: Config = Config.default.copy(baseDir = filledDirPath)
      val filePath = "src/main/scala/package/doesnotexist.scala"
      val gitProcessResult = Try(Seq(filePath))
      when(processRunnerMock(Command("git ls-files", "--others --exclude-standard --cached"), config.baseDir))
        .thenReturn(gitProcessResult)

      val sut = new FileCollector(processRunnerMock)

      val results = sut.filesToCopy

      results should be(empty)
    }

    describe("log tests") {
      it("Should log that no files config option is found and is using fallback to copy all files") {
        implicit val config: Config = Config.default.copy(baseDir = filledDirPath)
        val gitProcessResult = Failure(new Exception(""))
        when(processRunnerMock(any[Command], any[File])).thenReturn(gitProcessResult)

        val sut = new FileCollector(processRunnerMock)

        sut.filesToCopy

        "No 'files' specified and not a git repository." shouldBe loggedAsWarning
        "Falling back to copying everything except the 'target/' folder(s)" shouldBe loggedAsWarning
      }
    }
  }
}
