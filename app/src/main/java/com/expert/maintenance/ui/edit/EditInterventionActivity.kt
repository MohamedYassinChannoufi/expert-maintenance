package com.expert.maintenance.ui.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.local.entity.Intervention
import com.expert.maintenance.data.local.entity.Priority
import com.expert.maintenance.databinding.ActivityEditInterventionBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditInterventionActivity - Allow users to modify intervention details
 * Fields that can be modified:
 * - Title
 * - Priority
 * - Start/End dates
 * - Planned start/end times
 * - Comments
 */
class EditInterventionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditInterventionBinding
    private lateinit var database: AppDatabase
    private var interventionId: Int = 0
    private var currentIntervention: Intervention? = null
    private var priorities: List<Priority> = listOf()
    private var selectedPriorityId: Int = 1

    // Date picker calendar
    private val calendar = Calendar.getInstance()

    // Date formats
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
    private val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.FRENCH)

    // API URL
    companion object {
        private const val API_BASE_URL = "http://192.168.100.39/ExpertMaintenance/backend/api.php"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditInterventionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get intervention ID from intent
        interventionId = intent.getIntExtra("intervention_id", 0)

        Log.d("EDIT_DEBUG", "=== EditInterventionActivity ONCREATE ===")
        Log.d("EDIT_DEBUG", "interventionId reçu: $interventionId")
        Log.d("EDIT_DEBUG", "Intent extras: ${intent.extras}")

        if (interventionId == 0) {
            Log.e("EDIT_DEBUG", "ERREUR: interventionId est 0!")
            Toast.makeText(this, "ID d'intervention invalide", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("EDIT_DEBUG", "interventionId valide: $interventionId")

        database = AppDatabase.getDatabase(this)

        setupToolbar()
        setupDatePickers()
        setupTimePickers()
        setupButtons()

        // Load priorities FIRST, then load intervention data
        loadPrioritiesAndIntervention()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadPrioritiesAndIntervention() {
        lifecycleScope.launch {
            try {
                Log.d("EDIT_DEBUG", "=== Chargement des priorités ===")

                // Load priorities first
                priorities = database.priorityDao().getAllPriorities().first()
                Log.d("EDIT_DEBUG", "Priorités chargées: ${priorities.size}")
                priorities.forEach { p ->
                    Log.d("EDIT_DEBUG", "  - Priorité: ${p.id} = ${p.nom}")
                }

                // Setup AutoCompleteTextView with priority names
                val priorityNames = priorities.map { it.nom }
                val adapter = ArrayAdapter(
                    this@EditInterventionActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    priorityNames
                )
                binding.etPriority.setAdapter(adapter)

                // Set default selection listener
                binding.etPriority.setOnItemClickListener { parent, _, position, _ ->
                    selectedPriorityId = priorities[position].id
                }

                Log.d("EDIT_DEBUG", "=== Chargement des données intervention ===")

                // Now load intervention data AFTER priorities are ready
                loadInterventionData()

            } catch (e: Exception) {
                Log.e("EDIT_DEBUG", "Erreur chargement priorités: ${e.message}", e)
                Toast.makeText(this@EditInterventionActivity,
                    "Erreur chargement priorités: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadInterventionData() {
        Log.d("EDIT_DEBUG", "=== loadInterventionData appelée ===")
        showProgress(true)

        lifecycleScope.launch {
            try {
                Log.d("EDIT_DEBUG", "Recherche intervention id=$interventionId dans la base locale")
                currentIntervention = database.interventionDao().getInterventionById(interventionId)

                if (currentIntervention == null) {
                    Log.e("EDIT_DEBUG", "❌ Intervention NON TROUVÉE dans la base locale!")
                    Log.e("EDIT_DEBUG", "   L'intervention existe-t-elle dans SQLite?")
                } else {
                    Log.d("EDIT_DEBUG", "✅ Intervention trouvée: ${currentIntervention?.titre}")
                    Log.d("EDIT_DEBUG", "   datedebut: ${currentIntervention?.datedebut}")
                    Log.d("EDIT_DEBUG",   "   datefin: ${currentIntervention?.datefin}")
                    Log.d("EDIT_DEBUG", "   heuredebutplan: ${currentIntervention?.heuredebutplan}")
                    Log.d("EDIT_DEBUG", "   heurefinplan: ${currentIntervention?.heurefinplan}")
                }

                currentIntervention?.let { intervention ->
                    Log.d("EDIT_DEBUG", "=== Remplissage du formulaire ===")

                    // Fill form fields
                    binding.etTitle.setText(intervention.titre)
                    Log.d("EDIT_DEBUG", "✓ Titre défini: ${intervention.titre}")

                    binding.etCommentaires.setText(intervention.commentaires)
                    Log.d("EDIT_DEBUG", "✓ Commentaires définis")

                    // Set dates
                    try {
                        val startDate = databaseDateFormat.parse(intervention.datedebut)
                        startDate?.let {
                            binding.etDateDebut.setText(displayDateFormat.format(it))
                            Log.d("EDIT_DEBUG", "✓ Date début: ${displayDateFormat.format(it)}")
                        }

                        val endDate = databaseDateFormat.parse(intervention.datefin)
                        endDate?.let {
                            binding.etDateFin.setText(displayDateFormat.format(it))
                            Log.d("EDIT_DEBUG", "✓ Date fin: ${displayDateFormat.format(it)}")
                        }
                    } catch (e: Exception) {
                        // If parsing fails, use raw strings
                        Log.e("EDIT_DEBUG", "Erreur parsing dates: ${e.message}")
                        binding.etDateDebut.setText(intervention.datedebut)
                        binding.etDateFin.setText(intervention.datefin)
                    }

                    // Set times
                    binding.etHeureDebut.setText(intervention.heuredebutplan)
                    binding.etHeureFin.setText(intervention.heurefinplan)
                    Log.d("EDIT_DEBUG", "✓ Heures: ${intervention.heuredebutplan} - ${intervention.heurefinplan}")

                    // Set priority
                    val priorityIndex = priorities.indexOfFirst { it.id == intervention.prioriteId }
                    Log.d("EDIT_DEBUG", "Priority index: $priorityIndex / Priorités chargées: ${priorities.size}")
                    if (priorityIndex >= 0) {
                        binding.etPriority.setText(priorities[priorityIndex].nom, false)
                        selectedPriorityId = intervention.prioriteId
                        Log.d("EDIT_DEBUG", "✓ Priorité définie: ${priorities[priorityIndex].nom}")
                    } else {
                        Log.w("EDIT_DEBUG", "⚠️ Priorité non trouvée pour id=${intervention.prioriteId}")
                    }

                    showProgress(false)
                    Log.d("EDIT_DEBUG", "✅ FORMULAIRE REMPLI AVEC SUCCÈS")
                } ?: run {
                    Log.e("EDIT_DEBUG", "❌ Intervention non trouvée pour id=$interventionId")
                    Toast.makeText(this@EditInterventionActivity,
                        "Intervention non trouvée dans la base locale", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("EDIT_DEBUG", "❌ Erreur chargement: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(this@EditInterventionActivity,
                    "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                showProgress(false)
            }
        }
    }

    private fun setupDatePickers() {
        // Date Début
        binding.etDateDebut.setOnClickListener {
            showDatePicker { selectedDate ->
                binding.etDateDebut.setText(displayDateFormat.format(selectedDate))

                // Auto-fill end date if not set
                if (binding.etDateFin.text.isNullOrBlank()) {
                    binding.etDateFin.setText(displayDateFormat.format(selectedDate))
                }
            }
        }

        // Date Fin
        binding.etDateFin.setOnClickListener {
            showDatePicker { selectedDate ->
                binding.etDateFin.setText(displayDateFormat.format(selectedDate))
            }
        }
    }

    private fun setupTimePickers() {
        // Heure Début
        binding.etHeureDebut.setOnClickListener {
            showTimePicker { selectedTime ->
                binding.etHeureDebut.setText(timeFormat.format(selectedTime))
            }
        }

        // Heure Fin
        binding.etHeureFin.setOnClickListener {
            showTimePicker { selectedTime ->
                binding.etHeureFin.setText(timeFormat.format(selectedTime))
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Calendar) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                onDateSelected(calendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(onTimeSelected: (Calendar) -> Unit) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                onTimeSelected(calendar)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24-hour format
        ).show()
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            if (validateForm()) {
                saveChanges()
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validate title
        if (binding.etTitle.text.isNullOrBlank()) {
            binding.tilTitle.error = "Le titre est requis"
            isValid = false
        } else {
            binding.tilTitle.error = null
        }

        // Validate priority
        if (binding.etPriority.text.isNullOrBlank()) {
            binding.tilPriority.error = "La priorité est requise"
            isValid = false
        } else {
            binding.tilPriority.error = null
        }

        // Validate dates
        if (binding.etDateDebut.text.isNullOrBlank()) {
            binding.tilDateDebut.error = "La date de début est requise"
            isValid = false
        } else {
            binding.tilDateDebut.error = null
        }

        if (binding.etDateFin.text.isNullOrBlank()) {
            binding.tilDateFin.error = "La date de fin est requise"
            isValid = false
        } else {
            binding.tilDateFin.error = null
        }

        // Validate times
        if (binding.etHeureDebut.text.isNullOrBlank()) {
            binding.tilHeureDebut.error = "L'heure de début est requise"
            isValid = false
        } else {
            binding.tilHeureDebut.error = null
        }

        if (binding.etHeureFin.text.isNullOrBlank()) {
            binding.tilHeureFin.error = "L'heure de fin est requise"
            isValid = false
        } else {
            binding.tilHeureFin.error = null
        }

        return isValid
    }

    private fun saveChanges() {
        showProgress(true)

        lifecycleScope.launch {
            try {
                // Parse dates from display format to database format
                val startDateStr = try {
                    val date = displayDateFormat.parse(binding.etDateDebut.text.toString())
                    databaseDateFormat.format(date!!)
                } catch (e: Exception) {
                    binding.etDateDebut.text.toString()
                }

                val endDateStr = try {
                    val date = displayDateFormat.parse(binding.etDateFin.text.toString())
                    databaseDateFormat.format(date!!)
                } catch (e: Exception) {
                    binding.etDateFin.text.toString()
                }

                // Create updated intervention
                val updatedIntervention = currentIntervention!!.copy(
                    titre = binding.etTitle.text.toString().trim(),
                    commentaires = binding.etCommentaires.text.toString().trim(),
                    datedebut = startDateStr,
                    datefin = endDateStr,
                    heuredebutplan = binding.etHeureDebut.text.toString().trim(),
                    heurefinplan = binding.etHeureFin.text.toString().trim(),
                    prioriteId = selectedPriorityId,
                    valsync = currentIntervention!!.valsync + 1 // Increment for sync
                )

                // Update local database
                database.interventionDao().update(updatedIntervention)

                // Send to server
                val uploadSuccess = uploadToServer(updatedIntervention)

                if (uploadSuccess) {
                    Toast.makeText(
                        this@EditInterventionActivity,
                        "✅ Intervention modifiée avec succès",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@EditInterventionActivity,
                        "⚠️ Modification enregistrée localement (sera synchronisée plus tard)",
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@EditInterventionActivity,
                    "❌ Erreur: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showProgress(false)
            }
        }
    }

    private suspend fun uploadToServer(intervention: Intervention): Boolean {
        return try {
            val url = URL("$API_BASE_URL?action=update_intervention")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Build JSON payload
            val jsonData = JSONObject().apply {
                put("id", intervention.id)
                put("titre", intervention.titre)
                put("datedebut", intervention.datedebut)
                put("datefin", intervention.datefin)
                put("heuredebutplan", intervention.heuredebutplan)
                put("heurefinplan", intervention.heurefinplan)
                put("commentaires", intervention.commentaires)
                put("priorite_id", intervention.prioriteId)
                put("valsync", intervention.valsync)
            }

            // Send request
            connection.outputStream.use { os ->
                os.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(response)
                val success = responseJson.optBoolean("success", false)

                if (!success) {
                    val error = responseJson.optString("error", "Erreur inconnue")
                    android.util.Log.e("EditIntervention", "Erreur serveur: $error")
                }

                success
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Erreur HTTP $responseCode"
                android.util.Log.e("EditIntervention", "Erreur upload: $errorBody")
                false
            }

        } catch (e: Exception) {
            android.util.Log.e("EditIntervention", "Exception upload: ${e.message}", e)
            false
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }
}
