package nl.koenhabets.yahtzeescore;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Multiplayer implements OnFailureListener {
    private MultiplayerListener listener;
    private MessageListener mMessageListener;
    private Message mMessage;
    private FirebaseAuth mAuth;
    private Boolean realtimeDatabaseEnabled = true;
    private FirebaseUser firebaseUser;
    private DatabaseReference database;

    private static List<PlayerItem> players = new ArrayList<>();
    Timer updateTimer;
    private int updateInterval = 10000;
    private JSONArray playersM = new JSONArray();
    private Context context;
    private String name;
    private int score;
    private Mqtt mqtt;

    public Multiplayer(Context context, String name, int score, FirebaseUser firebaseUser) {
        database = FirebaseDatabase.getInstance().getReference();
        initMultiplayer(context, name);
        this.context = context;
        this.name = name;
        this.listener = null;
        this.score = score;
        this.firebaseUser = firebaseUser;
    }


    public interface MultiplayerListener {
        void onChange(List<PlayerItem> players);
    }

    public void setMultiplayerListener(MultiplayerListener listener) {
        this.listener = listener;
    }

    public void initMultiplayer(Context context, String name) {
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
        mMessage = new Message(("new player").getBytes());

        Nearby.getMessagesClient(context).publish(mMessage).addOnFailureListener(this);
        Nearby.getMessagesClient(context).subscribe(mMessageListener);
        try {
            mqtt = new Mqtt(context, name);
            mqtt.setMqttListener(message -> proccessMessage(message, true));
            updateTimer = new Timer();
            updateTimer.scheduleAtFixedRate(new updateTask(), 6000, updateInterval);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SharedPreferences sharedPref = context.getSharedPreferences("nl.koenhabets.yahtzeescore", Context.MODE_PRIVATE);

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

        Log.i("players", playersM.toString() + "");

        if (realtimeDatabaseEnabled) {
            database.child("score").addChildEventListener(new ChildEventListener() {

                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Log.i("Firebase Received", dataSnapshot.getValue().toString());
                    try {
                        proccessMessage(dataSnapshot.getValue().toString(), true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Log.w("EditTagsActivity", "Failed to read scores.", error.toException());
                }
            });
        }
    }

    public void setScore(int score) {
        this.score = score;
        // add the player to the players list and update it on screen
        if (!name.equals("") && players.size() != 0) { ///todo players nearby
            // remove player if name already exists
            for (int i = 0; i < players.size(); i++) {
                PlayerItem playerItem = players.get(i);
                if (playerItem.getName().equals(name)) {
                    players.remove(i);
                    break;
                }
            }
            PlayerItem item = new PlayerItem(name, score, new Date().getTime(), true);
            players.add(item);
            listener.onChange(players);
        }
        updateNearbyScore();
    }

    public int getPlayerAmount() {
        return players.size();
    }

    public void addPlayer(PlayerItem playerItem) {
        players.add(playerItem);
    }

    public void stopMultiplayer() {
        try {
            Nearby.getMessagesClient(context).unpublish(mMessage);
            Nearby.getMessagesClient(context).unsubscribe(mMessageListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mqtt.disconnectMqtt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            updateTimer.cancel();
            updateTimer.purge();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (realtimeDatabaseEnabled) {
            try {
                database.child("score").child(firebaseUser.getUid()).removeValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onFailure(Exception e) {
        Log.e("Nearby asdf", "iohawehfpv");
        e.printStackTrace();
    }

    private class updateTask extends TimerTask {
        @Override
        public void run() {
            updateNearbyScore();
        }
    }

    public void proccessMessage(String message, boolean mqtt) {
        try {
            if (!message.equals("new player")) {
                String[] messageSplit = message.split(";");
                boolean exists = false;
                if (!messageSplit[0].equals(name) && !messageSplit[0].equals("")) {
                    for (int i = 0; i < players.size(); i++) {
                        PlayerItem playerItem = players.get(i);
                        if (playerItem.getName().equals(messageSplit[0])) {
                            exists = true;
                            if (playerItem.getLastUpdate() < Long.parseLong(messageSplit[2]) && mqtt) {
                                Log.i("message", "newer message");
                                players.remove(i);
                                PlayerItem item = new PlayerItem(messageSplit[0], Integer.parseInt(messageSplit[1]), Long.parseLong(messageSplit[2]), true);
                                players.add(item);
                                listener.onChange(players);
                                break;
                            }
                        }
                    }
                    if (!exists && !mqtt) {
                        Log.i("New player", messageSplit[0]);
                        PlayerItem item = new PlayerItem(messageSplit[0], Integer.parseInt(messageSplit[1]), Long.parseLong(messageSplit[2]), true);
                        players.add(item);
                        listener.onChange(players);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateNearbyScore() {
        Nearby.getMessagesClient(context).unpublish(mMessage);
        Date date = new Date();
        if (!name.equals("")) {
            String text = name + ";" + (score) + ";" + date.getTime();
            mMessage = new Message((text).getBytes());
            Log.i("tada", "score sent");
            try {
                mqtt.publish("score", text);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Nearby.getMessagesClient(context).publish(mMessage).addOnFailureListener(this);
            if (realtimeDatabaseEnabled) {
                try {
                    database.child("score").child(firebaseUser.getUid()).setValue(text);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }
}