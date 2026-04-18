package com.expert.maintenance.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API Service for Expert Maintenance
 * Handles all HTTP requests to the backend PHP API
 */
interface ApiService {

    // ==================== Authentication ====================

    /**
     * Authenticate an employee
     * POST /api.php?action=authenticate
     */
    @POST("api.php?action=authenticate")
    suspend fun authenticate(@Body credentials: Map<String, String>): Response<AuthResponse>

    // ==================== Synchronization ====================

    /**
     * Full synchronization - get all data with valsync > lastSync
     * GET /api.php?action=full_sync&last_sync={lastSync}&employee_id={employeeId}
     */
    @GET("api.php?action=full_sync")
    suspend fun fullSync(
        @Query("last_sync") lastSync: Int = 0,
        @Query("employee_id") employeeId: Int
    ): Response<SyncResponse>

    /**
     * Sync employees
     * GET /api.php?action=sync_employees&last_sync={lastSync}
     */
    @GET("api.php?action=sync_employees")
    suspend fun syncEmployees(@Query("last_sync") lastSync: Int = 0): Response<SyncResponse>

    /**
     * Sync clients
     * GET /api.php?action=sync_clients&last_sync={lastSync}
     */
    @GET("api.php?action=sync_clients")
    suspend fun syncClients(@Query("last_sync") lastSync: Int = 0): Response<SyncResponse>

    /**
     * Sync sites
     * GET /api.php?action=sync_sites&last_sync={lastSync}
     */
    @GET("api.php?action=sync_sites")
    suspend fun syncSites(@Query("last_sync") lastSync: Int = 0): Response<SyncResponse>

    /**
     * Sync interventions for an employee
     * GET /api.php?action=sync_interventions&last_sync={lastSync}&employee_id={employeeId}
     */
    @GET("api.php?action=sync_interventions")
    suspend fun syncInterventions(
        @Query("last_sync") lastSync: Int = 0,
        @Query("employee_id") employeeId: Int
    ): Response<SyncResponse>

    /**
     * Sync tasks
     * GET /api.php?action=sync_tasks&last_sync={lastSync}&intervention_id={interventionId}
     */
    @GET("api.php?action=sync_tasks")
    suspend fun syncTasks(
        @Query("last_sync") lastSync: Int = 0,
        @Query("intervention_id") interventionId: Int? = null
    ): Response<SyncResponse>

    /**
     * Sync priorities
     * GET /api.php?action=sync_priorities&last_sync={lastSync}
     */
    @GET("api.php?action=sync_priorities")
    suspend fun syncPriorities(@Query("last_sync") lastSync: Int = 0): Response<SyncResponse>

    /**
     * Sync images metadata
     * GET /api.php?action=sync_images&last_sync={lastSync}
     */
    @GET("api.php?action=sync_images")
    suspend fun syncImages(@Query("last_sync") lastSync: Int = 0): Response<SyncResponse>

    // ==================== Interventions ====================

    /**
     * Get intervention details
     * GET /api.php?action=get_intervention&id={id}
     */
    @GET("api.php?action=get_intervention")
    suspend fun getIntervention(@Query("id") interventionId: Int): Response<InterventionDetailResponse>

    /**
     * Update intervention
     * POST /api.php?action=update_intervention
     */
    @POST("api.php?action=update_intervention")
    suspend fun updateIntervention(@Body intervention: Map<String, Any>): Response<BaseResponse>

    /**
     * Get intervention history for a site
     * GET /api.php?action=get_intervention_history&site_id={siteId}&limit={limit}
     */
    @GET("api.php?action=get_intervention_history")
    suspend fun getInterventionHistory(
        @Query("site_id") siteId: Int,
        @Query("limit") limit: Int = 50
    ): Response<HistoryResponse>

    // ==================== Images ====================

    /**
     * Upload image
     * POST /api.php?action=upload_image (multipart)
     */
    @Multipart
    @POST("api.php?action=upload_image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("intervention_id") interventionId: RequestBody,
        @Part("nom") name: RequestBody? = null,
        @Part("dateCapture") dateCapture: RequestBody? = null
    ): Response<UploadImageResponse>

    /**
     * Get images for intervention (metadata only)
     * GET /api.php?action=get_images&intervention_id={interventionId}
     */
    @GET("api.php?action=get_images")
    suspend fun getImages(@Query("intervention_id") interventionId: Int): Response<ImagesResponse>

    /**
     * Get image binary data
     * GET /api.php?action=get_image_binary&id={id}
     */
    @Streaming
    @GET("api.php?action=get_image_binary")
    suspend fun getImageBinary(@Query("id") imageId: Int): Response<okhttp3.ResponseBody>

