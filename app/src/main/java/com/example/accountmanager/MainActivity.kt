package com.example.accountmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var tabLayout: TabLayout
    private lateinit var viewHome: View
    private lateinit var viewSaved: View
    private lateinit var viewSettings: View
    private lateinit var viewExport: View

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etCookies: EditText
    private lateinit var etWebhook: EditText
    private lateinit var etFilename: EditText
    private lateinit var tvLogs: TextView
    private lateinit var listContainer: LinearLayout

    // Data
    private lateinit var sharedPrefs: SharedPreferences
    private val gson = Gson()
    private var accountsList = mutableListOf<AccountModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        bindViews()

        loadSettings()
        loadLocalAccounts()
        loadLogs()

        if (etPassword.text.toString().isEmpty()) {
            changePassword()
        }

        setupTabs()
        setupListeners()
        updateTabCounts()
    }

    private fun bindViews() {
        tabLayout = findViewById(R.id.tabLayout)
        viewHome = findViewById(R.id.viewHome)
        viewSaved = findViewById(R.id.viewSaved)
        viewSettings = findViewById(R.id.viewSettings)
        viewExport = findViewById(R.id.viewExport)
        listContainer = findViewById(R.id.listContainer)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etCookies = findViewById(R.id.etCookies)
        etWebhook = findViewById(R.id.etWebhook)
        etFilename = findViewById(R.id.etFilename)
        tvLogs = findViewById(R.id.tvLogs)
    }

    private fun setupTabs() {
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText("Home"))
        tabLayout.addTab(tabLayout.newTab().setText("Saved (0)"))
        tabLayout.addTab(tabLayout.newTab().setText("Settings"))
        tabLayout.addTab(tabLayout.newTab().setText("Export"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewHome.visibility = View.GONE
                viewSaved.visibility = View.GONE
                viewSettings.visibility = View.GONE
                viewExport.visibility = View.GONE

                when (tab?.position) {
                    0 -> viewHome.visibility = View.VISIBLE
                    1 -> {
                        viewSaved.visibility = View.VISIBLE
                        refreshSavedListUI()
                    }
                    2 -> viewSettings.visibility = View.VISIBLE
                    3 -> viewExport.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabCounts() {
        val count = accountsList.size
        tabLayout.getTabAt(1)?.text = "Saved ($count)"
    }

    private fun setupListeners() {
        // Password Copy
        findViewById<Button>(R.id.btnCopyPass).setOnClickListener {
            val clip = ClipData.newPlainText("Password", etPassword.text.toString())
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

        // Password Change
        findViewById<Button>(R.id.btnChangePass).setOnClickListener {
            changePassword()
            saveSettings()
        }

        // --- CLEAR INPUTS BUTTON ---
        findViewById<Button>(R.id.btnClearInputs).setOnClickListener {
            etUsername.text?.clear()
            etCookies.text?.clear()
            etUsername.requestFocus()
            Toast.makeText(this, "Inputs Cleared", Toast.LENGTH_SHORT).show()
        }

        // Save Settings
        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
        }

        // Submit
        findViewById<Button>(R.id.btnSubmit).setOnClickListener { submitData() }

        // Clear Logs
        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            tvLogs.text = ""
            saveLogsToStorage("")
        }

        // Clear Saved Accounts
        findViewById<Button>(R.id.btnClearAccounts).setOnClickListener {
            accountsList.clear()
            saveLocalAccounts()
            refreshSavedListUI()
            updateTabCounts()
            Toast.makeText(this, "All accounts cleared", Toast.LENGTH_SHORT).show()
        }

        // Download Export
        findViewById<Button>(R.id.btnDownloadExport).setOnClickListener { exportToFile() }
    }

    private fun refreshSavedListUI() {
        listContainer.removeAllViews()
        if (accountsList.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "No saved accounts yet."
            emptyText.setTextColor(0xFF94a3b8.toInt())
            emptyText.gravity = Gravity.CENTER
            listContainer.addView(emptyText)
            return
        }
        for (account in accountsList.reversed()) {
            val cardView = TextView(this)
            val shortCookie = if (account.auth_code.length > 20) "${account.auth_code.take(20)}..." else account.auth_code
            cardView.text = "User: ${account.username}\nPass: ${account.password}\nCookie: $shortCookie"
            cardView.setTextColor(0xFFe5e7eb.toInt())
            cardView.setBackgroundColor(0xFF1f2937.toInt())
            cardView.setPadding(40, 40, 40, 40)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 30)
            cardView.layoutParams = params
            listContainer.addView(cardView)
        }
    }

    private fun changePassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val dateStr = SimpleDateFormat("dd", Locale.getDefault()).format(Date())
        val length = (8..12).random()
        val letterCount = length - dateStr.length
        var result = ""
        for (i in 0 until letterCount) result += chars.random()
        etPassword.setText(result + dateStr)
    }

    private fun saveSettings() {
        sharedPrefs.edit().apply {
            putString("webhook_url", etWebhook.text.toString())
            putString("json_filename", etFilename.text.toString())
            putString("current_password", etPassword.text.toString())
        }.apply()
    }

    private fun loadSettings() {
        etWebhook.setText(sharedPrefs.getString("webhook_url", ""))
        etFilename.setText(sharedPrefs.getString("json_filename", "accounts.json"))
        etPassword.setText(sharedPrefs.getString("current_password", ""))
    }

    private fun submitData() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()
        val cookies = etCookies.text.toString()
        val webhook = etWebhook.text.toString()

        if (username.isEmpty()) { Toast.makeText(this, "Enter Username", Toast.LENGTH_SHORT).show(); return }

        val newEntry = AccountModel("", username, password, cookies)
        accountsList.add(newEntry)
        saveLocalAccounts()
        updateTabCounts()

        val rawString = "$username:$password|||$cookies||"
        val encoded = android.util.Base64.encodeToString(rawString.toByteArray(), android.util.Base64.NO_WRAP)
        val payload = "accounts=$encoded"

        appendLog("[System] Sending...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL(webhook).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain")
                conn.outputStream.write(payload.toByteArray())
                conn.outputStream.flush()
                conn.outputStream.close()

                val code = conn.responseCode
                val msg = if(code == 200) "Success" else "Error $code"
                withContext(Dispatchers.Main) {
                    appendLog("[$code] $msg")
                    resetInputs()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("[Error] ${e.message}")
                    resetInputs()
                }
            }
        }
    }

    private fun resetInputs() {
        etUsername.setText("")
        etCookies.setText("")
        changePassword()
        saveSettings()
    }

    private fun saveLocalAccounts() {
        val json = gson.toJson(accountsList)
        val filename = etFilename.text.toString()
        if (!filename.endsWith(".json")) return
        try {
            openFileOutput(filename, Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
        } catch (e: Exception) { appendLog("[Save Error] ${e.message}") }
    }

    private fun loadLocalAccounts() {
        val filename = sharedPrefs.getString("json_filename", "accounts.json") ?: "accounts.json"
        try {
            val json = openFileInput(filename).bufferedReader().readText()
            val type = object : TypeToken<MutableList<AccountModel>>() {}.type
            accountsList = gson.fromJson(json, type)
        } catch (e: Exception) { accountsList = mutableListOf() }
    }

    private fun exportToFile() {
        if (accountsList.isEmpty()) { Toast.makeText(this, "No accounts", Toast.LENGTH_SHORT).show(); return }
        val json = gson.toJson(accountsList)
        val filename = etFilename.text.toString()

        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/insta_saver")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { it.write(json.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(it, values, null, null)
                Toast.makeText(this, "Saved to Download/insta_saver", Toast.LENGTH_LONG).show()
                appendLog("[Export] Success")
            } catch (e: Exception) { Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun appendLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLogs.text = "[$ts] $msg\n" + tvLogs.text
        saveLogsToStorage(tvLogs.text.toString())
    }

    private fun saveLogsToStorage(str: String) {
        val snippet = if (str.length > 5000) str.substring(0, 5000) else str
        sharedPrefs.edit().putString("app_logs", snippet).apply()
    }

    private fun loadLogs() {
        tvLogs.text = sharedPrefs.getString("app_logs", "")
    }
}

data class AccountModel(val email: String, val username: String, val password: String, val auth_code: String)