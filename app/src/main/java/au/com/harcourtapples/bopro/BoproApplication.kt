package au.com.harcourtapples.bopro

import android.app.Application
import au.com.harcourtapples.bopro.db.AppDatabase
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import au.com.harcourtapples.bopro.repository.StocktakeRepository

class BoproApplication : Application() {
    val settingsStore by lazy { SettingsStore(applicationContext) }
    val database      by lazy { AppDatabase.get(applicationContext) }
    val stocktakeRepo by lazy { StocktakeRepository(database.sessionDao(), database.countDao()) }
    val receiveRepo   by lazy { ReceiveRepository() }
}
