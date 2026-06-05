package au.com.harcourtapples.bopro.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import au.com.harcourtapples.bopro.db.dao.LocalCountDao
import au.com.harcourtapples.bopro.db.dao.LocalSessionDao
import au.com.harcourtapples.bopro.db.entities.LocalCount
import au.com.harcourtapples.bopro.db.entities.LocalSession

@Database(entities = [LocalSession::class, LocalCount::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): LocalSessionDao
    abstract fun countDao(): LocalCountDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bopro_offline.db"
                ).build().also { INSTANCE = it }
            }
    }
}
