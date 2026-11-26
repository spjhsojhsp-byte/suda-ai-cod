package app.suda.aicode

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Suda ai Code: ask an AI model to write code in C, C++, C#, Rust and more,
 * then copy it or push it straight into a GitHub repository.
 * Keys are typed by the user and kept only in this app's private storage.
 */
class MainActivity : Activity() {

    private data class Lang(val label: String, val ext: String)

    private val languages = listOf(
        Lang("C++", "cpp"), Lang("C", "c"), Lang("C#", "cs"), Lang("Rust", "rs"),
        Lang("Java", "java"), Lang("Kotlin", "kt"), Lang("Python", "py"),
        Lang("JavaScript", "js"), Lang("Go", "go"),
    )

    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var ghToken: EditText
    private lateinit var ghRepo: EditText
    private lateinit var ghBranch: EditText
    private lateinit var ghPath: EditText
    private lateinit var langSpinner: Spinner
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var generate: Button
    private lateinit var push: Button
    private lateinit var settingsBox: LinearLayout

    private val prefs by lazy { getSharedPreferences("suda", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadPrefs()
    }

    override fun onPause() {
        savePrefs()
        super.onPause()
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun edit(hint: String, secret: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or
            if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Suda ai Code"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "ให้ AI เขียนโค้ด C / C++ / C# และอื่น ๆ แล้วพุชขึ้น GitHub"
            textSize = 13f
        })

        val settingsToggle = Button(this).apply { text = "ตั้งค่า (API key / GitHub)" }
        root.addView(settingsToggle)

        settingsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        apiKey = edit("Anthropic API key", secret = true)
        model = edit("ชื่อโมเดล (เช่น claude-sonnet-5-5)")
        ghToken = edit("GitHub token (สิทธิ์ contents: write)", secret = true)
        ghRepo = edit("GitHub repo (เจ้าของ/ชื่อ)")
        ghBranch = edit("บรานช์ (เว้นว่าง = main)")
        ghPath = edit("ที่เก็บไฟล์ในรีโป (เว้นว่าง = ตั้งชื่ออัตโนมัติ)")
        listOf(apiKey, model, ghToken, ghRepo, ghBranch, ghPath).forEach { settingsBox.addView(it) }
        root.addView(settingsBox)
        settingsToggle.setOnClickListener {
            settingsBox.visibility = if (settingsBox.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        langSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages.map { it.label },
            )
        }
        root.addView(TextView(this).apply { text = "ภาษา"; setPadding(0, dp(12), 0, 0) })
        root.addView(langSpinner)

        prompt = EditText(this).apply {
            hint = "บอกว่าต้องการโค้ดอะไร"
            minLines = 4
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(prompt)

        generate = Button(this).apply { text = "สร้างโค้ด" }
        root.addView(generate)

        status = TextView(this).apply { textSize = 12f }
        root.addView(status)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(output)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val copy = Button(this).apply { text = "คัดลอก" }
        push = Button(this).apply { text = "พุชขึ้น GitHub" }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(push, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        generate.setOnClickListener { onGenerate() }
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", extractCode(output.text.toString())))
            toast("คัดลอกแล้ว")
        }
        push.setOnClickListener { onPush() }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun loadPrefs() {
        apiKey.setText(prefs.getString("apiKey", ""))
        model.setText(prefs.getString("model", "claude-sonnet-5-5"))
        ghToken.setText(prefs.getString("ghToken", ""))
        ghRepo.setText(prefs.getString("ghRepo", ""))
        ghBranch.setText(prefs.getString("ghBranch", ""))
        ghPath.setText(prefs.getString("ghPath", ""))
        if (apiKey.text.isEmpty()) settingsBox.visibility = View.VISIBLE
    }

    private fun savePrefs() {
        prefs.edit()
            .putString("apiKey", apiKey.text.toString().trim())
            .putString("model", model.text.toString().trim())
            .putString("ghToken", ghToken.text.toString().trim())
            .putString("ghRepo", ghRepo.text.toString().trim())
            .putString("ghBranch", ghBranch.text.toString().trim())
            .putString("ghPath", ghPath.text.toString().trim())
            .apply()
    }

    // ---------- Actions ----------

    private fun onGenerate() {
        savePrefs()
        val key = apiKey.text.toString().trim()
        val request = prompt.text.toString().trim()
        if (key.isEmpty()) { toast("ใส่ Anthropic API key ในตั้งค่าก่อน"); return }
        if (request.isEmpty()) { toast("พิมพ์สิ่งที่ต้องการก่อน"); return }
        val lang = languages[langSpinner.selectedItemPosition]
        val modelName = model.text.toString().trim().ifEmpty { "claude-sonnet-5-5" }

        generate.isEnabled = false
        status.text = "กำลังให้ AI เขียนโค้ด..."
        Thread {
            val result = try {
                callModel(key, modelName, lang.label, request)
            } catch (e: Exception) {
                "ผิดพลาด: ${e.message}"
            }
            runOnUiThread {
                output.text = result
                status.text = ""
                generate.isEnabled = true
            }
        }.start()
    }

    private fun onPush() {
        savePrefs()
        val code = extractCode(output.text.toString())
        val token = ghToken.text.toString().trim()
        val repo = ghRepo.text.toString().trim()
        if (code.isEmpty()) { toast("ยังไม่มีโค้ดให้พุช"); return }
        if (token.isEmpty() || !repo.contains("/")) { toast("ใส่ GitHub token และ repo (เจ้าของ/ชื่อ) ในตั้งค่า"); return }
        val lang = languages[langSpinner.selectedItemPosition]
        val branch = ghBranch.text.toString().trim().ifEmpty { "main" }
        val path = ghPath.text.toString().trim().ifEmpty {
            "suda/code-${System.currentTimeMillis() / 1000}.${lang.ext}"
        }

        push.isEnabled = false
        status.text = "กำลังพุชขึ้น GitHub..."
        Thread {
            val msg = try {
                pushFile(token, repo, branch, path, code)
                "พุชแล้ว: $repo/$path ($branch)"
            } catch (e: Exception) {
                "พุชไม่สำเร็จ: ${e.message}"
            }
            runOnUiThread {
                status.text = msg
                push.isEnabled = true
            }
        }.start()
    }

    // ---------- Network ----------

    private fun http(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun callModel(key: String, modelName: String, lang: String, request: String): String {
        val body = JSONObject()
            .put("model", modelName)
            .put("max_tokens", 4096)
            .put(
                "system",
                "You are Suda ai Code, a coding assistant. Write complete, working $lang code for the request. " +
                    "Reply with one fenced code block, then at most three short lines of explanation.",
            )
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", request)),
            )
        val (code, text) = http(
            "POST",
            "https://api.anthropic.com/v1/messages",
            mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json",
            ),
            body.toString(),
        )
        if (code !in 200..299) error("API $code: ${text.take(300)}")
        val content = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().ifEmpty { "(ไม่มีข้อความตอบกลับ)" }
    }

    private fun pushFile(token: String, repo: String, branch: String, path: String, content: String) {
        val url = "https://api.github.com/repos/$repo/contents/$path"
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Content-Type" to "application/json",
        )
        // Existing file? A PUT over it needs its current sha.
        val (getCode, getText) = http("GET", "$url?ref=$branch", headers, null)
        val sha = if (getCode == 200) JSONObject(getText).optString("sha") else null

        val body = JSONObject()
            .put("message", "Add $path from Suda ai Code")
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (!sha.isNullOrEmpty()) body.put("sha", sha)

        val (putCode, putText) = http("PUT", url, headers, body.toString())
        if (putCode !in 200..299) error("GitHub $putCode: ${putText.take(300)}")
    }

    /** Returns the first fenced code block of a reply, or the whole text when there is none. */
    private fun extractCode(text: String): String {
        val start = text.indexOf("```")
        if (start < 0) return text.trim()
        val afterFence = text.indexOf('\n', start)
        if (afterFence < 0) return text.trim()
        val end = text.indexOf("```", afterFence)
        return (if (end < 0) text.substring(afterFence + 1) else text.substring(afterFence + 1, end)).trim()
    }
}
