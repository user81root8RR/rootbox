package dev.rootbox

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class LauncherActivity : Activity() {
    data class App(val label: String, val icon: Drawable, val pkg: String)

    private var all = listOf<App>()
    private var shown = listOf<App>()
    private lateinit var grid: GridView
    private lateinit var search: EditText

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(i: Int) = shown[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val a = shown[i]
            return LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(8), 0, dp(8))
                addView(ImageView(context).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(TextView(context).apply {
                    text = a.label; textSize = 11f; setTextColor(Color.WHITE); maxLines = 1
                    setShadowLayer(4f, 0f, 0f, Color.BLACK); gravity = Gravity.CENTER
                })
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(48), dp(12), dp(8)) }
        search = EditText(this).apply {
            hint = "Search apps"; setTextColor(Color.WHITE); setHintTextColor(0x99FFFFFF.toInt())
            setBackgroundColor(0x66000000); setPadding(dp(12), dp(8), dp(12), dp(8)); setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search)
        grid = GridView(this).apply {
            numColumns = 4; adapter = this@LauncherActivity.adapter; setPadding(0, dp(8), 0, 0)
            setOnItemClickListener { _, _, i, _ -> packageManager.getLaunchIntentForPackage(shown[i].pkg)?.let { startActivity(it) } }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0x88000000.toInt()) }
        fun dockBtn(t: String, f: () -> Unit) = dock.addView(Button(this).apply { text = t; setOnClickListener { f() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dockBtn("Terminal") { openTerminal() }
        dockBtn("Linux") { startActivity(Intent(this, ManagerActivity::class.java)) }
        dockBtn("Home") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        root.addView(dock)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); load() }
    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); search.setText(""); grid.setSelection(0) }
    @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() {}

    private fun load() {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(i, 0)
            .map { App(it.loadLabel(pm).toString(), it.loadIcon(pm), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase() }
        filter()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
        adapter.notifyDataSetChanged()
    }

    private fun openTerminal() {
        val d = Rootfs.list(this)
        when {
            d.isEmpty() -> { Toast.makeText(this, "Install a Linux system first", Toast.LENGTH_LONG).show(); startActivity(Intent(this, ManagerActivity::class.java)) }
            d.size == 1 -> startActivity(Intent(this, TerminalActivity::class.java).putExtra("distro", d[0]))
            else -> AlertDialog.Builder(this).setItems(d.toTypedArray()) { _, i ->
                startActivity(Intent(this, TerminalActivity::class.java).putExtra("distro", d[i])) }.show()
        }
    }
}
