package dev.rootbox

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.*
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object Rootfs {
    private val KNOWN = setOf("bin","boot","dev","etc","home","lib","lib64","media","mnt","opt","proc","root","run","sbin","srv","sys","tmp","usr","var")

    fun dir(c: Context) = File(c.filesDir, "distros").apply { mkdirs() }
    fun list(c: Context): List<String> =
        dir(c).listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun prettyName(c: Context, name: String): String = try {
        File(dir(c), "$name/etc/os-release").readLines()
            .firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"') ?: name
    } catch (e: Exception) { name }

    /** Delete without following symlinks. */
    fun delete(d: File) {
        if (!d.exists()) return
        Files.walkFileTree(d.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(f: java.nio.file.Path, a: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(f); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dd: java.nio.file.Path, e: IOException?): FileVisitResult {
                try { Os.chmod(dd.toString(), 448) } catch (_: Exception) {}
                Files.deleteIfExists(dd); return FileVisitResult.CONTINUE
            }
            override fun preVisitDirectory(dd: java.nio.file.Path, a: BasicFileAttributes): FileVisitResult {
                try { Os.chmod(dd.toString(), 448) } catch (_: Exception) {}
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun decompress(raw: InputStream): InputStream {
        val b = BufferedInputStream(raw, 1 shl 16)
        b.mark(8)
        val m = ByteArray(6); val n = b.read(m); b.reset()
        fun u(i: Int) = if (i < n) m[i].toInt() and 0xff else -1
        return when {
            u(0) == 0x1f && u(1) == 0x8b -> GzipCompressorInputStream(b, true)
            u(0) == 0xfd && u(1) == 0x37 && u(2) == 0x7a -> XZCompressorInputStream(b)
            u(0) == 0x28 && u(1) == 0xb5 && u(2) == 0x2f && u(3) == 0xfd -> ZstdCompressorInputStream(b)
            else -> b
        }
    }

    private fun chmod(f: File, mode: Int) { try { Os.chmod(f.path, mode) } catch (_: Exception) {} }

    fun extract(c: Context, raw: InputStream, name: String, log: (String) -> Unit) {
        val dest = File(dir(c), name)
        delete(dest); dest.mkdirs()
        var strip = -1
        var count = 0
        TarArchiveInputStream(decompress(raw)).use { tar ->
            while (true) {
                val e = tar.nextTarEntry ?: break
                var p = e.name.removePrefix("./").trimStart('/')
                if (p == ".") p = ""
                if (strip < 0 && p.isNotEmpty()) {
                    strip = if (p.substringBefore('/') in KNOWN) 0 else 1
                    if (strip == 1) log("Stripping top folder '${p.substringBefore('/')}'")
                }
                if (strip == 1) p = p.substringAfter('/', "")
                if (p.isEmpty() || p.split('/').contains("..")) continue
                val t = File(dest, p)
                try {
                    when {
                        e.isDirectory -> { t.mkdirs(); chmod(t, (e.mode and 0xFFF) or 0x1C0) }
                        e.isSymbolicLink -> { t.parentFile?.mkdirs(); t.delete(); Os.symlink(e.linkName, t.path) }
                        e.isLink -> {
                            var lp = e.linkName.removePrefix("./").trimStart('/')
                            if (strip == 1) lp = lp.substringAfter('/', "")
                            val src = File(dest, lp)
                            if (src.exists()) { t.parentFile?.mkdirs(); t.delete(); src.copyTo(t) }
                        }
                        e.isFile -> {
                            t.parentFile?.mkdirs(); t.delete()
                            FileOutputStream(t).use { o -> tar.copyTo(o) }
                            chmod(t, (e.mode and 0xFFF) or 0x180)
                        }
                        else -> {}
                    }
                } catch (ex: Exception) { log("skip $p: ${ex.message}") }
                if (++count % 500 == 0) log("Extracted $count entries…")
            }
        }
        log("Configuring…")
        postInstall(dest)
        log("Done: $count entries")
    }

    private fun postInstall(d: File) {
        File(d, "tmp").mkdirs(); chmod(File(d, "tmp"), 1023)
        File(d, "root").mkdirs(); chmod(File(d, "root"), 448)
        File(d, "etc").mkdirs()
        val rc = File(d, "etc/resolv.conf"); rc.delete(); rc.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        val hosts = File(d, "etc/hosts"); if (!hosts.exists()) hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
        // Arch: pacman can't check disk space / sandbox under proot
        val pc = File(d, "etc/pacman.conf")
        if (pc.isFile) {
            val out = mutableListOf<String>()
            for (l in pc.readLines()) {
                if (l.trim() == "CheckSpace") out.add("#CheckSpace") else out.add(l)
                if (l.trim() == "[options]") out.add("DisableSandbox")
            }
            pc.writeText(out.joinToString("\n") + "\n")
        }
        // Debian/Ubuntu: apt sandbox user doesn't work under proot
        val apt = File(d, "etc/apt/apt.conf.d")
        if (apt.isDirectory) File(apt, "99rootbox").writeText("APT::Sandbox::User \"root\";\n")
    }
}
