package au.com.harcourtapples.bopro.repository

import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.*
import au.com.harcourtapples.bopro.db.dao.LocalCountDao
import au.com.harcourtapples.bopro.db.dao.LocalSessionDao
import au.com.harcourtapples.bopro.db.entities.LocalCount
import au.com.harcourtapples.bopro.db.entities.LocalSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StocktakeRepository(
    private val sessionDao: LocalSessionDao,
    private val countDao: LocalCountDao
) {
    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private fun now() = timestampFmt.format(Date())

    suspend fun getSessions(offline: Boolean, serverUrl: String, apiKey: String = ""): List<Session> {
        return if (offline) sessionDao.getAll().map { it.toSession(countDao.countForSession(it.id)) }
        else {
            val resp = ApiClient.service(serverUrl, apiKey).getSessions()
            if (resp.isSuccessful) resp.body() ?: emptyList()
            else throw Exception("Server error ${resp.code()}")
        }
    }

    suspend fun createSession(offline: Boolean, serverUrl: String, apiKey: String = "", label: String, deptId: Int?): Int {
        return if (offline) sessionDao.insert(LocalSession(label = label, startedAt = now())).toInt()
        else {
            val resp = ApiClient.service(serverUrl, apiKey).createSession(CreateSessionRequest(label, deptId))
            if (resp.isSuccessful) resp.body()?.get("id") ?: throw Exception("No ID in response")
            else throw Exception("Failed to create session: ${resp.code()}")
        }
    }

    suspend fun getSession(offline: Boolean, serverUrl: String, apiKey: String = "", sessionId: Int): Session? {
        return if (offline) sessionDao.getById(sessionId)?.toSession(countDao.countForSession(sessionId))
        else {
            val resp = ApiClient.service(serverUrl, apiKey).getSession(sessionId)
            if (resp.isSuccessful) resp.body() else throw Exception("Server error ${resp.code()}")
        }
    }

    suspend fun getCounts(offline: Boolean, serverUrl: String, apiKey: String = "", sessionId: Int): List<Count> {
        return if (offline) countDao.getBySession(sessionId).map { it.toCount() }
        else {
            val resp = ApiClient.service(serverUrl, apiKey).getCounts(sessionId)
            if (resp.isSuccessful) resp.body() ?: emptyList()
            else throw Exception("Server error ${resp.code()}")
        }
    }

    suspend fun getExistingQty(offline: Boolean, serverUrl: String, sessionId: Int, barcode: String, apiKey: String = ""): Double {
        return if (offline) countDao.getBySessionAndBarcode(sessionId, barcode)?.qty ?: 0.0
        else try {
            val resp = ApiClient.service(serverUrl, apiKey).getCountForBarcode(sessionId, barcode)
            if (resp.isSuccessful) resp.body()?.get("counted_qty") ?: 0.0 else 0.0
        } catch (_: Exception) { 0.0 }
    }

    suspend fun addCount(offline: Boolean, serverUrl: String, sessionId: Int, barcode: String, qty: Double, apiKey: String = "", description: String = "") {
        if (offline) {
            val existing = countDao.getBySessionAndBarcode(sessionId, barcode)
            if (existing != null) countDao.update(existing.copy(qty = existing.qty + qty))
            else countDao.insert(LocalCount(sessionId = sessionId, barcode = barcode,
                description = description.ifBlank { barcode }, qty = qty, scannedAt = now()))
        } else {
            val resp = ApiClient.service(serverUrl, apiKey).addCount(sessionId, AddCountRequest(barcode, qty))
            if (!resp.isSuccessful) throw Exception("Failed to save count: ${resp.code()}")
        }
    }

    suspend fun deleteCount(offline: Boolean, serverUrl: String, apiKey: String = "", sessionId: Int, countId: Int) {
        if (offline) countDao.deleteById(countId)
        else ApiClient.service(serverUrl, apiKey).deleteCount(sessionId, countId)
    }

    suspend fun getUnsyncedSessions(): List<LocalSession> = sessionDao.getUnsynced()
    suspend fun getUnsyncedCountsForSession(sessionId: Int): List<LocalCount> = countDao.getUnsyncedForSession(sessionId)

    suspend fun syncSession(serverUrl: String, apiKey: String = "", session: LocalSession): Int {
        val api = ApiClient.service(serverUrl, apiKey)
        val resp = api.createSession(CreateSessionRequest(session.label, null, session.notes))
        if (!resp.isSuccessful) throw Exception("Failed to create session on server: ${resp.code()}")
        val serverId = resp.body()?.get("id") ?: throw Exception("No server ID returned")
        for (count in countDao.getUnsyncedForSession(session.id)) {
            val cr = api.addCount(serverId, AddCountRequest(count.barcode, count.qty))
            if (!cr.isSuccessful) throw Exception("Failed to sync count ${count.id}: ${cr.code()}")
            countDao.update(count.copy(synced = true))
        }
        sessionDao.update(session.copy(synced = true, serverId = serverId))
        return serverId
    }

    private fun LocalSession.toSession(lineCount: Int) = Session(
        id = id, label = label, deptName = null, status = "OPEN",
        startedAt = startedAt, closedAt = null, lineCount = lineCount, notes = notes
    )
    private fun LocalCount.toCount() = Count(
        id = id, sessionId = sessionId, barcode = barcode, countedQty = qty,
        description = description, sellPrice = 0.0, deptName = null, scannedAt = scannedAt
    )
}
