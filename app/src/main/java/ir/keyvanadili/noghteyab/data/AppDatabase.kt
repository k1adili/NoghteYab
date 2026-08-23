package ir.keyvanadili.noghteyab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GeoPoint::class, CategoryEntity::class, TrackEntity::class, TrackPointEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun geoPointDao(): GeoPointDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds the tracks/track_points tables without touching existing points/categories data. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        distanceMeters REAL NOT NULL DEFAULT 0.0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_points_trackId ON track_points(trackId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "noghteyab.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Safety net only for schema jumps with no explicit migration path
                    // (e.g. a very old install skipping straight to this version).
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
