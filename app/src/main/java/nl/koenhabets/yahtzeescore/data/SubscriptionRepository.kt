package nl.koenhabets.yahtzeescore.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.koenhabets.yahtzeescore.model.Subscription
import java.io.File

class SubscriptionRepository(val context: Context) {
    private val fileName = "subscriptions.json"

    suspend fun getAll(): List<Subscription> = withContext(Dispatchers.IO) {
        try {
            val jsonString = File(context.filesDir, fileName).readText()
            Json.decodeFromString<List<Subscription>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun insert(vararg subscriptions: Subscription) = withContext(Dispatchers.IO) {
        addItem(subscriptions.toList())
    }

    suspend fun insert(subscriptions: List<Subscription>) = withContext(Dispatchers.IO) {
        addItem(subscriptions)
    }

    private suspend fun addItem(subscriptions: List<Subscription>) {
        Log.i("Subscriptions add", subscriptions.toString())
        val currentSubscriptions = getAll().toMutableList()
        subscriptions.forEach { newSubscription ->
            currentSubscriptions.removeAll { it.userId == newSubscription.userId }
        }
        currentSubscriptions.addAll(subscriptions)
        File(context.filesDir, fileName).writeText(Json.encodeToString(currentSubscriptions))
    }

    suspend fun getUserById(userId: String): Subscription? = withContext(Dispatchers.IO) {
        val subscriptions = getAll()
        return@withContext subscriptions.find { it.userId == userId }
    }

    suspend fun delete(subscription: Subscription) = withContext(Dispatchers.IO) {
        val subscriptions = getAll().toMutableList()
        subscriptions.removeAll { it.userId == subscription.userId }
        File(context.filesDir, fileName).writeText(Json.encodeToString(subscriptions))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        File(context.filesDir, fileName).writeText(Json.encodeToString(emptyList<Subscription>()))
    }
}