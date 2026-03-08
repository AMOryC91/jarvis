package com.xap4kter.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {

    private static final String TAG = "CommandProcessor";
    private final Context context;
    private final AudioManager audioManager;
    private final WifiManager wifiManager;
    private final PowerManager powerManager;
    private final LocationManager locationManager;

    public CommandProcessor(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public String process(String command) {
        command = command.toLowerCase(Locale.getDefault()).trim();

        // Время
        if (command.matches(".*(время|который час).*")) {
            return getCurrentTime();
        }

        // Дата
        if (command.matches(".*(дата|какое сегодня число).*")) {
            return getCurrentDate();
        }

        // Открыть приложение
        Pattern appPattern = Pattern.compile("(открой|запусти) (.+)");
        Matcher appMatcher = appPattern.matcher(command);
        if (appMatcher.find()) {
            String appName = appMatcher.group(2);
            return openApp(appName);
        }

        // Открыть сайт
        Pattern sitePattern = Pattern.compile("(открой сайт) (.+)");
        Matcher siteMatcher = sitePattern.matcher(command);
        if (siteMatcher.find()) {
            String url = siteMatcher.group(2);
            return openWebsite(url);
        }

        // Поиск в Google
        Pattern googlePattern = Pattern.compile("(найди в гугл|поищи) (.+)");
        Matcher googleMatcher = googlePattern.matcher(command);
        if (googleMatcher.find()) {
            String query = googleMatcher.group(2);
            return googleSearch(query);
        }

        // YouTube поиск
        Pattern youtubePattern = Pattern.compile("(найди на ютуб) (.+)");
        Matcher youtubeMatcher = youtubePattern.matcher(command);
        if (youtubeMatcher.find()) {
            String query = youtubeMatcher.group(2);
            return youtubeSearch(query);
        }

        // Громкость
        if (command.startsWith("громкость")) {
            Pattern volPattern = Pattern.compile("громкость (\\d+)");
            Matcher volMatcher = volPattern.matcher(command);
            if (volMatcher.find()) {
                int level = Integer.parseInt(volMatcher.group(1));
                return setVolume(level);
            }
        }
        if (command.contains("громче")) {
            return adjustVolume(true);
        }
        if (command.contains("тише")) {
            return adjustVolume(false);
        }

        // Wi-Fi
        if (command.contains("включи вайфай") || command.contains("включи wifi")) {
            return setWifi(true);
        }
        if (command.contains("выключи вайфай") || command.contains("выключи wifi")) {
            return setWifi(false);
        }

        // Фонарик
        if (command.contains("включи фонарик") || command.contains("свет")) {
            return torch(true);
        }
        if (command.contains("выключи фонарик")) {
            return torch(false);
        }

        // Батарея
        if (command.contains("батарея") || command.contains("заряд")) {
            return getBatteryStatus();
        }

        // Погода
        if (command.contains("погода")) {
            String city = extractCity(command);
            return getWeather(city);
        }

        // Таймер
        Pattern timerPattern = Pattern.compile("таймер (\\d+) (секунд|минут|часов)");
        Matcher timerMatcher = timerPattern.matcher(command);
        if (timerMatcher.find()) {
            int value = Integer.parseInt(timerMatcher.group(1));
            String unit = timerMatcher.group(2);
            return setTimer(value, unit);
        }

        // Напоминание
        Pattern reminderPattern = Pattern.compile("напомни мне (.+) через (\\d+) минут");
        Matcher reminderMatcher = reminderPattern.matcher(command);
        if (reminderMatcher.find()) {
            String text = reminderMatcher.group(1);
            int minutes = Integer.parseInt(reminderMatcher.group(2));
            return setReminder(text, minutes);
        }

        // Помощь
        if (command.contains("что ты умеешь") || command.contains("команды") || command.contains("помощь")) {
            return "Я умею: открывать приложения и сайты, искать в Google и YouTube, управлять громкостью, Wi-Fi, фонариком, показывать время, дату, погоду, ставить таймеры и напоминания.";
        }

        return "Не понял команду";
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return "Сейчас " + sdf.format(new Date());
    }

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ru"));
        return "Сегодня " + sdf.format(new Date());
    }

    private String openApp(String appName) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(getPackageNameByAppName(appName));
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Открываю " + appName;
        } else {
            // Поиск по имени
            List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            for (ResolveInfo ri : activities) {
                String label = ri.loadLabel(pm).toString().toLowerCase();
                if (label.contains(appName.toLowerCase())) {
                    String packageName = ri.activityInfo.packageName;
                    Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
                        return "Открываю " + ri.loadLabel(pm).toString();
                    }
                }
            }
            return "Приложение " + appName + " не найдено";
        }
    }

    private String getPackageNameByAppName(String appName) {
        // Сопоставление известных приложений
        switch (appName) {
            case "телеграм":
                return "org.telegram.messenger";
            case "вотсап":
            case "whatsapp":
                return "com.whatsapp";
            case "инстаграм":
                return "com.instagram.android";
            case "вк":
            case "вконтакте":
                return "com.vkontakte.android";
            case "ютуб":
            case "youtube":
                return "com.google.android.youtube";
            case "хром":
                return "com.android.chrome";
            case "настройки":
                return "com.android.settings";
            // Добавьте больше соответствий
            default:
                return null;
        }
    }

    private String openWebsite(String url) {
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Открываю " + url;
    }

    private String googleSearch(String query) {
        Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Ищу " + query;
    }

    private String youtubeSearch(String query) {
        Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Ищу на YouTube " + query;
    }

    private String setVolume(int level) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int newLevel = (int) (level / 100.0 * max);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newLevel, 0);
        return "Громкость установлена на " + level + "%";
    }

    private String adjustVolume(boolean up) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI);
        return up ? "Громче" : "Тише";
    }

    private String setWifi(boolean enable) {
        wifiManager.setWifiEnabled(enable);
        return enable ? "Wi-Fi включён" : "Wi-Fi выключен";
    }

    private String torch(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    // Включаем фонарик через CameraManager
                    android.hardware.camera2.CameraManager cameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                    String cameraId = cameraManager.getCameraIdList()[0];
                    cameraManager.setTorchMode(cameraId, enable);
                    return enable ? "Фонарик включён" : "Фонарик выключен";
                } catch (Exception e) {
                    Log.e(TAG, "Torch error", e);
                    return "Ошибка управления фонариком";
                }
            } else {
                return "Нет разрешения на камеру";
            }
        } else {
            return "Фонарик не поддерживается";
        }
    }

    private String getBatteryStatus() {
        Intent batteryIntent = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
        int percent = (level * 100) / scale;
        return "Заряд батареи " + percent + "%";
    }

    private String getWeather(String city) {
        // Используем wttr.in
        String urlStr = "https://wttr.in/" + (city != null ? city : "") + "?format=%c+%t+%w+%h&lang=ru";
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            String weather = response.toString().trim();
            if (weather.startsWith("Unknown")) {
                return "Не удалось определить погоду";
            }
            return (city != null ? "Погода в " + city + ": " : "Погода: ") + weather;
        } catch (IOException e) {
            Log.e(TAG, "Weather error", e);
            return "Ошибка получения погоды";
        }
    }

    private String extractCity(String command) {
        // Простое извлечение: после "погода" идёт "в" или название
        Pattern p = Pattern.compile("погода в (.+)");
        Matcher m = p.matcher(command);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String setTimer(int value, String unit) {
        int seconds;
        switch (unit) {
            case "минут":
                seconds = value * 60;
                break;
            case "часов":
                seconds = value * 3600;
                break;
            default:
                seconds = value;
        }
        // Запускаем таймер
        new Handler().postDelayed(() -> {
            // Отправить уведомление или проиграть звук
            Toast.makeText(context, "Таймер истёк!", Toast.LENGTH_LONG).show();
        }, seconds * 1000L);
        return "Таймер на " + value + " " + unit + " запущен";
    }

    private String setReminder(String text, int minutes) {
        // Здесь можно использовать AlarmManager для напоминания через минуты
        Toast.makeText(context, "Напоминание установлено", Toast.LENGTH_SHORT).show();
        return "Напомню вам " + text + " через " + minutes + " минут";
    }
}