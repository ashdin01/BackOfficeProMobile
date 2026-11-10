package au.com.harcourtapples.bopro

import android.app.Application
import au.com.harcourtapples.bopro.db.AppDatabase
import au.com.harcourtapples.bopro.repository.OrderPrepRepository
import au.com.harcourtapples.bopro.repository.ReceiveDraftStore
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import au.com.harcourtapples.bopro.repository.StockRepository
import au.com.harcourtapples.bopro.repository.StocktakeRepository
import au.com.harcourtapples.bopro.repository.WarehouseOrderDraftStore
import au.com.harcourtapples.bopro.repository.WarehouseOrderRepository

class BoproApplication : Application() {
    val settingsStore  by lazy { SettingsStore(applicationContext) }
    val database       by lazy { AppDatabase.get(applicationContext) }
    val stocktakeRepo  by lazy { StocktakeRepository(database.sessionDao(), database.countDao()) }
    val receiveRepo    by lazy { ReceiveRepository(ReceiveDraftStore(applicationContext)) }
    val orderPrepRepo  by lazy { OrderPrepRepository() }
    val warehouseOrderRepo by lazy { WarehouseOrderRepository(WarehouseOrderDraftStore(applicationContext)) }
    val stockRepo      by lazy { StockRepository() }
}
