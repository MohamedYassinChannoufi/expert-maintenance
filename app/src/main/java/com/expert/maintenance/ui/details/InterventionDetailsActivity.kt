package com.expert.maintenance.ui.details

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.app.Activity
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import android.util.Log
import com.expert.maintenance.R
import com.expert.maintenance.adapters.PhotoAdapter
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.local.entity.Image
import com.expert.maintenance.data.local.entity.Intervention
import com.expert.maintenance.data.local.entity.Site
import com.expert.maintenance.data.local.entity.Client
import com.expert.maintenance.databinding.ActivityInterventionDetailsBinding
import com.expert.maintenance.ui.ImageCaptureActivity

import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InterventionDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInterventionDetailsBinding
    private lateinit var database: AppDatabase
    private lateinit var photoAdapter: PhotoAdapter
    private var interventionId: Int = 0
    private var currentIntervention: Intervention? = null
    private var currentSite: Site? = null
    private var currentClient: Client? = null

    // Activity result launcher for photo capture
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("PHOTO_DEBUG", "Retour capture photo, resultCode=$result")
        if (result.resultCode == RESULT_OK) {
            Log.d("PHOTO_DEBUG", "Photo capturée avec succès, rechargement...")
            loadPhotos()
        }
    }

    // Activity result launcher for choosing photo from gallery
    private val choosePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("PHOTO_DEBUG", "Photo sélectionnée depuis galerie: $it")
            saveGalleryPhoto(it)
        }
    }

    companion object {
        const val EXTRA_INTERVENTION_ID = "intervention_id"
        private const val API_BASE_URL = "http://192.168.100.19/ExpertMaintenance/backend/api.php"
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInterventionDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        interventionId = intent.getIntExtra(EXTRA_INTERVENTION_ID, 0)

        if (interventionId == 0) {
            Toast.makeText(this, "Intervention ID invalide", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupPhotoGallery()
        loadInterventionDetails()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // Tabs removed - using simple scrollable layout instead

    // Tab content methods removed - all content is visible in scrollable layout

    /**
     * Download images from server and save to local SQLite
     * This ensures photos taken on other devices or previously uploaded are available locally
     */
    private fun downloadImagesFromServer() {
        lifecycleScope.launch {
            try {
                Log.d("PHOTO_DEBUG", "Téléchargement images depuis serveur pour interventionId=$interventionId")

                val url = URL("$API_BASE_URL?action=get_images_for_intervention&intervention_id=$interventionId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.optBoolean("success", false)) {
                        val imagesArray = jsonResponse.getJSONArray("images")
                        Log.d("PHOTO_DEBUG", "${imagesArray.length()} images trouvées sur le serveur")

                        var downloadedCount = 0
                        for (i in 0 until imagesArray.length()) {
                            val imageObj = imagesArray.getJSONObject(i)
                            val imgBase64 = imageObj.optString("img_base64", "")

                            if (imgBase64.isNotEmpty()) {
                                // Decode Base64 to byte array
                                val byteArray = Base64.decode(imgBase64, Base64.NO_WRAP)

                                val imageName = Image(
                                    id = imageObj.optInt("id", 0),
                                    nom = imageObj.optString("nom", "IMG_${System.currentTimeMillis()}.jpg"),
                                    img = byteArray,
                                    dateCapture = imageObj.optString("dateCapture", ""),
                                    interventionId = interventionId,
                                    valsync = imageObj.optInt("valsync", 1)
                                )

                                // Insert or replace in local database
                                database.imageDao().insert(imageName)
                                downloadedCount++
                                Log.d("PHOTO_DEBUG", "Image téléchargée: ${imageName.nom} (${byteArray.size} bytes)")
                            }
                        }

                        Log.d("PHOTO_DEBUG", "$downloadedCount images téléchargées et sauvegardées localement")

                        // Reload photos from local database
                        loadPhotos()
                    }
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("PHOTO_DEBUG", "Erreur téléchargement images: ${e.message}", e)
                // Silently fail - photos are not critical, will try again next time
            }
        }
    }

    private fun setupPhotoGallery() {
        // Initialize photo adapter
        photoAdapter = PhotoAdapter()

        // Setup RecyclerView for photos
        binding.recyclerPhotos.apply {
            layoutManager = GridLayoutManager(this@InterventionDetailsActivity, 3)
            adapter = photoAdapter
        }

        // Handle photo click to view full size
        photoAdapter.setOnPhotoClickListener { photo ->
            // TODO: Show full size image in dialog
            Toast.makeText(this, "Photo: ${photo.nom}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            try {
                Log.d("PHOTO_DEBUG", "Chargement photos pour interventionId=$interventionId")
                val photos = database.imageDao().getImagesByIntervention(interventionId).first()
                Log.d("PHOTO_DEBUG", "${photos.size} photos trouvées")

                if (photos.isEmpty()) {
                    binding.tvNoPhotos.visibility = View.VISIBLE
                    binding.recyclerPhotos.visibility = View.GONE
                    Log.d("PHOTO_DEBUG", "Aucune photo - affichage du message vide")
                    Log.d("PHOTO_DEBUG", "Tentative de téléchargement depuis le serveur...")
                    // No photos locally, try to download from server
                    downloadImagesFromServer()
                } else {
                    binding.tvNoPhotos.visibility = View.GONE
                    binding.recyclerPhotos.visibility = View.VISIBLE
                    photoAdapter.submitList(photos)
                    Log.d("PHOTO_DEBUG", "Photos affichées dans le RecyclerView")

                    // Log photo details
                    photos.forEachIndexed { index, photo ->
                        Log.d("PHOTO_DEBUG", "Photo $index: nom=${photo.nom}, taille=${photo.img?.size ?: 0} bytes")
                    }
                }
            } catch (e: Exception) {
                Log.e("PHOTO_DEBUG", "Erreur chargement photos: ${e.message}", e)
                // Silently fail - photos are not critical
            }
        }
    }

    private fun saveGalleryPhoto(uri: Uri) {
        lifecycleScope.launch {
            try {
                Log.d("PHOTO_DEBUG", "Sauvegarde photo galerie: $uri")

                // Read image from URI
                val inputStream = contentResolver.openInputStream(uri)
                val byteArray = inputStream?.use { it.readBytes() }

                if (byteArray == null || byteArray.isEmpty()) {
                    Log.e("PHOTO_DEBUG", "Image bytes are empty from gallery")
                    Toast.makeText(
                        this@InterventionDetailsActivity,
                        "Erreur: Image vide",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                Log.d("PHOTO_DEBUG", "Taille image galerie: ${byteArray.size} bytes")

                // Create image entity
                val imageName = Image(
                    id = 0,
                    nom = "GAL_${System.currentTimeMillis()}.jpg",
                    img = byteArray,
                    dateCapture = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date()),
                    interventionId = interventionId,
                    valsync = 0
                )

                Log.d("PHOTO_DEBUG", "Insertion en BDD...")
                database.imageDao().insert(imageName)
                Log.d("PHOTO_DEBUG", "Insertion réussie!")

                Toast.makeText(
                    this@InterventionDetailsActivity,
                    "Photo ajoutée avec succès",
                    Toast.LENGTH_SHORT
                ).show()

                // Reload photos
                loadPhotos()

            } catch (e: Exception) {
                Log.e("PHOTO_DEBUG", "Erreur sauvegarde galerie: ${e.message}", e)
                Toast.makeText(
                    this@InterventionDetailsActivity,
                    "Erreur: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupListeners() {
        binding.switchCompleted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                updateInterventionCompletion(true)
            } else {
                showUncheckConfirmation()
            }
        }

        binding.btnOpenMap.setOnClickListener {
            openGoogleMaps()
        }

        binding.btnTakePhoto.setOnClickListener {
            val intent = Intent(this@InterventionDetailsActivity, ImageCaptureActivity::class.java)
            intent.putExtra("intervention_id", interventionId)
            takePhotoLauncher.launch(intent)
        }

        binding.btnChoosePhoto.setOnClickListener {
            Log.d("PHOTO_DEBUG", "Ouverture galerie photo")
            choosePhotoLauncher.launch("image/*")
        }

        binding.btnHistory.setOnClickListener {
            openHistory()
        }
    }

    private fun loadInterventionDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                currentIntervention = database.interventionDao().getInterventionById(interventionId)

                currentIntervention?.let { intervention ->
                    // Load site and client info
                    currentSite = database.siteDao().getSiteById(intervention.siteId)
                    currentSite?.let { site ->
                        currentClient = database.clientDao().getClientById(site.clientId)
                    }

                    displayInterventionDetails(intervention)
                }

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@InterventionDetailsActivity,
                    "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE

                // Load photos after intervention details
                Log.d("PHOTO_DEBUG", "Chargement des photos après détails intervention")
                loadPhotos()

                // Also try to download from server (in case local DB is outdated)
                Log.d("PHOTO_DEBUG", "Vérification des images sur le serveur...")
                downloadImagesFromServer()
            }
        }
    }

    private fun displayInterventionDetails(intervention: Intervention) {
        // Toolbar title
        binding.toolbar.title = "Intervention n° ${intervention.id}"

        // Header card
        binding.tvInterventionTitle.text = intervention.titre.uppercase(Locale.FRENCH)

        // Priority chip
        binding.chipPriority.text = when (intervention.prioriteId) {
            1 -> getString(R.string.priority_normale)
            2 -> getString(R.string.priority_urgente)
            3 -> getString(R.string.priority_critique)
            else -> getString(R.string.priority_normale)
        }

        // Set chip color based on priority
        val chipColor = when (intervention.prioriteId) {
            1 -> R.color.priority_normal
            2 -> R.color.priority_urgent
            3 -> R.color.priority_critical
            else -> R.color.priority_normal
        }
        binding.chipPriority.setChipBackgroundColorResource(chipColor)

        // Date and time
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
        val plannedDate = parseDate(intervention.dateplanification)
        binding.tvPlannedDate.text = dateFormat.format(plannedDate)
        binding.tvTimeRange.text = "${intervention.heuredebutplan.substring(0, 5)} - ${intervention.heurefinplan.substring(0, 5)}"

        // Completion status
        binding.switchCompleted.isChecked = intervention.terminee

        // Client info
        currentClient?.let { client ->
            binding.tvClientName.text = "Société: ${client.nom}"
            binding.tvClientContact.text = "Contact: ${client.contact}"
            binding.tvClientPhone.text = "Tél: ${client.tel}"
            binding.tvClientEmail.text = "Email: ${client.email}"
        }

        // Site address
        currentSite?.let { site ->
            binding.tvSiteAddress.text = "Adresse: ${site.rue}, ${site.codepostal} ${site.ville}"
        }

        currentIntervention = intervention
    }

    private fun parseDate(dateStr: String): java.util.Date {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).parse(dateStr) ?: java.util.Date()
        } catch (e: Exception) {
            java.util.Date()
        }
    }

    private fun updateInterventionCompletion(isCompleted: Boolean) {
        lifecycleScope.launch {
            try {
                currentIntervention?.let { intervention ->
                    val updatedIntervention = intervention.copy(
                        terminee = isCompleted,
                        dateterminaison = if (isCompleted) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(java.util.Date())
                        } else "0000-00-00",
                        valsync = intervention.valsync + 1
                    )

                    database.interventionDao().update(updatedIntervention)
                    currentIntervention = updatedIntervention

                    Toast.makeText(
                        this@InterventionDetailsActivity,
                        if (isCompleted) getString(R.string.intervention_completed)
                        else getString(R.string.intervention_pending),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InterventionDetailsActivity,
                    "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUncheckConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_uncheck_title)
            .setMessage(R.string.confirm_uncheck_message)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                updateInterventionCompletion(false)
            }
            .setNegativeButton(R.string.btn_no) { dialog, _ ->
                binding.switchCompleted.isChecked = true
                dialog.dismiss()
            }
            .show()
    }

    private fun openGoogleMaps() {
        currentSite?.let { site ->
            try {
                val gmmIntentUri = Uri.parse("geo:${site.latitude},${site.longitude}?q=${site.latitude},${site.longitude}(${site.rue})")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                // Check if Google Maps is installed
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // Open in browser if Google Maps app is not installed
                    val browserIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps?q=${site.latitude},${site.longitude}"))
                    startActivity(browserIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir la carte", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Adresse non disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openHistory() {
        currentSite?.let { site ->
            val intent = Intent(this, com.expert.maintenance.ui.history.HistoryActivity::class.java).apply {
                putExtra(com.expert.maintenance.ui.history.HistoryActivity.EXTRA_SITE_ID, site.id)
                putExtra(com.expert.maintenance.ui.history.HistoryActivity.EXTRA_SITE_NAME, site.rue)
            }
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "Site non disponible", Toast.LENGTH_SHORT).show()
        }
    }
}
