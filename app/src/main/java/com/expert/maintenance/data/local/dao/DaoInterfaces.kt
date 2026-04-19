package com.expert.maintenance.data.local.dao

import androidx.room.*
import com.expert.maintenance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Employee Data Access Object
 */
@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employes ORDER BY nom, prenom")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employes WHERE id = :id")
    suspend fun getEmployeeById(id: Int): Employee?

    @Query("SELECT * FROM employes WHERE login = :login AND pwd = :password AND actif = 1")
    suspend fun authenticate(login: String, password: String): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(employee: Employee)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<Employee>)

    @Update
    suspend fun update(employee: Employee)

    @Delete
    suspend fun delete(employee: Employee)

    @Query("DELETE FROM employes")
    suspend fun deleteAll()

    @Query("SELECT * FROM employes WHERE valsync > :lastSync")
    suspend fun getEmployeesToUpdate(lastSync: Int): List<Employee>
}

/**
 * Client Data Access Object
 */
@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY nom")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Int): Client?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: Client)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<Client>)

    @Update
    suspend fun update(client: Client)

    @Delete
    suspend fun delete(client: Client)

    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    @Query("SELECT * FROM clients WHERE valsync > :lastSync")
    suspend fun getClientsToUpdate(lastSync: Int): List<Client>
}

/**
 * Site Data Access Object
 */
@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY ville, rue")
    fun getAllSites(): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getSiteById(id: Int): Site?

    @Query("SELECT * FROM sites WHERE client_id = :clientId")
    suspend fun getSitesByClientId(clientId: Int): List<Site>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sites: List<Site>)

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)

    @Query("DELETE FROM sites")
    suspend fun deleteAll()

    @Query("SELECT * FROM sites WHERE valsync > :lastSync")
    suspend fun getSitesToUpdate(lastSync: Int): List<Site>
}

/**
 * Priority Data Access Object
 */
@Dao
interface PriorityDao {
    @Query("SELECT * FROM priorites ORDER BY id")
    fun getAllPriorities(): Flow<List<Priority>>

    @Query("SELECT * FROM priorites WHERE id = :id")
    suspend fun getPriorityById(id: Int): Priority?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priority: Priority)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(priorities: List<Priority>)

    @Update
    suspend fun update(priority: Priority)

    @Delete
    suspend fun delete(priority: Priority)

    @Query("DELETE FROM priorites")
    suspend fun deleteAll()

    @Query("SELECT * FROM priorites WHERE valsync > :lastSync")
    suspend fun getPrioritiesToUpdate(lastSync: Int): List<Priority>
}

/**
 * Intervention Data Access Object
 */
@Dao
interface InterventionDao {
    @Query("SELECT * FROM interventions ORDER BY datedebut DESC, heuredebutplan DESC")
    fun getAllInterventions(): Flow<List<Intervention>>

    @Query("SELECT * FROM interventions WHERE id = :id")
    suspend fun getInterventionById(id: Int): Intervention?

    @Query("SELECT * FROM interventions WHERE site_id = :siteId ORDER BY datedebut DESC")
    fun getInterventionsBySite(siteId: Int): Flow<List<Intervention>>

    @Query("""
        SELECT i.* FROM interventions i
        INNER JOIN employes_interventions ei ON i.id = ei.intervention_id
        WHERE ei.employe_id = :employeeId
        ORDER BY i.datedebut DESC, i.heuredebutplan DESC
    """)
    fun getInterventionsByEmployee(employeeId: Int): Flow<List<Intervention>>

    @Query("""
        SELECT i.* FROM interventions i
        INNER JOIN employes_interventions ei ON i.id = ei.intervention_id
        WHERE ei.employe_id = :employeeId AND i.datedebut = :date
        ORDER BY i.heuredebutplan
    """)
    fun getInterventionsByEmployeeAndDate(employeeId: Int, date: String): Flow<List<Intervention>>

    // Temporary query for testing - shows all interventions for a date (ignores employee filter)
    @Query("""
        SELECT i.* FROM interventions i
        WHERE i.datedebut = :date
        ORDER BY i.heuredebutplan
    """)
    fun getInterventionsByDate(date: String): Flow<List<Intervention>>

    @Query("""
        SELECT i.* FROM interventions i
        INNER JOIN employes_interventions ei ON i.id = ei.intervention_id
        WHERE ei.employe_id = :employeeId
        AND i.datedebut >= :startDate AND i.datedebut <= :endDate
        ORDER BY i.datedebut, i.heuredebutplan
    """)
    fun getInterventionsByEmployeeAndDateRange(
        employeeId: Int,
        startDate: String,
        endDate: String
    ): Flow<List<Intervention>>

