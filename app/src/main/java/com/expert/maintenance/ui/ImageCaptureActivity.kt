package com.expert.maintenance.ui

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.SyncManager
import com.expert.maintenance.data.local.dao.ImageDao
import com.expert.maintenance.data.local.entity.Image
import com.expert.maintenance.databinding.ActivityImageCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCaptureBinding
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    private var interventionId: Int = 0
    private lateinit var database: AppDatabase
    private lateinit var syncManager: SyncManager

    companion object {
        private const val TAG = "ImageCaptureActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        interventionId = intent.getIntExtra("intervention_id", 0)
        database = AppDatabase.getDatabase(this)

        // Initialize SyncManager for image upload
        syncManager = SyncManager(
            applicationContext,
            database.employeeDao(),
            database.clientDao(),
            database.siteDao(),
            database.interventionDao(),
            database.taskDao(),
            database.priorityDao(),
            database.imageDao(),
            database.employeeInterventionDao()
        )

        if (interventionId == 0) {
            Toast.makeText(this, "Intervention ID invalide", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupToolbar()

        // Request permissions before starting camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnSave.setOnClickListener {
            finish()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding.toolbar.title = "Prendre une photo"
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(binding.previewView.display.rotation)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                Log.d(TAG, "Caméra démarrée avec succès")

            } catch (e: Exception) {
                Log.e(TAG, "Erreur de démarrage de la caméra", e)
                Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create a media item for the captured image
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRENCH).format(Date()))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ExpertMaintenance")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo capturée: ${output.savedUri}")

                    // Save to database
                    lifecycleScope.launch {
                        try {
                            Log.d(TAG, "=== DÉBUT SAUVEGARDE PHOTO ===")
                            Log.d(TAG, "interventionId: $interventionId")
                            Log.d(TAG, "savedUri: ${output.savedUri}")

                            // Read the saved image file directly
                            val inputStream = contentResolver.openInputStream(output.savedUri!!)
                            val byteArray = inputStream?.use { it.readBytes() }
                            inputStream?.close()

                            Log.d(TAG, "Byte array lu: ${byteArray?.size ?: 0} bytes")

                            if (byteArray == null || byteArray.isEmpty()) {
                                Log.e(TAG, "❌ Image bytes are empty")
                                Toast.makeText(
                                    this@ImageCaptureActivity,
                                    "Erreur: Image vide",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            Log.d(TAG, "✅ Image size: ${byteArray.size} bytes")

                            val imageName = Image(
                                id = 0,
                                nom = "IMG_${System.currentTimeMillis()}.jpg",
                                img = byteArray,
                                dateCapture = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date()),
                                interventionId = interventionId,
                                valsync = 0
                            )

                            Log.d(TAG, "📝 Insertion en BDD locale...")
                            database.imageDao().insert(imageName)
                            Log.d(TAG, "✅ Insertion locale réussie!")

                            // Upload to server immediately
                            Log.d(TAG, "📤 Envoi au serveur en cours...")
                            Toast.makeText(
                                this@ImageCaptureActivity,
                                "📤 Envoi photo au serveur...",
                                Toast.LENGTH_SHORT
                            ).show()

                            val uploadSuccess = syncManager.uploadImageToServer(
                                imageData = byteArray,
                                interventionId = interventionId,
                                imageName = imageName.nom,
                                dateCapture = imageName.dateCapture
                            )

                            if (uploadSuccess) {
                                // Update local record: mark as synced
                                val syncedImage = imageName.copy(valsync = 1)
                                database.imageDao().update(syncedImage)
                                Log.d(TAG, "✅ Image synchronisée avec le serveur!")
                                Toast.makeText(
                                    this@ImageCaptureActivity,
                                    "✓ Photo enregistrée et synchronisée (${byteArray.size / 1024} KB)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Log.w(TAG, "⚠️ Photo enregistrée localement mais pas envoyée au serveur")
                                Toast.makeText(
                                    this@ImageCaptureActivity,
                                    "⚠️ Photo enregistrée (sera synchronisée plus tard)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            // Verify insertion
                            val inserted = database.imageDao().getImagesByIntervention(interventionId).first()
                            Log.d(TAG, "📊 Vérification: ${inserted.size} images dans la BDD pour interventionId=$interventionId")

                            // Return result to calling activity
                            setResult(RESULT_OK)
                            finish()

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ERREUR CRITIQUE lors de l'enregistrement en BDD", e)
                            Log.e(TAG, "   Exception type: ${e::class.java.simpleName}")
                            Log.e(TAG, "   Message: ${e.message}")
                            e.printStackTrace()
                            Toast.makeText(
                                this@ImageCaptureActivity,
                                "❌ Erreur: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Erreur de capture photo", exception)
                    Toast.makeText(
                        this@ImageCaptureActivity,
                        "Erreur de capture: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permission de caméra requise pour prendre des photos",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
