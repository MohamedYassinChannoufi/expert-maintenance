package com.expert.maintenance.data

import android.content.Context
import android.util.Log
import com.expert.maintenance.data.local.dao.*
import com.expert.maintenance.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

class SyncManager(
    private val context: Context,
    private val employeeDao: EmployeeDao,
    private val clientDao: ClientDao,
    private val siteDao: SiteDao,
    private val interventionDao: InterventionDao,
    private val taskDao: TaskDao,
    private val priorityDao: PriorityDao,
    private val imageDao: ImageDao,
    private val employeeInterventionDao: EmployeeInterventionDao
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val API_BASE_URL = "http://192.168.100.19/ExpertMaintenance/backend/api.php"
        const val PREFS_NAME = "expert_maintenance_prefs"
        const val KEY_LAST_SYNC = "last_sync_timestamp"
        const val KEY_EMPLOYEE_ID = "current_employee_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getLastSyncTimestamp(): Int {
        return prefs.getInt(KEY_LAST_SYNC, 0)
    }

    private fun saveLastSyncTimestamp(timestamp: Int) {
        prefs.edit().putInt(KEY_LAST_SYNC, timestamp).apply()
    }

    fun getCurrentEmployeeId(): Int {
        return prefs.getInt(KEY_EMPLOYEE_ID, 0)
    }

    fun saveCurrentEmployeeId(employeeId: Int) {
        prefs.edit().putInt(KEY_EMPLOYEE_ID, employeeId).apply()
    }

    fun clearEmployeeData() {
        prefs.edit().clear().apply()
    }

    fun resetSyncTimestamp() {
        saveLastSyncTimestamp(0)
        Log.d(TAG, "Timestamp de synchronisation réinitialisé à 0")
    }

    suspend fun performFullSync(): SyncResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val lastSync = getLastSyncTimestamp()
            val employeeId = getCurrentEmployeeId()

            val fullUrl = "$API_BASE_URL?action=full_sync&last_sync=$lastSync&employee_id=$employeeId"
            Log.d(TAG, "URL de sync: $fullUrl")

            val url = URL(fullUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d(TAG, "Code de réponse HTTP: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection)
                Log.d(TAG, "Réponse reçue: ${response.length} caractères")
                return@withContext processSyncResponse(response)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Aucun détail"
                } catch (e: Exception) {
                    "Impossible de lire"
                }
                Log.e(TAG, "Erreur serveur $responseCode: $errorBody")
                return@withContext SyncResult(false, "Erreur serveur: $responseCode")
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Échec de connexion: ${e.message}")
            return@withContext SyncResult(false, "Impossible de se connecter au serveur")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Hôte inconnu: ${e.message}")
            return@withContext SyncResult(false, "Serveur introuvable")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout: ${e.message}")
            return@withContext SyncResult(false, "Délai de connexion dépassé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de sync: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message ?: "null"}")
            return@withContext SyncResult(false, "Erreur: ${e.message ?: "inconnue"}")
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun processSyncResponse(response: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val jsonObject = JSONObject(response)
            val success = jsonObject.getBoolean("success")

            if (!success) {
                return@withContext SyncResult(false, jsonObject.optString("error", "Unknown error"))
            }

            val dataObject = jsonObject.getJSONObject("data")
            val timestamp = jsonObject.optInt("timestamp", (System.currentTimeMillis() / 1000).toInt())

            // IMPORTANT: Insert in correct order to respect foreign key constraints
            // 1. Parent tables first (no foreign keys)
            if (dataObject.has("employees")) {
                syncEmployees(dataObject.getJSONArray("employees"))
            }
            if (dataObject.has("clients")) {
                syncClients(dataObject.getJSONArray("clients"))
            }
            // 2. Sites (references clients - must be after clients)
            if (dataObject.has("sites")) {
                syncSites(dataObject.getJSONArray("sites"))
            }
            // 3. Priorities (no foreign keys)
            if (dataObject.has("priorities")) {
                syncPriorities(dataObject.getJSONArray("priorities"))
            }
            // 4. Interventions (references sites and priorities - must be after both)
            if (dataObject.has("interventions")) {
                syncInterventions(dataObject.getJSONArray("interventions"))
            }
            // 5. Tasks (references interventions - must be after interventions)
            if (dataObject.has("tasks")) {
                syncTasks(dataObject.getJSONArray("tasks"))
            }
            // 6. Images (references interventions - must be after interventions)
            if (dataObject.has("images")) {
                syncImagesMetadata(dataObject.getJSONArray("images"))
            }

            // 7. Upload local images to server (NEW - for photos taken on phone)
            uploadImagesToServer()

            saveLastSyncTimestamp(timestamp)
            Log.d(TAG, "Synchronisation terminée avec succès")

            return@withContext SyncResult(true, "Sync successful")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du traitement: ${e.message}")
            e.printStackTrace()
            return@withContext SyncResult(false, "Erreur: ${e.message}")
        }
    }


    private suspend fun syncEmployees(jsonArray: JSONArray) {
        val employees = mutableListOf<Employee>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            employees.add(
                Employee(
                    id = obj.getInt("id"),
                    login = obj.getString("login"),
                    pwd = obj.getString("pwd"),
                    prenom = obj.getString("prenom"),
                    nom = obj.getString("nom"),
                    email = obj.getString("email"),
                    actif = obj.getInt("actif") == 1,
                    valsync = obj.getInt("valsync")
                )
            )
        }
        employeeDao.insertAll(employees)
        Log.d(TAG, "${employees.size} employés synchronisés")
    }

    private suspend fun syncClients(jsonArray: JSONArray) {
        val clients = mutableListOf<Client>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            clients.add(
                Client(
                    id = obj.getInt("id"),
                    nom = obj.getString("nom"),
                    adresse = obj.getString("adresse"),
                    tel = obj.getString("tel"),
                    fax = obj.getString("fax"),
                    email = obj.getString("email"),
                    contact = obj.getString("contact"),
                    telcontact = obj.getString("telcontact"),
                    valsync = obj.getInt("valsync")
                )
            )
        }
        clientDao.insertAll(clients)
        Log.d(TAG, "${clients.size} clients synchronisés")
    }

    private suspend fun syncSites(jsonArray: JSONArray) {
        val sites = mutableListOf<Site>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            sites.add(
                Site(
                    id = obj.getInt("id"),
                    longitude = obj.getDouble("longitude"),
                    latitude = obj.getDouble("latitude"),
                    adresse = obj.getString("adresse"),
                    rue = obj.getString("rue"),
                    codepostal = obj.getInt("codepostal"),
                    ville = obj.getString("ville"),
                    contact = obj.getString("contact"),
                    telcontact = obj.getString("telcontact"),
                    clientId = obj.getInt("client_id"),
                    valsync = obj.getInt("valsync")
                )
            )
        }
        siteDao.insertAll(sites)
        Log.d(TAG, "${sites.size} sites synchronisés")
    }

    private suspend fun syncPriorities(jsonArray: JSONArray) {
        val priorities = mutableListOf<Priority>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            priorities.add(
                Priority(
                    id = obj.getInt("id"),
                    nom = obj.getString("nom"),
                    valsync = obj.getInt("valsync")
                )
            )
        }
        priorityDao.insertAll(priorities)
        Log.d(TAG, "${priorities.size} priorités synchronisées")
    }

    private suspend fun syncInterventions(jsonArray: JSONArray) {
        val interventions = mutableListOf<Intervention>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            try {
                interventions.add(
                    Intervention(
                        id = obj.getInt("id"),
                        titre = obj.getString("titre"),
                        datedebut = obj.getString("datedebut"),
                        datefin = obj.getString("datefin"),
                        heuredebutplan = obj.getString("heuredebutplan"),
                        heurefinplan = obj.getString("heurefinplan"),
                        commentaires = obj.getString("commentaires"),
                        dateplanification = obj.getString("dateplanification"),
                        heuredebuteffect = obj.getString("heuredebuteffect"),
                        heurefineffect = obj.getString("heurefineffect"),
                        terminee = obj.getInt("terminee") == 1,
                        dateterminaison = obj.getString("dateterminaison"),
                        validee = obj.getInt("validee") == 1,
                        datevalidation = obj.getString("datevalidation"),
                        prioriteId = obj.getInt("priorite_id"),
                        siteId = obj.getInt("site_id"),
                        valsync = obj.getInt("valsync")
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur intervention id=${obj.optInt("id")}: ${e.message}")
            }
        }
        if (interventions.isNotEmpty()) {
            interventionDao.insertAll(interventions)
            Log.d(TAG, "${interventions.size} interventions synchronisées")
        }
    }

    private suspend fun syncTasks(jsonArray: JSONArray) {
        val tasks = mutableListOf<Task>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            tasks.add(
                Task(
                    id = obj.getInt("id"),
                    refernce = obj.getString("refernce"),
                    nom = obj.getString("nom"),
                    duree = obj.getDouble("duree"),
                    prixheure = obj.getDouble("prixheure"),
                    dateaction = obj.getString("dateaction"),
                    interventionId = obj.getInt("intervention_id"),
                    valsync = obj.getInt("valsync")
                )
            )
        }
        if (tasks.isNotEmpty()) {
            taskDao.insertAll(tasks)
            Log.d(TAG, "${tasks.size} tâches synchronisées")
        }
    }

    private suspend fun syncImagesMetadata(jsonArray: JSONArray) {
        val images = mutableListOf<Image>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            images.add(
                Image(
                    id = obj.getInt("id"),
                    nom = obj.getString("nom"),
                    img = null,
                    dateCapture = obj.getString("dateCapture"),
                    interventionId = obj.getInt("intervention_id"),
                    valsync = obj.getInt("valsync")
                )
            )
        }
        if (images.isNotEmpty()) {
            imageDao.insertAll(images)
            Log.d(TAG, "${images.size} images synchronisées")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        return BufferedReader(InputStreamReader(connection.inputStream)).use {
            it.readText()
        }
    }

    /**
     * Upload local images (taken on phone) to the MySQL server
     * This handles the ascending sync: SQLite → MySQL
     */
    private suspend fun uploadImagesToServer() = withContext(Dispatchers.IO) {
        try {
            // Get all locally stored images
            val localImages = imageDao.getAllImages().first()

            if (localImages.isEmpty()) {
                Log.d(TAG, "Aucune image locale à uploader")
                return@withContext
            }

            Log.d(TAG, "${localImages.size} images locales trouvées pour upload")

            var uploadCount = 0
            for (image in localImages) {
                try {
                    val byteArray = image.img ?: continue

                    // Skip if already synced (valsync > 0 means it came from server)
                    // Only upload images with valsync == 0 (created locally)
                    if (image.valsync > 0) {
                        continue
                    }

                    Log.d(TAG, "Upload image: ${image.nom}, taille: ${byteArray.size} bytes, interventionId: ${image.interventionId}")

                    // Upload to server via API
                    val uploaded = uploadSingleImage(image, byteArray)
                    if (uploaded) {
                        // Mark as synced by incrementing valsync
                        imageDao.update(image.copy(valsync = image.valsync + 1))
                        uploadCount++
                        Log.d(TAG, "Image ${image.nom} uploadée avec succès")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur upload image ${image.nom}: ${e.message}")
                }
            }

            Log.d(TAG, "$uploadCount images uploadées vers le serveur")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur uploadImagesToServer: ${e.message}")
        }
    }

    /**
     * Upload a single image to the server
     * Returns true if successful
     */
    private suspend fun uploadSingleImage(image: Image, byteArray: ByteArray): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_BASE_URL?action=upload_image")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000 // Longer timeout for image upload
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Build JSON payload
            val jsonData = JSONObject().apply {
                put("id", image.id)
                put("nom", image.nom)
                put("dateCapture", image.dateCapture)
                put("intervention_id", image.interventionId)
                // Convert byte array to Base64 for JSON transmission
                val base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                put("img", base64Image)
            }

            Log.d(TAG, "Envoi image au serveur: ${jsonData.toString().substring(0, kotlin.math.min(100, jsonData.toString().length))}...")

            // Send request
            connection.outputStream.use { os ->
                os.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Réponse serveur: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(response)
                return@withContext responseJson.optBoolean("success", false)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Erreur upload: $responseCode - $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception upload: ${e.message}")
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Upload a single image to the server immediately after capture
     * This is called directly from ImageCaptureActivity after taking a photo
     *
     * @param imageData The image byte array
     * @param interventionId The intervention ID this image belongs to
     * @param imageName The name of the image file
     * @param dateCapture The capture date
     * @return true if upload was successful, false otherwise
     */
    suspend fun uploadImageToServer(
        imageData: ByteArray,
        interventionId: Int,
        imageName: String,
        dateCapture: String
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_BASE_URL?action=upload_image")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000 // Longer timeout for image upload
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Build JSON payload
            val jsonData = JSONObject().apply {
                put("nom", imageName)
                put("dateCapture", dateCapture)
                put("intervention_id", interventionId)
                // Convert byte array to Base64 for JSON transmission
                val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
                put("img", base64Image)
            }

            Log.d(TAG, "Envoi image au serveur: ${jsonData.toString().substring(0, kotlin.math.min(100, jsonData.toString().length))}...")

            // Send request
            connection.outputStream.use { os ->
                os.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Réponse serveur: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(response)
                val success = responseJson.optBoolean("success", false)
                Log.d(TAG, "Upload réussi: $success")
                return@withContext success
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Erreur upload: $responseCode - $errorBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception upload: ${e.message}", e)
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    data class SyncResult(
        val success: Boolean,
        val message: String
    )
}