    @Query("SELECT * FROM interventions WHERE terminee = 0 ORDER BY datedebut, heuredebutplan")
    fun getPendingInterventions(): Flow<List<Intervention>>

    @Query("SELECT * FROM interventions WHERE terminee = 1 ORDER BY datedebut DESC")
    fun getCompletedInterventions(): Flow<List<Intervention>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(intervention: Intervention)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(interventions: List<Intervention>)

    @Update
    suspend fun update(intervention: Intervention)

    @Delete
    suspend fun delete(intervention: Intervention)

    @Query("DELETE FROM interventions")
    suspend fun deleteAll()

    @Query("SELECT * FROM interventions WHERE valsync > :lastSync")
    suspend fun getInterventionsToUpdate(lastSync: Int): List<Intervention>

    @Query("UPDATE interventions SET terminee = :completed, dateterminaison = :completionDate, valsync = valsync + 1 WHERE id = :id")
    suspend fun markAsCompleted(id: Int, completed: Boolean, completionDate: String)
}

/**
 * Task Data Access Object
 */
@Dao
interface TaskDao {
    @Query("SELECT * FROM taches ORDER BY nom")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM taches WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Query("SELECT * FROM taches WHERE intervention_id = :interventionId ORDER BY nom")
    fun getTasksByIntervention(interventionId: Int): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM taches WHERE intervention_id = :interventionId")
    suspend fun deleteTasksByIntervention(interventionId: Int)

    @Query("DELETE FROM taches")
    suspend fun deleteAll()

    @Query("SELECT * FROM taches WHERE valsync > :lastSync")
    suspend fun getTasksToUpdate(lastSync: Int): List<Task>
}

/**
 * Image Data Access Object
 */
@Dao
interface ImageDao {
    @Query("SELECT * FROM images ORDER BY dateCapture DESC")
    fun getAllImages(): Flow<List<Image>>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getImageById(id: Int): Image?

    @Query("SELECT * FROM images WHERE intervention_id = :interventionId ORDER BY dateCapture DESC")
    fun getImagesByIntervention(interventionId: Int): Flow<List<Image>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: Image)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<Image>)

    @Update
    suspend fun update(image: Image)

    @Delete
    suspend fun delete(image: Image)

    @Query("DELETE FROM images WHERE intervention_id = :interventionId")
    suspend fun deleteImagesByIntervention(interventionId: Int)

    @Query("DELETE FROM images")
    suspend fun deleteAll()

    @Query("SELECT * FROM images WHERE valsync > :lastSync")
    suspend fun getImagesToUpdate(lastSync: Int): List<Image>
}

/**
 * Contract Data Access Object
 */
@Dao
interface ContractDao {
    @Query("SELECT * FROM contrats ORDER BY datedebut DESC")
    fun getAllContracts(): Flow<List<Contract>>

    @Query("SELECT * FROM contrats WHERE id = :id")
    suspend fun getContractById(id: Int): Contract?

    @Query("SELECT * FROM contrats WHERE client_id = :clientId")
    suspend fun getContractsByClientId(clientId: Int): List<Contract>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contract: Contract)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contracts: List<Contract>)

    @Update
    suspend fun update(contract: Contract)

    @Delete
    suspend fun delete(contract: Contract)

    @Query("DELETE FROM contrats")
    suspend fun deleteAll()

    @Query("SELECT * FROM contrats WHERE valsync > :lastSync")
    suspend fun getContractsToUpdate(lastSync: Int): List<Contract>
}

/**
 * Employee-Intervention Relationship Data Access Object
 */
@Dao
interface EmployeeInterventionDao {
    @Query("SELECT * FROM employes_interventions")
    fun getAllRelationships(): Flow<List<EmployeeIntervention>>

    @Query("SELECT * FROM employes_interventions WHERE employe_id = :employeeId")
    suspend fun getInterventionsForEmployee(employeeId: Int): List<EmployeeIntervention>

    @Query("SELECT * FROM employes_interventions WHERE intervention_id = :interventionId")
    suspend fun getEmployeesForIntervention(interventionId: Int): List<EmployeeIntervention>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: EmployeeIntervention)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relations: List<EmployeeIntervention>)

    @Delete
    suspend fun delete(relation: EmployeeIntervention)

    @Query("DELETE FROM employes_interventions WHERE employe_id = :employeeId")
    suspend fun deleteByEmployeeId(employeeId: Int)

    @Query("DELETE FROM employes_interventions WHERE intervention_id = :interventionId")
    suspend fun deleteByInterventionId(interventionId: Int)

    @Query("DELETE FROM employes_interventions")
    suspend fun deleteAll()
}
