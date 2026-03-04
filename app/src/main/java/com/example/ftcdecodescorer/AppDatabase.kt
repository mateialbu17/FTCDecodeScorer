package com.example.ftcdecodescorer

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val sessionId: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "matches",
    foreignKeys = [ForeignKey(
        entity = Session::class,
        parentColumns = ["sessionId"],
        childColumns = ["parentSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["parentSessionId"])]
)
data class MatchResult(
    @PrimaryKey(autoGenerate = true) val matchId: Int = 0,
    val parentSessionId: Int,
    val matchNumber: String,
    val teamNumber: String? = null,
    val matchType: String = "Full",
    val competitionType: String = "None",
    val eventName: String = "Unknown", // COLOANA NOUA

    val totalScore: Int,
    val autoPoints: Int,
    val teleopPoints: Int,
    val endgamePoints: Int,
    val rp: Int,

    val autoClassified: Int,
    val autoOverflow: Int,
    val autoPattern: Int,
    val autoLeave1: Boolean,
    val autoLeave2: Boolean,

    val teleopClassified: Int,
    val teleopOverflow: Int,
    val teleopDepot: Int,
    val teleopPattern: Int,

    val park1Status: String,
    val park2Status: String,
    val matchOutcome: String,

    val matchNotes: String? = null,
    val park1Level: String? = null,
    val park2Level: String? = null,

    val mechDrivetrain: String? = null,
    val mechDrivetrainNotes: String? = null,
    val mechIntakeNotes: String? = null,
    val mechSort: Boolean = false,
    val mechSortNotes: String? = null,
    val mechTurret: Boolean = false,
    val mechHood: Boolean = false,
    val mechOuttakeNotes: String? = null,

    val mechCloseAccuracy: Int = 0,
    val mechFarAccuracy: Int = 0,
    val mechPlaystyleDial: Int = 5,

    val mechClimb: Boolean = false,
    val mechClimbNotes: String? = null,

    val timestamp: Long = System.currentTimeMillis()
)
@Dao
interface AppDao {
    @Insert
    suspend fun insertSession(session: Session): Long

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<Session>

    @Query("SELECT * FROM sessions WHERE name = :folderName LIMIT 1")
    suspend fun getSessionByName(folderName: String): Session?

    @Insert
    suspend fun insertMatch(match: MatchResult)
    @Update // <--- ADAUGĂ ASTA
    suspend fun updateMatch(match: MatchResult) // <--- ȘI ASTA

    @Delete
    suspend fun deleteMatch(match: MatchResult)
    @Query("UPDATE matches SET competitionType = 'Regional Championship' WHERE competitionType = 'Regional'")
    suspend fun updateRegionalFolders()
    @Query("SELECT * FROM matches WHERE parentSessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMatchesForSession(sessionId: Int): List<MatchResult>

    @Query("SELECT * FROM matches ORDER BY timestamp ASC")
    suspend fun getAllMatches(): List<MatchResult>

    // Ia doar evenimentele unice pentru un anumit tip de competiție
    @Query("SELECT DISTINCT eventName FROM matches WHERE competitionType = :compType AND teamNumber IS NOT NULL AND eventName != 'Unknown'")
    suspend fun getEventNamesForCompType(compType: String): List<String>

    // ---- Funcțiile de ștergere (FĂRĂ DUBLURI) ----
    @Delete
    suspend fun deleteSession(session: Session)

    @Query("DELETE FROM matches WHERE parentSessionId = :sessionId")
    suspend fun deleteMatchesForSession(sessionId: Int)
}

// MIGRAȚIILE
val MIGRATION_11_12 = object : Migration(11, 12) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE matches ADD COLUMN competitionType TEXT NOT NULL DEFAULT 'None'") } }
val MIGRATION_12_13 = object : Migration(12, 13) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE matches ADD COLUMN mechCloseAccuracy INTEGER NOT NULL DEFAULT 0"); database.execSQL("ALTER TABLE matches ADD COLUMN mechFarAccuracy INTEGER NOT NULL DEFAULT 0"); database.execSQL("ALTER TABLE matches ADD COLUMN mechPlaystyleDial INTEGER NOT NULL DEFAULT 5") } }
val MIGRATION_13_14 = object : Migration(13, 14) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE matches ADD COLUMN eventName TEXT NOT NULL DEFAULT 'Unknown'") } }

@Database(entities = [Session::class, MatchResult::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ftc_decode_v11.db")
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}