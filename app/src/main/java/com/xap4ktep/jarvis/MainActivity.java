package com.xap4kter.jarvis;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "JarvisMain";

    private TextView tvStatus, tvCommand, tvResponse;
    private FloatingActionButton fabMic;
    private ImageButton btnSettings;
    private View waveOverlay;

    private TextToSpeech tts;
    private AudioManager audioManager;
    private boolean isListening = false;
    private boolean isSpeaking = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // BroadcastReceiver для получения команд из сервиса
    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra("command");
            if (command != null) {
                tvCommand.setText("Команда: " + command);
                processCommand(command);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvCommand = findViewById(R.id.tvCommand);
        tvResponse = findViewById(R.id.tvResponse);
        fabMic = findViewById(R.id.fabMic);
        btnSettings = findViewById(R.id.btnSettings);
        waveOverlay = findViewById(R.id.waveOverlay);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // Настройка TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru"));
                tts.setSpeechRate(0.9f);
            }
        });

        // Обработчик нажатия на микрофон
        fabMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Регистрация локального BroadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver,
                new IntentFilter("com.xap4kter.jarvis.COMMAND"));

        // Запуск сервиса
        startService(new Intent(this, AssistantService.class));

        // Проверка и запрос разрешений
        checkPermissions();
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };

        List<String> missingPermissions = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(p);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Проверяем, все ли разрешения даны
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Разрешение " + permissions[i] + " необходимо для работы", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startListening() {
        isListening = true;
        tvStatus.setText("Слушаю...");
        tvStatus.setTextColor(getColor(R.color.status_listening));
        fabMic.setImageResource(R.drawable.ic_mic_active);
        // Запускаем анимацию волн
        waveOverlay.setVisibility(View.VISIBLE);
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
        waveOverlay.startAnimation(pulse);

        // Отправляем команду сервису начать прослушивание
        Intent intent = new Intent("com.xap4kter.jarvis.START_LISTENING");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void stopListening() {
        isListening = false;
        tvStatus.setText("Ожидание");
        tvStatus.setTextColor(getColor(R.color.status_idle));
        fabMic.setImageResource(R.drawable.ic_mic_normal);
        waveOverlay.clearAnimation();
        waveOverlay.setVisibility(View.GONE);

        // Останавливаем прослушивание в сервисе
        Intent intent = new Intent("com.xap4kter.jarvis.STOP_LISTENING");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void processCommand(String command) {
        tvCommand.setText("Команда: " + command);
        // Отправляем команду в CommandProcessor (через сервис или напрямую)
        Intent intent = new Intent("com.xap4kter.jarvis.PROCESS_COMMAND");
        intent.putExtra("command", command);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void showResponse(String response) {
        mainHandler.post(() -> {
            tvResponse.setText("Ответ: " + response);
            if (isVoiceResponseEnabled()) {
                speak(response);
            }
        });
    }

    private boolean isVoiceResponseEnabled() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("voice_response", true);
    }

    private void speak(String text) {
        if (tts != null && !isSpeaking) {
            isSpeaking = true;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            // Слушаем окончание
            tts.setOnUtteranceProgressListener(new TextToSpeech.OnUtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) { }

                @Override
                public void onDone(String utteranceId) {
                    isSpeaking = false;
                }

                @Override
                public void onError(String utteranceId) {
                    isSpeaking = false;
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(commandReceiver);
        super.onDestroy();
    }
}