package com.expert.maintenance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Employee entity - represents a technician/worker
 */
@Entity(tableName = "employes")
data class Employee(
    @PrimaryKey val id: Int,
    val login: String,
    val pwd: String,
    val prenom: String,
    val nom: String,
    val email: String,
    val actif: Boolean,
    val valsync: Int
)

/**
 * Client entity - represents a customer company
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey val id: Int,
    val nom: String,
    val adresse: String,
    val tel: String,
    val fax: String,
    val email: String,
    val contact: String,
    val telcontact: String,
    val valsync: Int
)

/**
 * Site entity - represents a work location
 */
@Entity(
    tableName = "sites",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("client_id")]
)
data class Site(
    @PrimaryKey val id: Int,
    val longitude: Double,
    val latitude: Double,
    val adresse: String,
    val rue: String,
    val codepostal: Int,
    val ville: String,
    val contact: String,
    val telcontact: String,
    @ColumnInfo(name = "client_id") val clientId: Int,
    val valsync: Int
)

/**
 * Priority entity - represents intervention priority levels
 */
@Entity(tableName = "priorites")
data class Priority(
    @PrimaryKey val id: Int,
    val nom: String,
    val valsync: Int
)

/**
 * Intervention entity - represents a maintenance task
 */
@Entity(
    tableName = "interventions",
    foreignKeys = [
        ForeignKey(
            entity = Priority::class,
            parentColumns = ["id"],
            childColumns = ["priorite_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Site::class,
            parentColumns = ["id"],
            childColumns = ["site_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("priorite_id"), Index("site_id")]
)
data class Intervention(
    @PrimaryKey val id: Int,
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
    @ColumnInfo(name = "priorite_id") val prioriteId: Int,
    @ColumnInfo(name = "site_id") val siteId: Int,
    val valsync: Int
)

/**
 * Task entity - represents specific tasks within an intervention
 */
@Entity(
    tableName = "taches",
    foreignKeys = [
        ForeignKey(
            entity = Intervention::class,
            parentColumns = ["id"],
            childColumns = ["intervention_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("intervention_id")]
)
data class Task(
    @PrimaryKey val id: Int,
    val refernce: String,
    val nom: String,
    val duree: Double,
    val prixheure: Double,
    val dateaction: String,
    @ColumnInfo(name = "intervention_id") val interventionId: Int,
    val valsync: Int
)

/**
 * Image entity - represents photos taken during interventions
 */
@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = Intervention::class,
            parentColumns = ["id"],
            childColumns = ["intervention_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("intervention_id")]
)
data class Image(
    @PrimaryKey val id: Int,
    val nom: String,
    val img: ByteArray?,
    val dateCapture: String,
    @ColumnInfo(name = "intervention_id") val interventionId: Int,
    val valsync: Int
)

/**
 * Contract entity - represents service contracts
 */
@Entity(
    tableName = "contrats",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("client_id")]
)
data class Contract(
    @PrimaryKey val id: Int,
    val datedebut: String,
    val datefin: String,
    val redevence: Double,
    @ColumnInfo(name = "client_id") val clientId: Int,
    val valsync: Int
)

/**
 * Employee-Intervention junction entity
 */
@Entity(
    tableName = "employes_interventions",
    foreignKeys = [
        ForeignKey(
            entity = Employee::class,
            parentColumns = ["id"],
            childColumns = ["employe_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Intervention::class,
            parentColumns = ["id"],
            childColumns = ["intervention_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("employe_id"), Index("intervention_id")]
)
data class EmployeeIntervention(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "employe_id") val employeId: Int,
    @ColumnInfo(name = "intervention_id") val interventionId: Int
)
