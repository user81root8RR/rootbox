package dev.rootbox

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : Activity(), TerminalViewClient, TerminalSessionClient {
    private lateinit var view: TerminalView
    private lateinit var session: TerminalSession
    private lateinit var ctrlBtn: Button
    private var ctrl = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val distro = intent.getStringExtra("distro") ?: run { finish(); return }
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)

        session = buildSession(distro)
        view = TerminalView(this, null)
        view.setTerminalViewClient(this)
        view.attachSession(session)
        view.setTextSize(dp(14))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun k(label: String, seq: String) = keys.addView(Button(this).apply {
            text = label; isAllCaps = false; minWidth = dp(48); setOnClickListener { session.write(seq) } })
        k("ESC", "\u001b"); k("TAB", "\t")
        ctrlBtn = Button(this).apply { text = "CTRL"; minWidth = dp(56); setOnClickListener {
            ctrl = !ctrl; alpha = if (ctrl) 0.5f else 1f } }
        keys.addView(ctrlBtn)
        k("←", "\u001b[D"); k("↑", "\u001b[A"); k("↓", "\u001b[B"); k("→", "\u001b[C")
        k("-", "-"); k("/", "/"); k("|", "|"); k("~", "~")
        root.addView(HorizontalScrollView(this).apply { addView(keys) })
        setContentView(root)
        view.requestFocus()
    }

    private fun buildSession(distro: String): TerminalSession {
        val nl = applicationInfo.nativeLibraryDir
        val rootDir = File(Rootfs.dir(this), distro)
        val tmp = File(filesDir, "tmp").apply { mkdirs() }
        val args = mutableListOf(
            "proot", "-0", "--link2symlink", "--kill-on-exit", "-r", rootDir.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys")
        if (File("/storage/emulated/0").exists()) { args += listOf("-b", "/storage/emulated/0:/sdcard") }
        args += listOf("-w", "/root", "/usr/bin/env", "-i",
            "HOME=/root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-c", "if [ -x /bin/bash ]; then exec /bin/bash --login; else exec /bin/sh -l; fi")
        val env = arrayOf(
            "PROOT_LOADER=$nl/libproot-loader.so", "PROOT_TMP_DIR=${tmp.path}", "TMPDIR=${tmp.path}",
            "LD_LIBRARY_PATH=$nl", "PROOT_NO_SECCOMP=1", "PATH=/system/bin")
        return TerminalSession("$nl/libproot.so", filesDir.path, args.toTypedArray(), env, 2000, this)
    }

    override fun onDestroy() { super.onDestroy(); if (::session.isInitialized) session.finishIfRunning() }

    // ---- TerminalSessionClient
    override fun onTextChanged(s: TerminalSession) { view.onScreenUpdated() }
    override fun onTitleChanged(s: TerminalSession) {}
    override fun onSessionFinished(s: TerminalSession) { finish() }
    override fun onCopyTextToClipboard(s: TerminalSession, text: String?) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("term", text))
    }
    override fun onPasteTextFromClipboard(s: TerminalSession?) {
        val t = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text
        if (t != null) session.emulator?.paste(t.toString())
    }
    override fun onBell(s: TerminalSession) {}
    override fun onColorsChanged(s: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(s: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null

    // ---- TerminalViewClient
    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent?) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0)
    }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, s: TerminalSession?) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false
    override fun readControlKey() = ctrl
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean {
        if (ctrl) { ctrl = false; runOnUiThread { ctrlBtn.alpha = 1f } }
        return false
    }
    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "error", e) }
}
