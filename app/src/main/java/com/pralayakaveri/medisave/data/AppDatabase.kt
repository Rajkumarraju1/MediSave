package com.pralayakaveri.medisave.data

import android.content.Context
import androidx.room.*

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicineEntity::class, MedicineReminderEntity::class, FamilyMemberEntity::class, UserEntity::class, DoseLogEntity::class],
    version = 18,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun medicineReminderDao(): MedicineReminderDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun userDao(): UserDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Existing migrations...
                val MIGRATION_6_7 = object : Migration(6, 7) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("DROP TABLE IF EXISTS medicines")
                        database.execSQL("CREATE TABLE medicines (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `brandName` TEXT NOT NULL, `saltComposition` TEXT NOT NULL, `normalizedSalt` TEXT NOT NULL, `price` REAL NOT NULL, `manufacturer` TEXT NOT NULL, `strength` TEXT NOT NULL DEFAULT '', `packSize` INTEGER)")
                    }
                }

                val MIGRATION_7_8 = object : Migration(7, 8) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `family_members` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `age` TEXT NOT NULL, `relation` TEXT NOT NULL, `condition` TEXT NOT NULL, PRIMARY KEY(`id`))")
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `profileId` TEXT NOT NULL DEFAULT 'primary'")
                    }
                }

                val MIGRATION_8_9 = object : Migration(8, 9) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `users` (
                                `userId` TEXT NOT NULL, 
                                `name` TEXT NOT NULL, 
                                `phone` TEXT NOT NULL, 
                                `email` TEXT NOT NULL, 
                                `age` TEXT NOT NULL, 
                                `gender` TEXT NOT NULL, 
                                `conditions` TEXT NOT NULL, 
                                `language` TEXT NOT NULL, 
                                `connectionCode` TEXT NOT NULL DEFAULT '',
                                PRIMARY KEY(`userId`)
                            )
                        """.trimIndent())
                    }
                }

                val MIGRATION_9_10 = object : Migration(9, 10) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `users` ADD COLUMN `connectionCode` TEXT NOT NULL DEFAULT ''")
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `dose_logs` (
                                `id` TEXT NOT NULL, 
                                `userId` TEXT NOT NULL, 
                                `medicineName` TEXT NOT NULL, 
                                `date` TEXT NOT NULL, 
                                `time` TEXT NOT NULL, 
                                `status` TEXT NOT NULL, 
                                `lastUpdatedAt` INTEGER NOT NULL, 
                                `notified` INTEGER NOT NULL DEFAULT 0, 
                                `syncPending` INTEGER NOT NULL DEFAULT 1, 
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent())
                    }
                }

                val MIGRATION_12_13 = object : Migration(12, 13) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `timezone` TEXT NOT NULL DEFAULT 'UTC'")
                    }
                }

                val MIGRATION_13_14 = object : Migration(13, 14) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `dose_logs` ADD COLUMN `pillCount` INTEGER NOT NULL DEFAULT 0")
                    }
                }

                val MIGRATION_14_15 = object : Migration(14, 15) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `startDate` TEXT NOT NULL DEFAULT ''")
                    }
                }

                val MIGRATION_15_16 = object : Migration(15, 16) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `gracePeriodMinutes` INTEGER NOT NULL DEFAULT 10")
                    }
                }

                val MIGRATION_16_17 = object : Migration(16, 17) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `lastRefillNotifiedAt` INTEGER NOT NULL DEFAULT 0")
                        database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `caregiverAlertEnabled` INTEGER NOT NULL DEFAULT 1")
                        database.execSQL("ALTER TABLE `dose_logs` ADD COLUMN `caregiverAlertEnabled` INTEGER NOT NULL DEFAULT 1")
                    }
                }

                val MIGRATION_17_18 = object : Migration(17, 18) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        val tables = listOf("medicine_reminders", "dose_logs")
                        
                        // medicine_reminders columns
                        val medColumns = mapOf(
                            "nextCheckAt" to "INTEGER NOT NULL DEFAULT 0",
                            "notifiedMap" to "TEXT NOT NULL DEFAULT '{}'",
                            "history" to "TEXT NOT NULL DEFAULT '{}'",
                            "totalScheduled" to "INTEGER NOT NULL DEFAULT 0"
                        )
                        
                        medColumns.forEach { (name, type) ->
                            try {
                                database.execSQL("ALTER TABLE `medicine_reminders` ADD COLUMN `$name` $type")
                            } catch (e: Exception) {
                                android.util.Log.w("AppDatabase", "Column $name may already exist in medicine_reminders")
                            }
                        }

                        // dose_logs columns
                        try {
                            database.execSQL("ALTER TABLE `dose_logs` ADD COLUMN `medicineId` TEXT NOT NULL DEFAULT ''")
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Column medicineId may already exist in dose_logs")
                        }
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medisave_database"
                )
                .addMigrations(
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, 
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                    MIGRATION_16_17, MIGRATION_17_18
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
