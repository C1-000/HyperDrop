package cythen.hyperdrop.utils

/**
 * Root-backed helpers that are still in use by the app.
 *
 * The old com.heytap.headset ("Melody") model-image scanner was an upstream leftover and
 * has been removed; only the scope-restart helper remains.
 */
object RootManager {
    private val packageNameRegex = Regex("^[A-Za-z0-9_.]+$")

    fun restartPackages(packages: Collection<String>): Boolean {
        val targets = packages.distinct().filter { it.matches(packageNameRegex) }
        if (targets.isEmpty()) return false

        return runCatching {
            val command = targets.joinToString("; ") { "am force-stop $it" }
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
