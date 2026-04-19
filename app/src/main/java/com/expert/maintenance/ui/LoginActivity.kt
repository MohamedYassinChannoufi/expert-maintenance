package com.expert.maintenance.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.SyncManager
import com.expert.maintenance.data.local.dao.EmployeeDao
import com.expert.maintenance.data.local.entity.Employee
import com.expert.maintenance.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var syncManager: SyncManager
    private lateinit var employeeDao: EmployeeDao

    companion object {
        private const val TAG = "LOGIN_DEBUG"
        // ✅ URL pour appareil physique sur même réseau WiFi
        // Pour émulateur Android: utiliser "http://10.0.2.2:80/ExpertMaintenance/backend/api.php"
        private const val API_BASE_URL = "http://10.245.206.12/ExpertMaintenance/backend/api.php"

        private const val PREFS_NAME = "expert_maintenance_prefs"
        private const val KEY_EMPLOYEE_ID = "current_employee_id"
        private const val KEY_EMPLOYEE_DATA = "employee_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        employeeDao = database.employeeDao()

        syncManager = SyncManager(
            context = this,
            employeeDao = employeeDao,
            clientDao = database.clientDao(),
            siteDao = database.siteDao(),
            interventionDao = database.interventionDao(),
            taskDao = database.taskDao(),
            priorityDao = database.priorityDao(),
            imageDao = database.imageDao(),
            employeeInterventionDao = database.employeeInterventionDao()
        )

        setupUI()
        checkExistingSession()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun checkExistingSession() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

        if (isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun attemptLogin() {
        val login = binding.etLogin.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (login.isEmpty()) {
            binding.tilLogin.error = getString(R.string.error_login_required)
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            return
        }

        binding.tvErrorMessage.visibility = View.GONE
        binding.tilLogin.error = null
        binding.tilPassword.error = null

        if (!isNetworkAvailable()) {
            attemptOfflineLogin(login, password)
            return
        }

        setLoadingState(true)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val fullUrl = "$API_BASE_URL?action=authenticate"
                    Log.d(TAG, "Tentative de connexion à: $fullUrl")
                    Log.d(TAG, "Login: $login")

                    val url = URL(fullUrl)
                    connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")

                    val jsonData = JSONObject().apply {
                        put("login", login)
                        put("password", password)
                    }

                    Log.d(TAG, "Envoi des données: $jsonData")

                    connection.outputStream.use { os ->
                        os.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                        os.flush()
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Code de réponse HTTP: $responseCode")

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use {
                            it.readText()
                        }
                        Log.d(TAG, "Réponse du serveur: $response")

                        val responseJson = JSONObject(response)
                        val success = responseJson.optBoolean("success", false)

                        if (success) {
                            Log.d(TAG, "Authentification réussie")
                            val employeeJson = responseJson.getJSONObject("employee")

                            Result.success(Employee(
                                id = employeeJson.getInt("id"),
                                login = employeeJson.getString("login"),
                                pwd = employeeJson.getString("pwd"),
                                prenom = employeeJson.getString("prenom"),
                                nom = employeeJson.getString("nom"),
                                email = employeeJson.getString("email"),
                                actif = employeeJson.getInt("actif") == 1,
                                valsync = employeeJson.getInt("valsync")
                            ))
                        } else {
                            val error = responseJson.optString("error", "Échec authentification")
                            Log.e(TAG, "Échec authentification: $error")
                            Result.failure<Employee>(Exception(error))
                        }
                    } else {
                        val errorBody = try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Aucun détail"
                        } catch (e: Exception) {
                            "Impossible de lire l'erreur"
                        }
                        Log.e(TAG, "Erreur serveur $responseCode: $errorBody")
                        Result.failure<Employee>(Exception("Erreur serveur: $responseCode"))
                    }

                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Échec de connexion: ${e.message}")
                    Log.e(TAG, "Vérifiez que:")
                    Log.e(TAG, "1. Votre téléphone est sur le même WiFi que le Mac (192.168.5.x)")
                    Log.e(TAG, "2. Apache/XAMPP est en cours d'exécution")
                    Log.e(TAG, "3. L'URL est correcte: $API_BASE_URL")
                    Result.failure<Employee>(e)
                } catch (e: java.net.UnknownHostException) {
                    Log.e(TAG, "Hôte inconnu: ${e.message}")
                    Log.e(TAG, "URL utilisée: $API_BASE_URL")
                    Result.failure<Employee>(e)
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Timeout de connexion: ${e.message}")
                    Result.failure<Employee>(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur inattendue: ${e.javaClass.simpleName}")
                    Log.e(TAG, "Message: ${e.message ?: "null"}")
                    Log.e(TAG, "Stack trace:", e)
                    Result.failure<Employee>(e)
                } finally {
                    connection?.disconnect()
                }
            }

            // Handle result on main thread
            result.fold(
                onSuccess = { employee ->
                    employeeDao.insert(employee)
                    saveSession(employee)
                    syncManager.saveCurrentEmployeeId(employee.id)
                    performInitialSync()
                },
                onFailure = { error: Throwable ->
                    when (error) {
                        is java.net.ConnectException -> {
                            showError("Impossible de se connecter au serveur\nVérifiez que vous êtes sur le même WiFi")
                        }
                        is java.net.UnknownHostException -> {
                            showError("Hôte introuvable. Vérifiez l'adresse du serveur")
                        }
                        is java.net.SocketTimeoutException -> {
                            showError("Délai de connexion dépassé. Vérifiez le réseau")
                        }
                        else -> {
                            val errorMsg = error.message ?: "Erreur inconnue"
                            showError("Erreur: $errorMsg\nVérifiez connexion réseau")
                        }
                    }
                    setLoadingState(false)
                }
            )
        }
    }

    private fun attemptOfflineLogin(login: String, password: String) {
        setLoadingState(true)
        lifecycleScope.launch {
            val employee = employeeDao.authenticate(login, password)
            if (employee != null) {
                saveSession(employee)
                syncManager.saveCurrentEmployeeId(employee.id)
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } else {
                showError("Identifiants incorrects")
                setLoadingState(false)
            }
        }
    }

    private fun performInitialSync() {
        lifecycleScope.launch {
            try {
                val result = syncManager.performFullSync()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun saveSession(employee: Employee) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val employeeJson = JSONObject().apply {
            put("id", employee.id)
            put("login", employee.login)
            put("prenom", employee.prenom)
            put("nom", employee.nom)
        }
        editor.putString(KEY_EMPLOYEE_DATA, employeeJson.toString())
        editor.putInt(KEY_EMPLOYEE_ID, employee.id)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    private fun setLoadingState(isLoading: Boolean) {
        runOnUiThread {
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            binding.tvErrorMessage.text = message
            binding.tvErrorMessage.visibility = View.VISIBLE
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isAvailable = if (android.os.Build.VERSION.SDK_INT >= 23) {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting ?: false
        }
        Log.d(TAG, "Réseau disponible: $isAvailable")
        return isAvailable
    }
}
