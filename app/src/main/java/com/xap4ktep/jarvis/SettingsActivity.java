package com.xap4kter.jarvis;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Switch switchVoiceResponse;
    private SeekBar seekBarSensitivity;
    private TextView tvSensitivityValue;
    private RadioGroup radioGroupTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Загружаем сохранённую тему ДО вызова super.onCreate и setContentView
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String theme = prefs.getString("theme", "purple");

        // Устанавливаем тему в зависимости от сохранённого значения
        switch (theme) {
            case "black":
                setTheme(R.style.Theme_Jarvis_Black);
                break;
            case "white":
                setTheme(R.style.Theme_Jarvis_White);
                break;
            default:
                setTheme(R.style.Theme_Jarvis_Purple);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Инициализация элементов интерфейса
        switchVoiceResponse = findViewById(R.id.switchVoiceResponse);
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);
        radioGroupTheme = findViewById(R.id.radioGroupTheme);

        // Загрузка текущих настроек
        boolean voiceResponse = prefs.getBoolean("voice_response", true);
        int sensitivity = prefs.getInt("sensitivity", 5);

        switchVoiceResponse.setChecked(voiceResponse);
        seekBarSensitivity.setProgress(sensitivity);
        tvSensitivityValue.setText(String.valueOf(sensitivity));

        // Установка радио-кнопки темы
        switch (theme) {
            case "black":
                radioGroupTheme.check(R.id.radioBlack);
                break;
            case "white":
                radioGroupTheme.check(R.id.radioWhite);
                break;
            default:
                radioGroupTheme.check(R.id.radioPurple);
                break;
        }

        // Слушатель для переключателя голосового ответа
        switchVoiceResponse.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("voice_response", isChecked).apply()
        );

        // Слушатель для ползунка чувствительности
        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSensitivityValue.setText(String.valueOf(progress));
                prefs.edit().putInt("sensitivity", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Слушатель для выбора темы
        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedTheme;
            if (checkedId == R.id.radioBlack) {
                selectedTheme = "black";
                // Для чёрной темы используем тёмный режим
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else if (checkedId == R.id.radioWhite) {
                selectedTheme = "white";
                // Для белой темы используем светлый режим
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                selectedTheme = "purple";
                // Для фиолетовой темы используем системный режим (по умолчанию светлый)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            // Сохраняем выбранную тему
            prefs.edit().putString("theme", selectedTheme).apply();
            // Пересоздаём активность, чтобы применить тему
            recreate();
        });
    }
}
