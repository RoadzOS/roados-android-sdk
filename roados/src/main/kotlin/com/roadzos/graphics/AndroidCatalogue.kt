package com.roadzos.graphics

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.security.MessageDigest

private const val ASSET_ROOT = "rvhl"

/**
 * The RVHL catalogue bundled in this library's assets.
 *
 * The core reads files, so on first use the bundle is copied from the APK's
 * assets to `noBackupFilesDir/roados/rvhl-<hash>` (about 4 MB; the hash comes
 * from the bundle manifest, so an SDK update with a new catalogue unpacks to a
 * new directory). The copy is atomic, and later calls reuse it. File hashes
 * are verified by the core on load.
 *
 * Does disk I/O the first time: call it off the main thread (the Compose and
 * View integrations already do).
 */
fun RoadOSCatalogue.Companion.bundled(context: Context): RoadOSCatalogue =
    BundledCatalogue.get(context.applicationContext)

private object BundledCatalogue {
    @Volatile private var instance: RoadOSCatalogue? = null

    fun get(context: Context): RoadOSCatalogue {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            return RoadOSCatalogue.load(unpack(context)).also { instance = it }
        }
    }

    private fun unpack(context: Context): File {
        val assets = context.assets
        val manifest = assets.open("$ASSET_ROOT/manifest.json").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(manifest).joinToString("") { "%02x".format(it) }
        val root = File(context.noBackupFilesDir, "roados")
        val target = File(root, "rvhl-${digest.take(16)}")
        if (File(target, ".complete").exists()) return target

        val staging = File(root, "staging-${System.nanoTime()}")
        try {
            copyTree(assets, ASSET_ROOT, staging)
            File(staging, ".complete").writeText(digest)
            if (!staging.renameTo(target)) {
                // Another process may have won the race; use its copy if complete.
                if (!File(target, ".complete").exists()) {
                    throw RoadOSException(RoadOSException.Code.IO, "cannot install RVHL bundle into $target")
                }
            }
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
        // Remove bundles left behind by older SDK versions.
        root.listFiles()?.filter { it.name.startsWith("rvhl-") && it != target }?.forEach { it.deleteRecursively() }
        return target
    }

    private fun copyTree(assets: AssetManager, path: String, dest: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
        } else {
            dest.mkdirs()
            for (child in children) copyTree(assets, "$path/$child", File(dest, child))
        }
    }
}
