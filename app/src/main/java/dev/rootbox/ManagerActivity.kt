package dev.rootbox

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ManagerActivity : Activity() {
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private var busy = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(32), dp(16), dp(16)) }
        sv.addView(root)
        root.addView(TextView(this).apply { text = "Linux systems"; textSize = 24f })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)
        root.addView(TextView(this).apply { text = "\nAdd a system"; textSize = 18f })
        root.addView(Button(this).apply {
            text = "Import from tarball (.tar.gz / .xz / .zst)…"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 1)
            }
        })
        for ((id, title, url) in Catalog.items) {
            root.addView(Button(this).apply { text = "Download: $title"; setOnClickListener { download(id, url) } })
        }
        status = TextView(this).apply { setPadding(0, dp(12), 0, 0) }
        root.addView(status)
        setContentView(sv)
        refresh()
    }

    private fun refresh() {
        listBox.removeAllViews()
        val l = Rootfs.list(this)
        if (l.isEmpty()) listBox.addView(TextView(this).apply { text = "(none installed yet)" })
        for (n in l) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "$n\n${Rootfs.prettyName(this@ManagerActivity, n)}" },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply { text = "Open"; setOnClickListener {
                startActivity(Intent(this@ManagerActivity, TerminalActivity::class.java).putExtra("distro", n)) } })
            row.addView(Button(this).apply { text = "Delete"; setOnClickListener {
                AlertDialog.Builder(this@ManagerActivity).setMessage("Delete $n?")
                    .setPositiveButton("Delete") { _, _ -> work("Deleting $n…") { Rootfs.delete(File(Rootfs.dir(this@ManagerActivity), n)) } }
                    .setNegativeButton("Cancel", null).show() } })
            listBox.addView(row)
        }
    }

    private fun log(s: String) = runOnUiThread { status.text = s }

    private fun work(msg: String, block: () -> Unit) {
        if (busy) { Toast.makeText(this, "Busy", Toast.LENGTH_SHORT).show(); return }
        busy = true; log(msg)
        Thread {
            try { block() } catch (e: Exception) { log("Error: ${e.message}") }
            busy = false
            runOnUiThread { refresh() }
        }.start()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (req != 1 || res != RESULT_OK) return
        var disp = "imported"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) disp = c.getString(i) }
        }
        val name = disp.substringBefore(".tar").substringBeforeLast('.').lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-").ifEmpty { "imported" }
        work("Importing $name…") {
            contentResolver.openInputStream(uri)!!.use { Rootfs.extract(this, it, name) { m -> log(m) } }
        }
    }

    private fun download(id: String, url: String) {
        work("Downloading $id…") {
            val tmp = File(cacheDir, "$id.tar.dl")
            val con = URL(url).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = true; con.connectTimeout = 20000; con.readTimeout = 30000
            val total = con.contentLengthLong
            con.inputStream.use { i -> tmp.outputStream().use { o ->
                val buf = ByteArray(1 shl 16); var done = 0L; var last = 0L
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (done - last > (2 shl 20)) { last = done; log("Downloading $id: ${done shr 20} / ${if (total > 0) total shr 20 else "?"} MB") }
                }
            } }
            tmp.inputStream().use { Rootfs.extract(this, it, id) { m -> log(m) } }
            tmp.delete()
        }
    }
}
