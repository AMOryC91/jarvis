package com.xap4kter.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.util.Locale;

public class AssistantService extends Service {

    private static final String TAG = "AssistantService";
    private static final String CHANNEL_ID = "JarvisChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String KEYWORD = "джарвис";

    private Model model;
    private SpeechService speechService;
    private boolean isListening = false;
    private boolean isSleepMode = false;

    private CommandProcessor commandProcessor;
    private Handler handler = new Handler();

    private final BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case "com.xap4kter.jarvis.START_LISTENING":
                    startListening();
                    break;
                case "com.xap4kter.jarvis.STOP_LISTENING":
                    stopListening();
                    break;
                case "com.xap4kter.jarvis.PROCESS_COMMAND":
                    String cmd = intent.getStringExtra("command");
                    if (cmd != null) processCommand(cmd);
                    break;
                case "com.xap4kter.jarvis.SLEEP_MODE":
                    enterSleepMode();
                    break;
                case "com.xap4kter.jarvis.WAKE_UP_MODE":
                    exitSleepMode();
                    break;
            }
        }
    };

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onResult(String hypothesis) {
            Log.d(TAG, "onResult: " + hypothesis);
            String text = hypothesis;
            try {
                org.json.JSONObject json = new org.json.JSONObject(hypothesis);
                text = json.optString("text", "").toLowerCase(Locale.getDefault());
            } catch (org.json.JSONException e) {
                // ignore
            }
            if (text.contains(KEYWORD) || text.contains("джарис") || text.contains("джарвиз")) {
                String command = text.replaceAll("джарвис|джарис|джарвиз", "").trim();
                if (!command.isEmpty()) {
                    sendCommand(command);
                }
            }
        }

        @Override
        public void onPartialResult(String hypothesis) {}

        @Override
        public void onError(Exception e) {
            Log.e(TAG, "Vosk error", e);
            if (!isSleepMode) {
                handler.postDelayed(() -> restartListening(), 500);
            }
        }

        @Override
        public void onTimeout() {
            Log.d(TAG, "Vosk timeout");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        commandProcessor = new CommandProcessor(this);

        LibVosk.setLogLevel(LogLevel.INFO);

        // Распаковка модели из assets в внутреннее хранилище
        StorageService.unpack(this, "model", "model",
                (model) -> {
                    this.model = model;
                    initSpeechService();
                },
                (exception) -> {
                    Log.e(TAG, "Failed to unpack model", exception);
                });
    }

    private void initSpeechService() {
        if (model == null) return;
        speechService = new SpeechService(model, 16000.0f);
        speechService.startListening(recognitionListener);
        isListening = true;
        Log.d(TAG, "SpeechService started");
    }

    private void startListening() {
        if (!isSleepMode && speechService != null) {
            speechService.startListening(recognitionListener);
            isListening = true;
            Log.d(TAG, "Started listening");
        }
    }

    private void stopListening() {
        if (speechService != null) {
            speechService.stop();
            isListening = false;
            Log.d(TAG, "Stopped listening");
        }
    }

    private void restartListening() {
        if (!isSleepMode && speechService != null) {
            speechService.stop();
            speechService.startListening(recognitionListener);
        }
    }

    private void enterSleepMode() {
        isSleepMode = true;
        stopListening();
        Log.d(TAG, "Entered sleep mode");
    }

    private void exitSleepMode() {
        isSleepMode = false;
        startListening();
        Log.d(TAG, "Exited sleep mode");
    }

    private void sendCommand(String command) {
        Intent intent = new Intent("com.xap4kter.jarvis.COMMAND");
        intent.putExtra("command", command);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void processCommand(String command) {
        String response = commandProcessor.process(command);
        Intent intent = new Intent("com.xap4kter.jarvis.RESPONSE");
        intent.putExtra("response", response);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
        if (model != null) {
            model.close();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Jarvis")
                .setContentText("Ассистент работает в фоне")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
