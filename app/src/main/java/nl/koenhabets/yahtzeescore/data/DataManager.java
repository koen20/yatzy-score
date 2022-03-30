package nl.koenhabets.yahtzeescore.data;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import nl.koenhabets.yahtzeescore.ScoreComparator;
import nl.koenhabets.yahtzeescore.ScoreItem;

public class DataManager {
    public DataManager() {
    }

    // save the game to shared preferences
    public void saveScore(int score, JSONObject jsonObjectScores, Context context, Game game) {
        SharedPreferences sharedPref = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray = new JSONArray(sharedPref.getString("scoresSaved", ""));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONObject jsonObject = new JSONObject();
        try {
            Log.i("scooree", score + "");
            jsonObject.put("score", score);
            jsonObject.put("date", new Date().getTime());
            jsonObject.put("id", UUID.randomUUID().toString());
            jsonObject.put("allScores", jsonObjectScores);
            jsonObject.put("game", game);
            jsonArray.put(jsonObject);
            sharedPref.edit().putString("scoresSaved", jsonArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        BackupManager backupManager = new BackupManager(context);
        backupManager.dataChanged();
    }

    //save the current score sheet to shared preferences.
    public void saveScores(JSONObject jsonObject, Context context, String game) {
        Log.i("score", "saving");
        SharedPreferences sharedPref = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        Log.i("saving", jsonObject.toString());
        sharedPref.edit().putString("scores-" + game, jsonObject.toString()).apply();
    }

    //get all scores from sharedprefrences, and sort them descending by score
    public List<ScoreItem> loadScores(Context context, Game gameFilter) {
        List<ScoreItem> scoreItems = new ArrayList<>();
        SharedPreferences sharedPref = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray = new JSONArray(sharedPref.getString("scoresSaved", ""));
            Log.i("read", jsonArray.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        for (int i = jsonArray.length() - 1; i >= 0; i--) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                JSONObject allScores = new JSONObject();
                try {
                    allScores = jsonObject.getJSONObject("allScores");
                } catch (JSONException ignored) {
                }
                Game game = Game.YahtzeeBonus;
                boolean filterEnabled = false;

                if (jsonObject.has("game")){
                    game = Game.valueOf(jsonObject.getString("game"));
                    filterEnabled = true;
                } else {
                    if (jsonObject.has("yahtzeeBonus")) {
                        if (!jsonObject.getBoolean("yahtzeeBonus")) {
                            game = Game.Yahtzee;
                        }
                    }
                }

                if (gameFilter == null) {
                    filterEnabled = false;
                } else if (gameFilter == Game.Yatzy) {
                    filterEnabled = true;
                }

                if (!filterEnabled || game == gameFilter) {
                    ScoreItem scoreItem = new ScoreItem(jsonObject.getInt("score"), jsonObject.getLong("date"),
                            jsonObject.getString("id"), game, allScores);
                    scoreItems.add(scoreItem);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Collections.sort(scoreItems, new ScoreComparator());
        return scoreItems;
    }
}
