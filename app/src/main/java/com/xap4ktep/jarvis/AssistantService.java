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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Locale;

public class AssistantService extends Service {

    private static final String TAG = "AssistantService";
    private static final String CHANNEL_ID = "JarvisChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String KEYWORD = "джарвис";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
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

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "onError: " + error);
                if (!isSleepMode) {
                    restartListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (isSleepMode) return;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    for (String text : matches) {
                        String lowerText = text.toLowerCase(Locale.getDefault());
                        Log.d(TAG, "Recognized variant: " + lowerText);
                        if (lowerText.contains(KEYWORD) || lowerText.contains("джарис") || lowerText.contains("джарвиз")) {
                            String command = lowerText.replaceAll("джарвис|джарис|джарвиз", "").trim();
                            if (!command.isEmpty()) {
                                sendCommand(command);
                            }
                            break;
                        }
                    }
                }
                if (!isSleepMode) {
                    restartListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (isSleepMode) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    for (String text : matches) {
                        String lowerText = text.toLowerCase(Locale.getDefault());
                        if (lowerText.contains(KEYWORD)) {
                            // Досрочно обнаружили ключевое слово – можно начать новый сеанс для команды
                            speechRecognizer.cancel();
                            startListeningForCommand();
                            return;
                        }
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

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
        if (!isListening && !isSleepMode) {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Started listening");
        }
    }

    private void startListeningForCommand() {
        if (!isListening || isSleepMode) return;
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        if (isListening) {
            isListening = false;
            speechRecognizer.stopListening();
            Log.d(TAG, "Stopped listening");
        }
    }

    private void restartListening() {
        if (isListening && !isSleepMode) {
            speechRecognizer.cancel();
            handler.postDelayed(() -> {
                if (isListening && !isSleepMode) {
                    speechRecognizer.startListening(recognizerIntent);
                }
            }, 50);
        }
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
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
