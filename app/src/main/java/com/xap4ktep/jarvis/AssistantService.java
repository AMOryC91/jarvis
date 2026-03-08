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
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Locale;

public class AssistantService extends Service {

    private static final String TAG = "AssistantService";
    private static final String CHANNEL_ID = "JarvisChannel";
    private static final int NOTIFICATION_ID = 1;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private boolean isKeywordDetected = false;
    private final String KEYWORD = "джарвис";

    private CommandProcessor commandProcessor;

    private final BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "com.xap4kter.jarvis.START_LISTENING":
                        startListening();
                        break;
                    case "com.xap4kter.jarvis.STOP_LISTENING":
                        stopListening();
                        break;
                    case "com.xap4kter.jarvis.PROCESS_COMMAND":
                        String cmd = intent.getStringExtra("command");
                        if (cmd != null) {
                            processCommand(cmd);
                        }
                        break;
                }
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
            public void onRmsChanged(float rmsdB) {
                // Можно передавать для визуализации
            }

            @Override
            public void onBufferReceived(byte[] buffer) { }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                if (!isKeywordDetected) {
                    // Если ключевое слово не обнаружено, перезапускаем прослушивание
                    restartListening();
                }
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "onError: " + error);
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).toLowerCase(Locale.getDefault());
                    Log.d(TAG, "Recognized: " + text);
                    if (!isKeywordDetected) {
                        // Ищем ключевое слово
                        if (text.contains(KEYWORD)) {
                            isKeywordDetected = true;
                            // Очищаем команду от ключевого слова
                            String command = text.replace(KEYWORD, "").trim();
                            if (command.isEmpty()) {
                                // Если после ключевого слова ничего нет, продолжаем слушать дальше
                                restartListening();
                            } else {
                                sendCommand(command);
                            }
                        } else {
                            // Нет ключевого слова, продолжаем слушать
                            restartListening();
                        }
                    } else {
                        // Мы уже в режиме ожидания команды после ключевого слова
                        sendCommand(text);
                        isKeywordDetected = false; // Сброс после выполнения команды
                    }
                } else {
                    restartListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).toLowerCase(Locale.getDefault());
                    // Можно анализировать частичные результаты для более быстрого обнаружения ключевого слова
                    if (!isKeywordDetected && text.contains(KEYWORD)) {
                        isKeywordDetected = true;
                        // Прерываем текущий сеанс и начинаем новый для команды
                        speechRecognizer.cancel();
                        startListeningForCommand();
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) { }
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.START_LISTENING"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.STOP_LISTENING"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter("com.xap4kter.jarvis.PROCESS_COMMAND"));
    }

    private void startListening() {
        if (!isListening) {
            isListening = true;
            isKeywordDetected = false;
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void startListeningForCommand() {
        // Запускаем распознавание специально для команды (без поиска ключевого слова)
        // Можно использовать тот же recognizer, просто сбросить флаг
        isKeywordDetected = true; // чтобы не искать ключевое слово в результатах
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        if (isListening) {
            isListening = false;
            isKeywordDetected = false;
            speechRecognizer.stopListening();
        }
    }

    private void restartListening() {
        if (isListening) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void sendCommand(String command) {
        // Отправляем команду в MainActivity через локальный broadcast
        Intent intent = new Intent("com.xap4kter.jarvis.COMMAND");
        intent.putExtra("command", command);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void processCommand(String command) {
        String response = commandProcessor.process(command);
        // Отправляем ответ в MainActivity
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
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
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
                .setSmallIcon(android.R.drawable.ic_media_play) // Замените на свою иконку
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}