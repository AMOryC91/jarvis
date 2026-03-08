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
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.Locale;

public class AssistantService extends Service implements RecognitionListener {

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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        commandProcessor = new CommandProcessor(this);

        // Инициализация Vosk
        LibVosk.setLogLevel(LogLevel.INFO);
        try {
            // Пытаемся загрузить модель из assets (папка model)
            model = StorageService.unpack(this, "model", "model",
                    (completed, total) -> Log.d(TAG, "Unpacking " + completed + "/" + total));
            speechService = new SpeechService(model, 16000.0f);
            speechService.addListener(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize Vosk", e);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.START_LISTENING"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.STOP_LISTENING"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.PROCESS_COMMAND"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.SLEEP_MODE"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.WAKE_UP_MODE"));

        startListening();
    }

    private void startListening() {
        if (!isListening && !isSleepMode && speechService != null) {
            isListening = true;
            speechService.startListening();
            Log.d(TAG, "Started listening");
        }
    }

    private void stopListening() {
        if (isListening && speechService != null) {
            isListening = false;
            speechService.stop();
            Log.d(TAG, "Stopped listening");
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

    // ========== Реализация RecognitionListener ==========

    @Override
    public void onResult(String hypothesis) {
        Log.d(TAG, "onResult: " + hypothesis);
        // Гипотеза приходит в формате JSON, например: {"text": "привет мир"}
        // Парсим вручную или используем org.json
        String text = hypothesis;
        try {
            org.json.JSONObject json = new org.json.JSONObject(hypothesis);
            text = json.optString("text", "").toLowerCase(Locale.getDefault());
        } catch (org.json.JSONException e) {
            // Если не JSON, используем как есть
        }
        if (text.contains(KEYWORD) || text.contains("джарис") || text.contains("джарвиз")) {
            String command = text.replaceAll("джарвис|джарис|джарвиз", "").trim();
            if (!command.isEmpty()) {
                sendCommand(command);
            }
        }
        // Vosk не требует перезапуска – он продолжает слушать автоматически.
        // Если нужно остановить после ключевого слова, можно вызвать stop() потом start().
        // Но для непрерывного прослушивания оставляем как есть.
    }

    @Override
    public void onPartialResult(String hypothesis) {
        // Можно анализировать частичные результаты, но для ключевого слова достаточно полного
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Vosk error", e);
        // При ошибке пытаемся перезапустить
        if (!isSleepMode) {
            handler.postDelayed(this::startListening, 500);
        }
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Vosk timeout");
        // Таймаут – просто продолжаем слушать (SpeechService сам перезапустится)
    }

    // ====================================================

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
