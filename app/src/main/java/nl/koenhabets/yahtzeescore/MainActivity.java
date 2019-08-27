package nl.koenhabets.yahtzeescore;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matomo.sdk.Matomo;
import org.matomo.sdk.Tracker;
import org.matomo.sdk.TrackerBuilder;
import org.matomo.sdk.extra.TrackHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity implements TextWatcher, GoogleApiClient.OnConnectionFailedListener, OnFailureListener {
    private EditText editText1;
    private EditText editText2;
    private EditText editText3;
    private EditText editText4;
    private EditText editText5;
    private EditText editText6;
    private EditText editText21;
    private EditText editText22;
    private EditText editText23;
    private EditText editText24;
    private EditText editText25;
    private EditText editText26;
    private EditText editText27;
    private TextView tvTotalLeft;
    private TextView tvTotalRight;
    private TextView tvTotal;
    private TextView tvBonus;
    private static TextView tvOp;
    private TextView tv11;
    private TextView tv12;
    private TextView tv13;
    private TextView tv14;
    private Button button;

    private MessageListener mMessageListener;
    private Message mMessage;
    public static String name = "";

    private int totalLeft = 0;
    private int totalRight = 0;
    private static List<PlayerItem> players = new ArrayList<>();
    Timer updateTimer;
    private int updateInterval = 10000;
    private JSONArray playersM = new JSONArray();

    static boolean multiplayer;

    private static Tracker mMatomoTracker;

    public synchronized Tracker getTracker() {
        if (mMatomoTracker != null) return mMatomoTracker;
        mMatomoTracker = TrackerBuilder.createDefault("https://analytics.koenhabets.nl/matomo.php", 6).build(Matomo.getInstance(this));
        return mMatomoTracker;
    }

    public static Tracker getTracker2() {
        return mMatomoTracker;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(sharedPref.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));

        int nightModeFlags =
                this.getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                ActionBar actionBar = getSupportActionBar();
                actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121212")));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = this.getWindow();
                    window.setStatusBarColor(Color.parseColor("#121212"));
                }
                break;
        }

        try {
            FirebaseAnalytics mFirebaseAnalytics;
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

            String testLabSetting = Settings.System.getString(getContentResolver(), "firebase.test.lab");
            if ("true".equals(testLabSetting)) {
                mFirebaseAnalytics.setAnalyticsCollectionEnabled(false);  //Disable Analytics Collection
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        getTracker();
        Tracker tracker = mMatomoTracker;
        TrackHelper.track().screen("/").title("Main screen").with(tracker);
        TrackHelper.track().download().with(tracker);


        editText1 = findViewById(R.id.editText);
        editText2 = findViewById(R.id.editText3);
        editText3 = findViewById(R.id.editText4);
        editText4 = findViewById(R.id.editText5);
        editText5 = findViewById(R.id.editText6);
        editText6 = findViewById(R.id.editText7);

        editText21 = findViewById(R.id.editText9);
        editText22 = findViewById(R.id.editText10);
        editText23 = findViewById(R.id.editText8);
        editText24 = findViewById(R.id.editText11);
        editText25 = findViewById(R.id.editText12);
        editText26 = findViewById(R.id.editText13);
        editText27 = findViewById(R.id.editText14);

        editText1.addTextChangedListener(this);
        editText2.addTextChangedListener(this);
        editText3.addTextChangedListener(this);
        editText4.addTextChangedListener(this);
        editText5.addTextChangedListener(this);
        editText6.addTextChangedListener(this);
        editText21.addTextChangedListener(this);
        editText22.addTextChangedListener(this);
        editText23.addTextChangedListener(this);
        editText24.addTextChangedListener(this);
        editText25.addTextChangedListener(this);
        editText26.addTextChangedListener(this);
        editText27.addTextChangedListener(this);

        tv11 = findViewById(R.id.textView11);
        tv12 = findViewById(R.id.textView12);
        tv13 = findViewById(R.id.textView13);
        tv14 = findViewById(R.id.textView14);
        button = findViewById(R.id.button);

        tvTotalLeft = findViewById(R.id.textViewTotalLeft);
        tvTotalRight = findViewById(R.id.textViewTotalRight);
        tvTotal = findViewById(R.id.textViewTotal);
        tvBonus = findViewById(R.id.textViewBonus);
        tvOp = findViewById(R.id.textViewOp);

        try {
            readScores(new JSONObject(sharedPref.getString("scores", "")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        calculateTotal();

        tv11.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText23.setText(25 + "");
            }
        });
        tv12.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText24.setText(30 + "");
            }
        });
        tv13.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText25.setText(40 + "");
            }
        });
        tv14.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText26.setText(50 + "");
            }
        });
        final Context context = this;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Clear all");
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });
                builder.setPositiveButton("Clear all", (dialog, id) -> {
                    clearText();
                    TrackHelper.track().event("category", "action").name("clear").with(mMatomoTracker);
                });
                builder.setNeutralButton("Clear and save score", (dialogInterface, i) -> {
                    saveScore(totalLeft + totalRight);
                    TrackHelper.track().event("category", "action").name("clear and save").with(mMatomoTracker);
                    clearText();
                });
                builder.show();
            }
        });
    }

    private void permissionDialog() {
        Context context = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Multiplayer");
        builder.setMessage("To automatically discover players nearby the app needs nearby permissions. Do you want to enable multiplayer?");
        builder.setNegativeButton("No", (dialog, id) -> {
            TrackHelper.track().event("multiplayer", "disable").name("disable").with(mMatomoTracker);
            SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
            sharedPref.edit().putBoolean("multiplayer", false).apply();
            sharedPref.edit().putBoolean("multiplayerAsked", true).apply();
        });
        builder.setPositiveButton("Yes", (dialog, id) -> {
            tvOp.setText(R.string.No_players_nearby);
            TrackHelper.track().event("multiplayer", "enable").name("enable").with(mMatomoTracker);
            SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
            sharedPref.edit().putBoolean("multiplayer", true).apply();
            sharedPref.edit().putBoolean("multiplayerAsked", true).apply();
            initMultiplayer();
        });
        builder.show();
    }

    private void initMultiplayer() {
        multiplayer = true;
        mMessage = new Message(("new player").getBytes());
        mMessageListener = new MessageListener() {
            @Override
            public void onFound(Message message) {
                Log.d("t", "Found message: " + new String(message.getContent()));
                proccessMessage(new String(message.getContent()), false);
            }

            @Override
            public void onLost(Message message) {
                Log.d("d", "Lost sight of message: " + new String(message.getContent()));
            }
        };

        Nearby.getMessagesClient(this).publish(mMessage).addOnFailureListener(this);
        Nearby.getMessagesClient(this).subscribe(mMessageListener);
        try {
            Mqtt.connectMqtt(name, this);
            updateTimer = new Timer();
            updateTimer.scheduleAtFixedRate(new updateTask(), 6000, updateInterval);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);

        Log.i("name", sharedPref.getString("name", ""));
        if (sharedPref.getString("name", "").equals("")) {
            nameDialog(this);
        } else {
            name = sharedPref.getString("name", "");
        }

        try {
            playersM = new JSONArray(sharedPref.getString("players", ""));
            for (int i = 0; i < playersM.length(); i++) {
                boolean exists = false;
                for (int k = 0; k < players.size(); k++) {
                    PlayerItem item = players.get(k);
                    if (item.getName().equals(playersM.getString(i))) {
                        exists = true;
                    }
                }
                if (!exists) {
                    PlayerItem playerItem = new PlayerItem(playersM.getString(i), 0, 0, false);
                    players.add(playerItem);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        tvOp.setMovementMethod(new ScrollingMovementMethod());
        tvOp.setOnClickListener(view -> addPlayerDialog());
        Log.i("players", playersM.toString() + "");
        calculateTotal();
    }

    private void saveScore(int score) {
        SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
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
            jsonArray.put(jsonObject);
            sharedPref.edit().putString("scoresSaved", jsonArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        BackupManager backupManager = new BackupManager(this);
        backupManager.dataChanged();
    }


    private void addPlayerDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_name, null);
        final EditText editTextName = view.findViewById(R.id.editText2);
        builder.setView(view);
        builder.setMessage("Add player");
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                playersM.put(editTextName.getText().toString());
                SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
                sharedPref.edit().putString("players", playersM.toString()).apply();
                PlayerItem playerItem = new PlayerItem(editTextName.getText().toString(), 0, 0, true);
                players.add(playerItem);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    public static void proccessMessage(String message, boolean mqtt) {
        try {
            if (!message.equals("new player")) {
                String messageSplit[] = message.split(";");
                boolean exists = false;
                if (!messageSplit[0].equals(name)) {
                    for (int i = 0; i < players.size(); i++) {
                        PlayerItem playerItem = players.get(i);
                        if (playerItem.getName().equals(messageSplit[0])) {
                            exists = true;
                            if (playerItem.getLastUpdate() < Long.parseLong(messageSplit[2])) {
                                Log.i("message", "newer message");
                                players.remove(i);
                                PlayerItem item = new PlayerItem(messageSplit[0], Integer.parseInt(messageSplit[1]), Long.parseLong(messageSplit[2]), true);
                                players.add(item);
                                break;
                            }
                        }
                    }
                    if (!exists && !mqtt) {
                        Log.i("New player", messageSplit[0]);
                        PlayerItem item = new PlayerItem(messageSplit[0], Integer.parseInt(messageSplit[1]), Long.parseLong(messageSplit[2]), true);
                        players.add(item);
                    }
                    String text = "Nearby: " + "\n";

                    for (int i = 0; i < players.size(); i++) {
                        PlayerItem playerItem = players.get(i);
                        if (playerItem.isVisible()) {
                            text = text + playerItem.getName() + ": " + playerItem.getScore() + "\n";
                        }
                    }
                    if (players.size() != 0) {
                        tvOp.setText(text);
                    }
                }
            }
        } catch (Exception e) {
            if (!message.equals("")) {
                tvOp.setText(message + "");
            }
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("error", connectionResult.getErrorMessage());
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        e.printStackTrace();
    }

    private class updateTask extends TimerTask {
        @Override
        public void run() {
            updateNearbyScore();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.privacy_policy:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://koenhabets.nl/privacy_policy.html"));
                startActivity(browserIntent);
                return true;
            case R.id.scores2:
                Intent myIntent = new Intent(this, ScoresActivity.class);
                this.startActivity(myIntent);
                return true;
            case R.id.settings2:
                Intent myIntent2 = new Intent(this, SettingsActivity.class);
                this.startActivity(myIntent2);
                return true;
            case R.id.rules:
                Intent browserIntent2 = new Intent(Intent.ACTION_VIEW, Uri.parse("https://en.wikipedia.org/wiki/Yahtzee#Rules"));
                startActivity(browserIntent2);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private void updateNearbyScore() {
        if (multiplayer) {
            Nearby.getMessagesClient(this).unpublish(mMessage);
            Date date = new Date();
            if (!name.equals("")) {
                String text = name + ";" + (totalLeft + totalRight) + ";" + date.getTime();
                mMessage = new Message((text).getBytes());
                Log.i("tada", "score sent");
                try {
                    if (!Mqtt.mqttAndroidClient.isConnected()) {
                        Mqtt.connectMqtt(name, this);
                    }
                    Mqtt.publish("score", text);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Nearby.getMessagesClient(this).publish(mMessage).addOnFailureListener(this);
            }
        }
    }

    private void nameDialog(Context context) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        LayoutInflater inflater = this.getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_name, null);
        final EditText editTextName = view.findViewById(R.id.editText2);
        SharedPreferences sharedPref2 = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        editTextName.setText(sharedPref2.getString("name", ""));
        builder.setView(view);
        builder.setMessage(context.getString(R.string.name_message));
        builder.setPositiveButton("Ok", (dialog, id) -> {
            SharedPreferences sharedPref = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
            sharedPref.edit().putString("name", editTextName.getText().toString()).apply();
            name = editTextName.getText().toString();
            try {
                Mqtt.disconnectMqtt();
                Mqtt.connectMqtt(name, context);
            } catch (MqttException e) {
                e.printStackTrace();
            }
            TrackHelper.track().event("category", "action").name("name changed").with(mMatomoTracker);
        });
        builder.show();
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
        if (sharedPref.contains("scores") || sharedPref.contains("name")) {
            if (!sharedPref.contains("version")) {
                sharedPref.edit().putInt("version", 1).apply();
                sharedPref.edit().putBoolean("multiplayer", true).apply();
                sharedPref.edit().putBoolean("multiplayerAsked", true).apply();
            }
        } else {
            sharedPref.edit().putInt("version", 1).apply();
        }

        if (sharedPref.getBoolean("multiplayer", false)) {
            initMultiplayer();
            multiplayer = true;
            tvOp.setText(R.string.No_players_nearby);
        } else {
            multiplayer = false;
            tvOp.setText("");
        }

        if (!sharedPref.getBoolean("multiplayerAsked", false)) {
            permissionDialog();
        }
        if (multiplayer) {
            try {
                updateTimer.cancel();
                updateTimer.purge();
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateTimer = new Timer();
            updateTimer.scheduleAtFixedRate(new updateTask(), 3000, updateInterval);
        }

    }

    @Override
    public void onDestroy() {
        if (multiplayer) {
            Log.i("onStop", "disconnecting");
            try {
                Nearby.getMessagesClient(this).unpublish(mMessage);
                Nearby.getMessagesClient(this).unsubscribe(mMessageListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Mqtt.disconnectMqtt();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                updateTimer.cancel();
                updateTimer.purge();
                mMatomoTracker.dispatch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        calculateTotal();
        saveScores();
        updateNearbyScore();
    }

    @Override
    public void afterTextChanged(Editable editable) {
    }

    private void calculateTotal() {
        totalLeft = getTextInt(editText1) + getTextInt(editText2) + getTextInt(editText3) + getTextInt(editText4) + getTextInt(editText5) + getTextInt(editText6);
        totalRight = getTextInt(editText21) + getTextInt(editText22) + getTextInt(editText23)
                + getTextInt(editText24) + getTextInt(editText25) + getTextInt(editText26) + getTextInt(editText27);
        if (totalLeft >= 63) {
            tvBonus.setText(getString(R.string.bonus) + 35);
            totalLeft = totalLeft + 35;
        } else {
            tvBonus.setText(getString(R.string.bonus) + 0);
        }
        tvTotalLeft.setText(getString(R.string.left) + totalLeft);
        tvTotalRight.setText(getString(R.string.right) + totalRight);
        tvTotal.setText(getString(R.string.Total) + (totalLeft + totalRight));
        if (players.size() == 0 && multiplayer) {
            tvOp.setText(R.string.No_players_nearby);
        }
        int color = Color.BLACK;
        int nightModeFlags =
                this.getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                color = Color.WHITE;
                break;
        }
        if (getTextInt(editText1) > 5) {
            editText1.setTextColor(Color.RED);
        } else {
            editText1.setTextColor(color);
        }
        if (getTextInt(editText2) > 10) {
            editText2.setTextColor(Color.RED);
        } else {
            editText2.setTextColor(color);
        }
        if (getTextInt(editText3) > 15) {
            editText3.setTextColor(Color.RED);
        } else {
            editText3.setTextColor(color);
        }
        if (getTextInt(editText4) > 20) {
            editText4.setTextColor(Color.RED);
        } else {
            editText4.setTextColor(color);
        }
        if (getTextInt(editText5) > 25) {
            editText5.setTextColor(Color.RED);
        } else {
            editText5.setTextColor(color);
        }
        if (getTextInt(editText6) > 30) {
            editText6.setTextColor(Color.RED);
        } else {
            editText6.setTextColor(color);
        }
        if (getTextInt(editText23) != 25 && getTextInt(editText23) != 0) {
            editText23.setTextColor(Color.RED);
        } else {
            editText23.setTextColor(color);
        }
        if (getTextInt(editText24) != 30 && getTextInt(editText24) != 0) {
            editText24.setTextColor(Color.RED);
        } else {
            editText24.setTextColor(color);
        }
        if (getTextInt(editText25) != 40 && getTextInt(editText25) != 0) {
            editText25.setTextColor(Color.RED);
        } else {
            editText25.setTextColor(color);
        }
        if (getTextInt(editText26) != 50 && getTextInt(editText26) != 0) {
            editText26.setTextColor(Color.RED);
        } else {
            editText26.setTextColor(color);
        }
    }

    private int getTextInt(EditText editText) {
        int d = 0;
        try {
            d = Integer.parseInt(editText.getText().toString());
        } catch (Exception ignored) {
        }
        return d;
    }

    private void clearText() {
        editText1.setText("");
        editText2.setText("");
        editText3.setText("");
        editText4.setText("");
        editText5.setText("");
        editText6.setText("");
        editText21.setText("");
        editText22.setText("");
        editText23.setText("");
        editText24.setText("");
        editText25.setText("");
        editText26.setText("");
        editText27.setText("");
        updateNearbyScore();
    }

    private void saveScores() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("1", editText1.getText().toString());
            jsonObject.put("2", editText2.getText().toString());
            jsonObject.put("3", editText3.getText().toString());
            jsonObject.put("4", editText4.getText().toString());
            jsonObject.put("5", editText5.getText().toString());
            jsonObject.put("6", editText6.getText().toString());
            jsonObject.put("21", editText21.getText().toString());
            jsonObject.put("22", editText22.getText().toString());
            jsonObject.put("23", editText23.getText().toString());
            jsonObject.put("24", editText24.getText().toString());
            jsonObject.put("25", editText25.getText().toString());
            jsonObject.put("26", editText26.getText().toString());
            jsonObject.put("27", editText27.getText().toString());
            SharedPreferences sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);
            sharedPref.edit().putString("scores", jsonObject.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void readScores(JSONObject jsonObject) {
        try {
            editText1.setText(jsonObject.getString("1"));
            editText2.setText(jsonObject.getString("2"));
            editText3.setText(jsonObject.getString("3"));
            editText4.setText(jsonObject.getString("4"));
            editText5.setText(jsonObject.getString("5"));
            editText6.setText(jsonObject.getString("6"));
            editText21.setText(jsonObject.getString("21"));
            editText22.setText(jsonObject.getString("22"));
            editText23.setText(jsonObject.getString("23"));
            editText24.setText(jsonObject.getString("24"));
            editText25.setText(jsonObject.getString("25"));
            editText26.setText(jsonObject.getString("26"));
            editText27.setText(jsonObject.getString("27"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
