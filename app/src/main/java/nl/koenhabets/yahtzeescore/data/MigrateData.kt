package nl.koenhabets.yahtzeescore.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class MigrateData(context: Context) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    //run in onStart in main activity
    init {
        Log.i("MigrateData", "Start")
        val sharedPref =
            context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE)
        if (sharedPref.contains("scores") || sharedPref.contains("name")) {
            if (!sharedPref.contains("version")) {
                sharedPref.edit().putInt("version", 1).apply()
                sharedPref.edit().putBoolean("multiplayer", true).apply()
                sharedPref.edit().putBoolean("multiplayerAsked", true).apply()
            }
        } else {
            sharedPref.edit().putInt("version", 1).apply()
        }

        // The version property was not being used correctly in previous versions. config-version is a new variable added in version 1.18.
        var configVersion = sharedPref.getInt("config-version", 0)
        Log.i("MigrateData", "Current config version: $configVersion")
        val editor = sharedPref.edit()

        // This is to prevent the config version from skipping 0 if the app is upgraded from an old version to a future version.
        if (sharedPref.contains("scores")) {
            configVersion = 0
        }

        // V1.18 (39) migrate from only Yahtzee to multiple game types
        if (configVersion == 0) {
            if (sharedPref.contains("scores")) {
                if (sharedPref.getBoolean("yahtzeeBonus", false)) {
                    editor.putString(
                        "scores-${Game.YahtzeeBonus}",
                        sharedPref.getString("scores", "")
                    )
                    editor.putString("game", Game.YahtzeeBonus.toString())
                } else {
                    editor.putString("scores-${Game.Yahtzee}", sharedPref.getString("scores", ""))
                    editor.putString("game", Game.Yahtzee.toString())
                }
                editor.remove("scores")
                editor.remove("yahtzeeBonus")
            }
            configVersion = 1
        }

        // V2.1 (54) remove unused preferences from sharedPreferences
        if (configVersion == 1) {
            editor.remove("players")
            editor.remove("qrCodeEnable")
            editor.remove("playersIdv8")
            configVersion = 2
        }

        // V2.1.1 (58) try to remove incorrect entries from database // database removed
        if (configVersion == 2) {
            configVersion = 3
        }

        // V2.2 (59) Move subscriptions from database to file // database removed
        if (configVersion == 3) {
            configVersion = 4
        }

        Log.i("MigrateData", "Updated to: $configVersion")
        editor.putInt("config-version", configVersion)
        editor.apply()
    }
}