package stryker4jvm.files

import cats.effect.IO
import fs2.Stream
import fs2.io.file.{Files, Path}
import stryker4jvm.files.Glob.glob

class GlobFileResolver(basePath: Path, pattern: Seq[String]) extends MutatesFileResolver with FilesFileResolver {

  override def files: Stream[IO, Path] =
    glob(basePath, pattern)
      .evalFilterNot(Files[IO].isDirectory)

}
