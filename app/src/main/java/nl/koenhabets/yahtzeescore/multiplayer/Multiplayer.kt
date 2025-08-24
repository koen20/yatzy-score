package nl.koenhabets.yahtzeescore.multiplayer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.koenhabets.yahtzeescore.BuildConfig
import nl.koenhabets.yahtzeescore.Permissions
import nl.koenhabets.yahtzeescore.data.SubscriptionRepository
import nl.koenhabets.yahtzeescore.model.NearbyMessage
import nl.koenhabets.yahtzeescore.model.PlayerItem
import nl.koenhabets.yahtzeescore.model.Response
import nl.koenhabets.yahtzeescore.model.Response.ScoreResponse
import nl.koenhabets.yahtzeescore.model.Subscription
import nl.koenhabets.yahtzeescore.multiplayer.YatzyServerClient.YatzyClientListener
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Date
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.timerTask

class Multiplayer(
    private val context: Context,
    private var name: String?,
    private val subscriptionRepository: SubscriptionRepository
) {
    private var listener: MultiplayerListener? = null
    private val subscriptions: MutableList<Subscription> = CopyOnWriteArrayList()
    private var yatzyServerClient: YatzyServerClient? = null
    private var updateTimer: Timer? = null
    private var score = 0
    private var fullScore: JSONObject? = null
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var playerDiscovery: PlayerDiscovery? = null

    var pairCode: String = getRandomString(10)
        private set
    var userId: String? = null
        private set

    interface MultiplayerListener {
        fun onPlayerChanged(player: PlayerItem)
    }

    fun setMultiplayerListener(listener: MultiplayerListener) {
        this.listener = listener
    }

    init {
        initMultiplayer()
    }

    private fun initMultiplayer() {
        val sharedPref =
            context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE)

        val userKey: String?

        if (sharedPref.contains("userKey")) {
            userKey = sharedPref.getString("userKey", null)
        } else {
            userKey = getRandomString(60)
            sharedPref.edit { putString("userKey", userKey) }
        }
        if (sharedPref.contains("userId")) {
            userId = sharedPref.getString("userId", null)
        } else {
            userId = getRandomString(50)
            sharedPref.edit { putString("userId", userId) }
        }

        if (userKey != null && userId != null) {
            yatzyServerClient =
                YatzyServerClient(userId!!, userKey, BuildConfig.VERSION_CODE)
            yatzyServerClient?.username = name
            yatzyServerClient?.setYatzyClientListener(object : YatzyClientListener {
                override fun onScore(score: ScoreResponse) {
                    processScore(score)
                }

                override fun onPair(pairResponse: Response.PairResponse) {
                    processPairRequest(pairResponse.userId, pairCode)
                }
            })
        } else {
            Log.e("Multiplayer", "userKey or userId is null")
        }

        scope.launch {
            val subscriptions = subscriptionRepository.getAll()
            Log.i("Multiplayer", "Subscribing to ${subscriptions.size} users")
            subscriptions.forEach {
                subscribe(it.userId)
            }
        }

        updateTimer = Timer()
        updateTimer?.schedule(timerTask {
            fullScore?.let {
                yatzyServerClient?.setScore(score, it)
            }
            scope.launch {
                subscriptionRepository.insert(subscriptions)
            }
        }, 6000, 30000)

        val permissionGranted = nearbyPermissionGranted()
        if (permissionGranted) {
            startPlayerDiscovery()
        }
    }

    fun nearbyPermissionGranted(): Boolean {
        var permissionGranted = true
        Permissions().getNearbyPermissions().forEach {
            Log.i(
                "MultiplayerPermissions",
                "$it : " + ContextCompat.checkSelfPermission(context, it)
            )
            if (ContextCompat.checkSelfPermission(context, it)
                == PackageManager.PERMISSION_DENIED
            ) {
                permissionGranted = false
            }
        }
        Log.i("Multiplayer", "Nearby permission granted $permissionGranted")
        return permissionGranted
    }

    fun startPlayerDiscovery() {
        userId?.let {
            if (playerDiscovery == null) {
                playerDiscovery = PlayerDiscovery(context, it)
                playerDiscovery?.startDiscovery()
                playerDiscovery?.setPlayerDiscoveryListener(object :
                    PlayerDiscovery.PlayerDiscoveryListener {
                    override fun onMessageReceived(message: NearbyMessage) {
                        subscribe(message.id)
                        //todo there are still problems with messages arriving late, disabled for now
                        //processNearbyMessage(message)
                    }
                })
            }
        }
    }

    private fun processPairRequest(userIdReceived: String, pairCodeReceived: String) {
        if (pairCodeReceived == pairCode) {
            subscribe(userIdReceived)
        }
    }

    private fun processNearbyMessage(message: NearbyMessage) {
        if (message.id != userId) {
            /* val lastMessage = subscriptions.find { it.userId == message.id }
             if (lastMessage == null || message.t > lastMessage.lastSeen!!) {

             }*/
            subscriptions.forEach {
                if (it.userId == message.id) {
                    it.name = message.u
                    it.lastSeen = Date().time
                    return@forEach
                }
            }

            val item = PlayerItem(
                message.id,
                message.u,
                message.s,
                null,
                Date().time,
                isLocal = false,
                null
            )
            listener?.onPlayerChanged(item)
        }
    }

    private fun processScore(score: ScoreResponse) {
        subscriptions.forEach {
            if (it.userId == score.userId) {
                it.name = score.username
                it.lastSeen = Date().time
                return@forEach
            }
        }

        val item = PlayerItem(
            score.userId,
            score.username,
            score.score,
            score.fullScore,
            Date().time,
            isLocal = false,
            score.game
        )
        listener?.onPlayerChanged(item)
    }

    fun setScore(score: Int, jsonObject: JSONObject) {
        this.score = score
        fullScore = jsonObject
        yatzyServerClient?.setScore(score, jsonObject)
        playerDiscovery?.updatePlayer(name, score)
    }

    fun setName(name: String) {
        this.name = name
        yatzyServerClient?.username = name
    }

    fun setGame(game: String) {
        yatzyServerClient?.game = game
    }

    fun endGame(game: String, versionString: String, versionCode: Int) {
        var gameSend = game
        val testLabSetting = Settings.System.getString(context.contentResolver, "firebase.test.lab")
        if ("true" == testLabSetting || "generic".equals(Build.BRAND, ignoreCase = true)) {
            gameSend = "test"
        }
        yatzyServerClient?.endGame(gameSend, versionString, versionCode)
    }

    fun stopMultiplayer() {
        updateTimer?.cancel()
        updateTimer?.purge()
        yatzyServerClient?.disconnect()
        playerDiscovery?.stopDiscovery()
    }

    fun subscribe(id: String, scannedPairCode: String? = null) {
        if (subscriptions.find { it.userId == id } == null && id != userId) {
            yatzyServerClient?.subscribe(id, scannedPairCode)
            scope.launch {
                val fetchedSubscription = subscriptionRepository.getUserById(id)
                if (fetchedSubscription == null) {
                    val subscription = Subscription(id, null, null)
                    subscriptionRepository.insert(subscription)
                    subscriptions.add(subscription)
                } else {
                    subscriptions.add(fetchedSubscription)
                }
            }

        }
    }

    private fun getRandomString(length: Int): String {
        val rnd = SecureRandom.getInstance("SHA1PRNG")
        val sb = StringBuilder(length)
        var i = 0
        val result = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        while (i < length) {
            val randomInt: Int = rnd.nextInt(result.length)
            sb.append(result[randomInt])
            i++
        }

        return sb.toString()
    }
}