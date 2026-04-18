package com.expert.maintenance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.expert.maintenance.data.local.dao.ClientDao
import com.expert.maintenance.data.local.dao.ContractDao
import com.expert.maintenance.data.local.dao.EmployeeDao
import com.expert.maintenance.data.local.dao.EmployeeInterventionDao
import com.expert.maintenance.data.local.dao.ImageDao
import com.expert.maintenance.data.local.dao.InterventionDao
import com.expert.maintenance.data.local.dao.PriorityDao
import com.expert.maintenance.data.local.dao.SiteDao
import com.expert.maintenance.data.local.dao.TaskDao
import com.expert.maintenance.data.local.entity.Client
import com.expert.maintenance.data.local.entity.Contract
import com.expert.maintenance.data.local.entity.Employee
import com.expert.maintenance.data.local.entity.EmployeeIntervention
import com.expert.maintenance.data.local.entity.Image
import com.expert.maintenance.data.local.entity.Intervention
import com.expert.maintenance.data.local.entity.Priority
import com.expert.maintenance.data.local.entity.Site
import com.expert.maintenance.data.local.entity.Task
import java.util.Date

/**
 * Local SQLite Database using Room
 * Stores all data for offline access and synchronization
 */
@Database(
    entities = [
        Employee::class,
        Client::class,
        Site::class,
        Intervention::class,
        Task::class,
        Priority::class,
        Image::class,
        Contract::class,
        EmployeeIntervention::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun employeeDao(): EmployeeDao
    abstract fun clientDao(): ClientDao
    abstract fun siteDao(): SiteDao
    abstract fun interventionDao(): InterventionDao
    abstract fun taskDao(): TaskDao
    abstract fun priorityDao(): PriorityDao
    abstract fun imageDao(): ImageDao
    abstract fun contractDao(): ContractDao
    abstract fun employeeInterventionDao(): EmployeeInterventionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expert_maintenance_db"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Type converters for Room database
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @androidx.room.TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
