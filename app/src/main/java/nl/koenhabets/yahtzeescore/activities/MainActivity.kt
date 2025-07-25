package nl.koenhabets.yahtzeescore.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.koenhabets.yahtzeescore.AppUpdates
import nl.koenhabets.yahtzeescore.BuildConfig
import nl.koenhabets.yahtzeescore.Permissions
import nl.koenhabets.yahtzeescore.R
import nl.koenhabets.yahtzeescore.Rules
import nl.koenhabets.yahtzeescore.adapters.PlayerAdapter
import nl.koenhabets.yahtzeescore.data.DataManager
import nl.koenhabets.yahtzeescore.data.Game
import nl.koenhabets.yahtzeescore.data.MigrateData
import nl.koenhabets.yahtzeescore.data.SubscriptionRepository
import nl.koenhabets.yahtzeescore.databinding.ActivityMainBinding
import nl.koenhabets.yahtzeescore.dialog.AddPlayerDialog
import nl.koenhabets.yahtzeescore.dialog.GameEndDialog
import nl.koenhabets.yahtzeescore.dialog.PlayerScoreDialog
import nl.koenhabets.yahtzeescore.model.PlayerItem
import nl.koenhabets.yahtzeescore.multiplayer.Multiplayer
import nl.koenhabets.yahtzeescore.multiplayer.Multiplayer.MultiplayerListener
import nl.koenhabets.yahtzeescore.view.ScoreView
import org.json.JSONObject
import java.util.Date
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    private val subscriptionRepository = SubscriptionRepository(this)
    var multiplayer: Multiplayer? = null
    var multiplayerEnabled = false
    private var playerAdapter: PlayerAdapter? = null
    private val multiplayerPlayers: MutableList<PlayerItem> = ArrayList()
    private var playerScoreDialog: PlayerScoreDialog? = null
    private var addPlayerDialog: AddPlayerDialog? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var scoreView: ScoreView
    private var lastInitGame: Game? = null
    var score = 0
    var name: String? = null
    private var nearbyEnabled = true
    private var permissionsRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val localPlayer = PlayerItem(id = "", null, null, null, 0, true, "")
        multiplayerPlayers.add(localPlayer)
        updateMultiplayerUI(multiplayerPlayers.indexOf(localPlayer), false)

        // Set the score to 0 to prevent showing the default score
        binding.textViewTotal.text = getString(R.string.Total, 0)
        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)

        nearbyEnabled = Date().time < sharedPref.getLong("nearbyMessages", 1717266612000)

        if (!sharedPref.contains("version") && !sharedPref.contains("multiplayer") && !sharedPref.contains(
                "multiplayerAsked"
            )
        ) {
            sharedPref.edit().putBoolean("welcomeShown", false).apply()
            val myIntent = Intent(this, WelcomeActivity::class.java)
            this.startActivity(myIntent)
            finish()
        } else if (!sharedPref.getBoolean("welcomeShown", true)) {
            val myIntent = Intent(this, WelcomeActivity::class.java)
            this.startActivity(myIntent)
            finish()
        }
        AppCompatDelegate.setDefaultNightMode(
            sharedPref.getInt(
                "theme",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        )

        addPlayerDialog = AddPlayerDialog(this)
        playerScoreDialog = PlayerScoreDialog(this)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        binding.recyclerViewMultiplayer.layoutManager = layoutManager
        playerAdapter = PlayerAdapter(this, multiplayerPlayers)
        binding.recyclerViewMultiplayer.adapter = playerAdapter

        playerAdapter?.setClickListener(object : PlayerAdapter.ItemClickListener {
            override fun onItemClick(view: View?, position: Int) {
                if (position >= 0 && position < multiplayerPlayers.size) {
                    val player = multiplayerPlayers[position]
                    if (player.id != multiplayer?.userId) {
                        player.fullScore?.let {
                            playerScoreDialog?.showDialog(this@MainActivity, player, position)
                            return
                        }

                        Toast.makeText(
                            this@MainActivity, R.string.score_nearby_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        val context: Context = this
        binding.button.setOnClickListener { saveScoreDialog(context) }

        // Check for updates after 3 seconds
        Timer().schedule(
            object : TimerTask() {
                override fun run() {
                    val appUpdates = AppUpdates(applicationContext)
                    val versionText = appUpdates.getVersionText()
                    if (versionText !== null) {
                        showUpdateToast(versionText)
                    }
                    appUpdates.updateConfig(context)
                }
            },
            3000
        )
    }

    private fun initScoreView() {
        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        val game = Game.valueOf(sharedPref.getString("game", Game.Yahtzee.toString())!!)

        scoreView.setScoreListener(object : ScoreView.ScoreListener {
            override fun onScore(score: Int, scores: JSONObject) {
                DataManager().saveScores(scores, applicationContext, game)
                if (multiplayerEnabled && multiplayer != null) {
                    setMultiplayerScore(score, scores)
                }

                this@MainActivity.score = score
                binding.textViewTotal.text = getString(R.string.Total, this@MainActivity.score)
                updateLocalPlayer()
            }
        })

        try {
            scoreView.setScores(JSONObject(sharedPref.getString("scores-$game", "")!!))
        } catch (ignored: Exception) {
        }
    }

    private fun updateLocalPlayer() {
        var playerFound: PlayerItem? = null
        multiplayerPlayers.forEach {
            if (it.isLocal) {
                it.score = score
                it.name = name
                playerFound = it
                return@forEach
            }
        }
        updateMultiplayerUI(multiplayerPlayers.indexOf(playerFound), true)
    }

    private fun setCurrentScoreView(game: Game) {
        if (this::scoreView.isInitialized) {
            binding.constraintScores.removeView(scoreView)
        }
        lastInitGame = game
        scoreView = ScoreView.getView(game, this)
        binding.constraintScores.addView(scoreView)
        val set = ConstraintSet()
        set.clone(binding.constraintScores)
        set.connect(scoreView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(scoreView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(binding.textViewTotal.id, ConstraintSet.TOP, scoreView.id, ConstraintSet.BOTTOM)
        set.connect(binding.textViewOp.id, ConstraintSet.TOP, scoreView.id, ConstraintSet.BOTTOM)
        set.applyTo(binding.constraintScores)
    }

    private fun showUpdateToast(finalUpdateText: String) {
        runOnUiThread {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setMessage(getString(R.string.update_available_long) + finalUpdateText)
                .setTitle(getString(R.string.update_available))
                .setNeutralButton(R.string.close) { _, _ -> }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

    private fun saveScoreDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.save_score)
        builder.setNegativeButton(R.string.no) { _: DialogInterface, _: Int ->
            val builder2 = AlertDialog.Builder(context)
            builder2.setTitle(R.string.score_not_save_conf)
            builder2.setNegativeButton(R.string.no) { _: DialogInterface, _: Int -> }
            builder2.setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
                if (multiplayerEnabled) {
                    if (score > 40) {
                        multiplayer?.endGame(
                            (lastInitGame ?: "").toString(),
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        )
                    }
                }
                scoreView.clearScores()
            }
            builder2.show()
        }
        builder.setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
            if (score < 5) {
                val toast = Toast.makeText(this, R.string.score_too_low_save, Toast.LENGTH_SHORT)
                toast.show()
            } else {
                val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
                if (sharedPref.getBoolean("endDialog", true)) {
                    val gameEndDialog = GameEndDialog(this)
                    gameEndDialog.showDialog(score, lastInitGame!!)
                }
                DataManager().saveScore(
                    score,
                    scoreView.createJsonScores(),
                    applicationContext,
                    lastInitGame!!
                )
            }
            if (multiplayerEnabled) {
                multiplayer?.endGame(
                    (lastInitGame ?: "").toString(),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            }
            scoreView.clearScores()
        }
        builder.setNeutralButton(R.string.cancel) { _: DialogInterface, _: Int -> }
        builder.show()
    }

    private fun initMultiplayer() {
        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        Log.i("name", sharedPref.getString("name", "")!!)
        if (sharedPref.getString("name", "") == "") {
            nameDialog(this)
        } else {
            name = sharedPref.getString("name", "")
        }
        updateLocalPlayer()

        initMultiplayerObj()

        binding.textViewOp.setOnClickListener { addPlayerDialog() }
    }

    private fun initMultiplayerObj() {
        multiplayer = Multiplayer(this, name, subscriptionRepository)

        multiplayer?.setMultiplayerListener(object : MultiplayerListener {
            override fun onPlayerChanged(player: PlayerItem) {
                Log.i("main", "player changed")
                val existingPlayer = multiplayerPlayers.find { it.id == player.id }

                if (existingPlayer == null) {
                    multiplayerPlayers.add(player)
                    updateMultiplayerUI(multiplayerPlayers.indexOf(player), false)
                } else {
                    multiplayerPlayers.remove(existingPlayer)
                    val combinedPlayer = existingPlayer.copy(
                        name = player.name ?: existingPlayer.name,
                        score = player.score ?: existingPlayer.score,
                        fullScore = player.fullScore ?: existingPlayer.fullScore,
                        lastUpdate = player.lastUpdate,
                        isLocal = player.isLocal,
                        game = player.game ?: existingPlayer.game
                    )
                    multiplayerPlayers.add(combinedPlayer)
                    updateMultiplayerUI(multiplayerPlayers.indexOf(player), true)
                }

                if (playerScoreDialog?.playerShown != null) {
                    if (playerScoreDialog?.playerShown != "") {
                        playerScoreDialog?.updateScore(player)
                    }
                }

            }
        })
        lastInitGame?.let {
            multiplayer?.setGame(it.name)
        }
        setMultiplayerScore(score, scoreView.createJsonScores())
        if (multiplayer?.nearbyPermissionGranted() == false) {
            requestNearbyPermissions()
        }
    }

    private fun setMultiplayerScore(score: Int, fullScore: JSONObject) {
        multiplayer?.setScore(score, fullScore)
    }

    private fun addPlayerDialog() {
        if (multiplayer?.userId != null && multiplayer?.pairCode != null) {
            addPlayerDialog?.showDialog(multiplayer?.userId!!, multiplayer?.pairCode!!)
        }
        addPlayerDialog?.setAddPlayerDialogListener(object :
            AddPlayerDialog.AddPlayerDialogListener {
            override fun onAddPlayer(userId: String, pairCode: String) {
                multiplayer?.subscribe(userId, pairCode)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, getString(R.string.player_added),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun requestPermissions() {
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(
                        Manifest.permission.CAMERA
                    ), 23
                )
            }
        })
    }

    private fun requestNearbyPermissions() {
        if (!permissionsRequested) {
            var showRationale = false
            Permissions().getNearbyPermissions().forEach {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, it)) {
                    showRationale = true
                    return@forEach
                } else {
                    showRationale = false
                }
            }
            Log.i("MainActivity", "Request nearby permissions rationale: $showRationale")
            val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)

            if (showRationale || !sharedPref.getBoolean("nearbyPermissionRationaleShown", false)) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder
                    .setMessage(getString(R.string.nearby_permission_rationale))
                    .setTitle(getString(R.string.nearby_permissions))
                    .setPositiveButton(R.string.close) { dialog, which ->
                        ActivityCompat.requestPermissions(
                            this@MainActivity, Permissions().getNearbyPermissions(), 56
                        )
                    }

                val dialog: AlertDialog = builder.create()
                dialog.show()
            } else {
                ActivityCompat.requestPermissions(
                    this@MainActivity, Permissions().getNearbyPermissions(), 56
                )
            }
            sharedPref.edit().putBoolean("nearbyPermissionRationaleShown", true).apply()
            permissionsRequested = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 23) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addPlayerDialog?.startCodeScanner()
            } else {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder
                    .setMessage(getString(R.string.camera_permissions_denied_dialog))
                    .setTitle(getString(R.string.camera_permission))
                    .setNeutralButton(R.string.close) { _, _ -> }
                val dialog: AlertDialog = builder.create()
                dialog.show()
            }
        } else if (requestCode == 56) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("Nearby", "Permissions granted")
                multiplayer?.startPlayerDiscovery()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMultiplayerUI(position: Int?, existing: Boolean?) {
        if (multiplayerPlayers.size > 1) {
            binding.recyclerViewMultiplayer.visibility = View.VISIBLE
            binding.textViewOp.setText(R.string.nearby)
        } else {
            binding.recyclerViewMultiplayer.visibility = View.GONE
            binding.textViewOp.setText(R.string.No_players_nearby)
        }

        multiplayerPlayers.sort()

        /*if (position != null && existing != null) {
            if (existing) {
                playerAdapter?.notifyItemChanged(position)
            } else {
                playerAdapter?.notifyItemInserted(position)
            }
        } else {
            playerAdapter?.notifyDataSetChanged()
        }*/
        playerAdapter?.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.add_player).isVisible = multiplayerEnabled
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.privacy_policy) {
            Rules.openUrl("https://koenhabets.nl/privacy_policy.html", this)
            return true
        } else if (itemId == R.id.scores2) {
            val myIntent = Intent(this, ScoresActivity::class.java)
            this.startActivity(myIntent)
            return true
        } else if (itemId == R.id.settings2) {
            val myIntent2 = Intent(this, SettingsActivity::class.java)
            this.startActivity(myIntent2)
            return true
        } else if (itemId == R.id.stats) {
            val myIntent3 = Intent(this, StatsActivity::class.java)
            this.startActivity(myIntent3)
            return true
        } else if (itemId == R.id.rules) {
            if (lastInitGame !== null) {
                val url = Rules.getRules(lastInitGame!!)
                if (url !== null) {
                    Rules.openUrl(url, this)
                }
            }
            return true
        } else if (itemId == R.id.add_player) {
            if (multiplayerEnabled) {
                addPlayerDialog()
                return true
            }
            return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun nameDialog(context: Context) {
        val builder = MaterialAlertDialogBuilder(context)
        val inflater = this.layoutInflater
        val view = inflater.inflate(R.layout.dialog_name, null)
        val editTextName = view.findViewById<EditText>(R.id.editText2)
        val sharedPref2 = context.getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        editTextName.setText(sharedPref2.getString("name", ""))
        builder.setView(view)
        builder.setMessage(context.getString(R.string.name_message))
        builder.setPositiveButton("Ok") { dialog: DialogInterface?, id: Int ->
            val sharedPref =
                context.getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
            sharedPref.edit().putString("name", editTextName.text.toString()).apply()
            val name = editTextName.text.toString()
            multiplayer?.setName(name)
        }
        builder.show()
    }

    public override fun onStart() {
        super.onStart()
        Log.i("onStart", "start")
        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        MigrateData(this)
        if (Game.valueOf(
                sharedPref.getString(
                    "game",
                    Game.Yahtzee.toString()
                )!!
            ) !== lastInitGame
        ) {
            setCurrentScoreView(
                Game.valueOf(
                    sharedPref.getString(
                        "game",
                        Game.Yahtzee.toString()
                    )!!
                )
            )
            initScoreView()
        }
        if (sharedPref.getBoolean("multiplayer", false)) {
            initMultiplayer()
            multiplayerEnabled = true
            binding.recyclerViewMultiplayer.visibility = View.GONE
            binding.textViewOp.visibility = View.VISIBLE
            binding.textViewOp.setText(R.string.No_players_nearby)
        } else {
            multiplayerEnabled = false
            binding.recyclerViewMultiplayer.visibility = View.GONE
            binding.textViewOp.visibility = View.GONE
        }
    }

    public override fun onStop() {
        if (multiplayer != null) {
            Log.i("onStop", "disconnecting")
            multiplayer?.stopMultiplayer()
        }
        super.onStop()
    }

    public override fun onResume() {
        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        Log.i("onResume", "start")
        scoreView.validateScores()
        if (Game.valueOf(
                sharedPref.getString(
                    "game",
                    Game.Yahtzee.toString()
                )!!
            ) !== lastInitGame
        ) {
            setCurrentScoreView(
                Game.valueOf(
                    sharedPref.getString(
                        "game",
                        Game.Yahtzee.toString()
                    )!!
                )
            )
            initScoreView()
        }
        super.onResume()
    }
}