package com.example.ftcdecodescorer

import android.content.Context
import androidx.room.*

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

    // SCHIMBARE AICI: Acum e String ca sa poti pune "Q1", "SF2", etc.
    val matchNumber: String,

    val teamNumber: String? = null,
    val matchType: String = "Full",

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
    @Delete
    suspend fun deleteSession(session: Session)
    @Insert
    suspend fun insertMatch(match: MatchResult)
    @Query("SELECT * FROM matches WHERE parentSessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMatchesForSession(sessionId: Int): List<MatchResult>
}

// BUMP LA VERSIUNEA 11
@Database(entities = [Session::class, MatchResult::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ftc_decode_v11.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}