    /**
     * Delete image
     * GET /api.php?action=delete_image&id={id}
     */
    @GET("api.php?action=delete_image")
    suspend fun deleteImage(@Query("id") imageId: Int): Response<BaseResponse>

    // ==================== Response Data Classes ====================

    data class AuthResponse(
        val success: Boolean,
        val employee: EmployeeDto? = null,
        val token: String? = null,
        val error: String? = null
    )

    data class SyncResponse(
        val success: Boolean,
        val data: SyncData? = null,
        val employees: List<EmployeeDto>? = null,
        val clients: List<ClientDto>? = null,
        val sites: List<SiteDto>? = null,
        val interventions: List<InterventionDto>? = null,
        val tasks: List<TaskDto>? = null,
        val priorities: List<PriorityDto>? = null,
        val images: List<ImageMetadataDto>? = null,
        val timestamp: Long? = null,
        val error: String? = null
    )

    data class SyncData(
        val employees: List<EmployeeDto>? = null,
        val clients: List<ClientDto>? = null,
        val sites: List<SiteDto>? = null,
        val interventions: List<InterventionDto>? = null,
        val tasks: List<TaskDto>? = null,
        val priorities: List<PriorityDto>? = null,
        val images: List<ImageMetadataDto>? = null
    )

    data class InterventionDetailResponse(
        val success: Boolean,
        val intervention: InterventionDetailDto? = null,
        val error: String? = null
    )

    data class HistoryResponse(
        val success: Boolean,
        val history: List<InterventionDto>? = null,
        val error: String? = null
    )

    data class UploadImageResponse(
        val success: Boolean,
        val message: String? = null,
        val image_id: Int? = null,
        val error: String? = null
    )

    data class ImagesResponse(
        val success: Boolean,
        val images: List<ImageMetadataDto>? = null,
        val error: String? = null
    )

    data class BaseResponse(
        val success: Boolean,
        val message: String? = null,
        val error: String? = null
    )

    // ==================== DTOs ====================

    data class EmployeeDto(
        val id: Int,
        val login: String,
        val pwd: String,
        val prenom: String,
        val nom: String,
        val email: String,
        val actif: Boolean,
        val valsync: Int
    )

    data class ClientDto(
        val id: Int,
        val nom: String,
        val adresse: String,
        val tel: String,
        val fax: String,
        val email: String,
        val contact: String,
        val telcontact: String,
        val valsync: Int
    )

    data class SiteDto(
        val id: Int,
        val longitude: Double,
        val latitude: Double,
        val adresse: String,
        val rue: String,
        val codepostal: Int,
        val ville: String,
        val contact: String,
        val telcontact: String,
        val client_id: Int,
        val valsync: Int
    )

    data class InterventionDto(
        val id: Int,
        val titre: String,
        val datedebut: String,
        val datefin: String,
        val heuredebutplan: String,
        val heurefinplan: String,
        val commentaires: String,
        val dateplanification: String,
        val heuredebuteffect: String,
        val heurefineffect: String,
        val terminee: Boolean,
        val dateterminaison: String,
        val validee: Boolean,
        val datevalidation: String,
        val priorite_id: Int,
        val site_id: Int,
        val valsync: Int
    )

    data class InterventionDetailDto(
        val id: Int,
        val titre: String,
        val datedebut: String,
        val datefin: String,
        val heuredebutplan: String,
        val heurefinplan: String,
        val commentaires: String,
        val dateplanification: String,
        val heuredebuteffect: String,
        val heurefineffect: String,
        val terminee: Boolean,
        val dateterminaison: String,
        val validee: Boolean,
        val datevalidation: String,
        val priorite_id: Int,
        val priorite_nom: String? = null,
        val site_id: Int,
        val site_adresse: String? = null,
        val rue: String? = null,
        val codepostal: Int? = null,
        val ville: String? = null,
        val longitude: Double? = null,
        val latitude: Double? = null,
        val site_contact: String? = null,
        val site_telcontact: String? = null,
        val client_nom: String? = null,
        val client_adresse: String? = null,
        val client_tel: String? = null,
        val client_email: String? = null,
        val client_contact: String? = null,
        val client_telcontact: String? = null,
        val taches: List<TaskDto>? = null,
        val valsync: Int
    )

    data class TaskDto(
        val id: Int,
        val refernce: String,
        val nom: String,
        val duree: Double,
        val prixheure: Double,
        val dateaction: String,
        val intervention_id: Int,
        val valsync: Int
    )

    data class PriorityDto(
        val id: Int,
        val nom: String,
        val valsync: Int
    )

    data class ImageMetadataDto(
        val id: Int,
        val nom: String,
        val dateCapture: String,
        val intervention_id: Int,
        val valsync: Int
    )
}
