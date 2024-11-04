import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object CompilationCache {
  lazy val farmHashCache: SettingKey[ConcurrentHashMap[Path, String]] =
    settingKey("Global cache of farm hash values for external dependencies").withRank(Invisible)

  private def farmHash(cache: ConcurrentHashMap[Path, String])(path: Path): String =
    cache.computeIfAbsent(path, HashUtil.farmHash(_).toString)

  private val perConfigSettings: Seq[Setting[?]] = Seq(
    pushRemoteCacheConfiguration ~= { _.withOverwrite(true) },
    remoteCacheId := {
      val id = remoteCacheId.value
      val classpath = externalDependencyClasspath.value.map(_.data.toPath).sorted
      val cache = (Global / farmHashCache).?.value.getOrElse(new ConcurrentHashMap())
      val hashString = classpath.map(farmHash(cache)).mkString
      val combined = id ++ hashString
      val hash = HashUtil.farmHash(combined.getBytes(StandardCharsets.UTF_8))
      java.lang.Long.toHexString(hash)
    }
  )

  val compilationCacheSettings: Seq[Setting[?]] =
    inConfig(Compile)(perConfigSettings) ++ inConfig(Test)(perConfigSettings)
}
