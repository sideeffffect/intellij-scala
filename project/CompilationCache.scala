import sbt.*
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object CompilationCache {
  private val farmHashCache: ConcurrentHashMap[Path, String] = new ConcurrentHashMap()

  private def farmHash(path: Path): String =
    farmHashCache.computeIfAbsent(path, HashUtil.farmHash(_).toString)

  private val perConfigSettings: Seq[Setting[?]] = Seq(
    pushRemoteCacheConfiguration ~= { _.withOverwrite(true) },
    remoteCacheId := {
      val id = remoteCacheId.value
      val classpath = externalDependencyClasspath.value.map(_.data.toPath).sorted
      val hashString = classpath.map(farmHash).mkString
      val combined = id ++ hashString
      val hash = HashUtil.farmHash(combined.getBytes(StandardCharsets.UTF_8))
      java.lang.Long.toHexString(hash)
    }
  )

  val compilationCacheSettings: Seq[Setting[?]] =
    inConfig(Compile)(perConfigSettings) ++ inConfig(Test)(perConfigSettings)
}
