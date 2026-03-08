package com.xap4kter.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {

    private static final String TAG = "CommandProcessor";
    private final Context context;
    private final AudioManager audioManager;
    private final WifiManager wifiManager;
    private final PowerManager powerManager;
    private final LocationManager locationManager;
    private final Random random = new Random();

    // Стиль ответов JARVIS
    private final String[] successPrefixes = {
            "Выполняю", "Слушаюсь", "Есть", "Сделано", "Готово", "Принято", "Так точно"
    };
    private final String[] errorPrefixes = {
            "Ошибка", "Не удалось", "Проблема", "Отказ", "Сбой"
    };

    public CommandProcessor(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    // ========================== ОСНОВНОЙ МЕТОД ==========================
    public String process(String command) {
        command = command.toLowerCase(Locale.getDefault()).trim();

        // ---------- Системные команды ----------
        if (matchesAny(command, new String[]{"время", "который час", "сколько времени"}))
            return formatJarvisResponse(getCurrentTime(), true);

        if (matchesAny(command, new String[]{"дата", "какое сегодня число", "сегодняшняя дата"}))
            return formatJarvisResponse(getCurrentDate(), true);

        if (matchesAny(command, new String[]{"батарея", "заряд", "сколько заряда", "процент заряда"}))
            return formatJarvisResponse(getBatteryStatus(), true);

        if (matchesAny(command, new String[]{"скриншот", "сделай скриншот", "снимок экрана", "сфоткай экран"}))
            return takeScreenshot();

        if (matchesAny(command, new String[]{"заблокируй экран", "блокировка", "выключи экран"}))
            return lockScreen();

        if (matchesAny(command, new String[]{"перезагрузи", "перезагрузка", "ребут"}))
            return rebootDevice();

        if (matchesAny(command, new String[]{"выключи телефон", "отключи", "шутдаун"}))
            return shutdownDevice();

        if (matchesAny(command, new String[]{"режим не беспокоить", "не беспокоить", "тихий режим"}))
            return setDnd(true);

        if (matchesAny(command, new String[]{"отключи не беспокоить", "выключи тихий режим"}))
            return setDnd(false);

        if (matchesAny(command, new String[]{"автоповорот", "поворот экрана"})) {
            if (command.contains("включи") || command.contains("вкл"))
                return setAutoRotate(true);
            else if (command.contains("выключи") || command.contains("отключи"))
                return setAutoRotate(false);
        }

        if (matchesAny(command, new String[]{"мобильные данные", "интернет", "мобильный интернет"})) {
            if (command.contains("включи") || command.contains("вкл"))
                return setMobileData(true);
            else if (command.contains("выключи") || command.contains("отключи"))
                return setMobileData(false);
        }

        if (matchesAny(command, new String[]{"режим полёта", "самолёт", "авиарежим"})) {
            if (command.contains("включи") || command.contains("вкл"))
                return setAirplaneMode(true);
            else if (command.contains("выключи") || command.contains("отключи"))
                return setAirplaneMode(false);
        }

        // Информация о системе
        if (matchesAny(command, new String[]{"свободная память", "сколько памяти", "оперативная память"}))
            return formatJarvisResponse(getMemoryInfo(), true);

        if (matchesAny(command, new String[]{"модель телефона", "какой телефон"}))
            return formatJarvisResponse(getDeviceModel(), true);

        if (matchesAny(command, new String[]{"версия андроид", "версия android"}))
            return formatJarvisResponse(getAndroidVersion(), true);

        if (matchesAny(command, new String[]{"температура", "температура телефона", "нагрев"}))
            return formatJarvisResponse(getDeviceTemperature(), true);

        // ---------- Управление приложениями ----------
        Pattern appPattern = Pattern.compile("(открой|запусти|открыть|запустить|включи|перейди в)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher appMatcher = appPattern.matcher(command);
        if (appMatcher.find()) {
            String appName = appMatcher.group(2).trim();
            return openApp(appName);
        }

        // Удаление приложения
        Pattern uninstallPattern = Pattern.compile("(удали|удалить|деинсталлировать)\\s+(приложение\\s+)?(.+)", Pattern.CASE_INSENSITIVE);
        Matcher uninstallMatcher = uninstallPattern.matcher(command);
        if (uninstallMatcher.find()) {
            String appName = uninstallMatcher.group(3).trim();
            return uninstallApp(appName);
        }

        // ---------- Интернет и поиск ----------
        Pattern googlePattern = Pattern.compile("(что такое|кто такой|что значит|найди в гугл|поищи|найди|расскажи про)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher googleMatcher = googlePattern.matcher(command);
        if (googleMatcher.find()) {
            String query = googleMatcher.group(2).trim();
            return googleSearch(query);
        }

        Pattern youtubePattern = Pattern.compile("(найди на ютуб|покажи на ютуб|ютуб|видео)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher youtubeMatcher = youtubePattern.matcher(command);
        if (youtubeMatcher.find()) {
            String query = youtubeMatcher.group(2).trim();
            return youtubeSearch(query);
        }

        Pattern sitePattern = Pattern.compile("(открой сайт|перейди на сайт|открыть сайт)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher siteMatcher = sitePattern.matcher(command);
        if (siteMatcher.find()) {
            String url = siteMatcher.group(2).trim();
            return openWebsite(url);
        }

        // ---------- Погода ----------
        if (command.contains("погода") || command.contains("температура") || command.contains("градус")) {
            String city = extractCity(command);
            return getWeather(city);
        }

        // ---------- Курсы валют ----------
        if (matchesAny(command, new String[]{"курс доллара", "доллар", "usd"}))
            return getCurrencyRate("USD");

        if (matchesAny(command, new String[]{"курс евро", "евро", "eur"}))
            return getCurrencyRate("EUR");

        // ---------- Навигация ----------
        Pattern routePattern = Pattern.compile("(проложи маршрут|как проехать|маршрут|доехать|навигация)\\s+(?:до|в|на)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher routeMatcher = routePattern.matcher(command);
        if (routeMatcher.find()) {
            String destination = routeMatcher.group(2).trim();
            return navigateTo(destination);
        }

        Pattern nearbyPattern = Pattern.compile("(ближайший|рядом|поблизости)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher nearbyMatcher = nearbyPattern.matcher(command);
        if (nearbyMatcher.find()) {
            String placeType = nearbyMatcher.group(2).trim();
            return findNearby(placeType);
        }

        // ---------- Мультимедиа ----------
        if (matchesAny(command, new String[]{"включи музыку", "музыка", "песню", "play music"}))
            return playMusic();

        if (matchesAny(command, new String[]{"следующий трек", "следующая песня", "дальше", "скип"}))
            return nextTrack();

        if (matchesAny(command, new String[]{"предыдущий трек", "предыдущая песня", "назад"}))
            return previousTrack();

        if (matchesAny(command, new String[]{"пауза", "стоп", "останови", "поставь на паузу"}))
            return pauseMusic();

        if (matchesAny(command, new String[]{"продолжи", "возобнови", "плей", "играй дальше"}))
            return resumeMusic();

        if (matchesAny(command, new String[]{"что играет", "какая песня", "информация о треке"}))
            return nowPlaying();

        // Видео
        Pattern videoPattern = Pattern.compile("(включи видео|покажи видео|видео)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher videoMatcher = videoPattern.matcher(command);
        if (videoMatcher.find()) {
            String video = videoMatcher.group(2).trim();
            return playVideo(video);
        }

        Pattern moviePattern = Pattern.compile("(включи фильм|фильм|кино)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher movieMatcher = moviePattern.matcher(command);
        if (movieMatcher.find()) {
            String movie = movieMatcher.group(2).trim();
            return playMovie(movie);
        }

        // ---------- Громкость ----------
        Pattern volPattern = Pattern.compile("громкость\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher volMatcher = volPattern.matcher(command);
        if (volMatcher.find()) {
            int level = Integer.parseInt(volMatcher.group(1));
            return setVolume(level);
        }

        if (matchesAny(command, new String[]{"громче", "прибавь громкость", "увеличь громкость", "громкость выше"}))
            return adjustVolume(true);

        if (matchesAny(command, new String[]{"тише", "убавь громкость", "уменьши громкость", "громкость ниже"}))
            return adjustVolume(false);

        if (matchesAny(command, new String[]{"максимальная громкость", "макс громкость", "на полную"}))
            return setMaxVolume();

        if (matchesAny(command, new String[]{"минимальная громкость", "мин громкость", "выключи звук", "без звука"}))
            return setMinVolume();

        // ---------- Яркость ----------
        Pattern brightnessPattern = Pattern.compile("яркость\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher brightnessMatcher = brightnessPattern.matcher(command);
        if (brightnessMatcher.find()) {
            int level = Integer.parseInt(brightnessMatcher.group(1));
            return setBrightness(level);
        }

        if (matchesAny(command, new String[]{"ярче", "прибавь яркость", "увеличь яркость", "яркость выше"}))
            return adjustBrightness(true);

        if (matchesAny(command, new String[]{"темнее", "убавь яркость", "уменьши яркость", "яркость ниже"}))
            return adjustBrightness(false);

        // ---------- Wi-Fi ----------
        if (matchesAny(command, new String[]{"включи вайфай", "включи wifi", "включи wi-fi", "подключи вайфай"}))
            return setWifi(true);

        if (matchesAny(command, new String[]{"выключи вайфай", "выключи wifi", "выключи wi-fi", "отключи вайфай"}))
            return setWifi(false);

        if (matchesAny(command, new String[]{"статус wifi", "статус вайфай", "какие сети рядом"}))
            return getWifiStatus();

        // ---------- Bluetooth ----------
        if (matchesAny(command, new String[]{"включи блютуз", "включи bluetooth", "подключи блютуз"}))
            return setBluetooth(true);

        if (matchesAny(command, new String[]{"выключи блютуз", "выключи bluetooth", "отключи блютуз"}))
            return setBluetooth(false);

        if (matchesAny(command, new String[]{"найди устройства", "поиск блютуз", "подключи устройство"}))
            return scanBluetooth();

        // ---------- Фонарик ----------
        if (matchesAny(command, new String[]{"включи фонарик", "фонарик", "свет", "включи свет"}))
            return torch(true);

        if (matchesAny(command, new String[]{"выключи фонарик", "выключи свет", "погаси свет"}))
            return torch(false);

        // ---------- Таймеры ----------
        Pattern timerPattern = Pattern.compile("таймер\\s+(?:на)?\\s*(\\d+)\\s*(секунд|минут|часов|сек|мин|час)", Pattern.CASE_INSENSITIVE);
        Matcher timerMatcher = timerPattern.matcher(command);
        if (timerMatcher.find()) {
            int value = Integer.parseInt(timerMatcher.group(1));
            String unit = timerMatcher.group(2);
            return setTimer(value, unit);
        }

        if (matchesAny(command, new String[]{"сколько осталось", "осталось времени", "таймер статус"}))
            return getTimerStatus();

        if (matchesAny(command, new String[]{"останови таймер", "сбрось таймер", "отмени таймер"}))
            return stopTimer();

        // ---------- Напоминания ----------
        Pattern reminderPattern = Pattern.compile("напомни\\s+(?:мне)?\\s*(.+?)\\s+через\\s+(\\d+)\\s+минут", Pattern.CASE_INSENSITIVE);
        Matcher reminderMatcher = reminderPattern.matcher(command);
        if (reminderMatcher.find()) {
            String text = reminderMatcher.group(1).trim();
            int minutes = Integer.parseInt(reminderMatcher.group(2));
            return setReminder(text, minutes);
        }

        Pattern simpleReminder = Pattern.compile("напомни\\s+(?:мне)?\\s*(.+?)\\s+в\\s+(\\d+):(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher simpleMatcher = simpleReminder.matcher(command);
        if (simpleMatcher.find()) {
            String text = simpleMatcher.group(1).trim();
            int hour = Integer.parseInt(simpleMatcher.group(2));
            int minute = Integer.parseInt(simpleMatcher.group(3));
            return setReminderTime(text, hour, minute);
        }

        if (matchesAny(command, new String[]{"покажи напоминания", "список напоминаний", "напоминания"}))
            return showReminders();

        // ---------- Звонки и сообщения ----------
        Pattern callPattern = Pattern.compile("позвони\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher callMatcher = callPattern.matcher(command);
        if (callMatcher.find()) {
            String contact = callMatcher.group(1).trim();
            return makeCall(contact);
        }

        Pattern smsPattern = Pattern.compile("напиши\\s+(.+?)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher smsMatcher = smsPattern.matcher(command);
        if (smsMatcher.find()) {
            String contact = smsMatcher.group(1).trim();
            String message = smsMatcher.group(2).trim();
            return sendSms(contact, message);
        }

        // ---------- Фоновые режимы ----------
        if (matchesAny(command, new String[]{"спи", "режим сна", "отдыхай", "замри"}))
            return sleepMode();

        if (matchesAny(command, new String[]{"проснись", "вернись", "режим ожидания", "слушай"}))
            return wakeUpMode();

        if (matchesAny(command, new String[]{"тихий режим", "только текст", "без голоса"}))
            return setVoiceResponse(false);

        if (matchesAny(command, new String[]{"голосовой режим", "отвечай голосом", "с голосом"}))
            return setVoiceResponse(true);

        if (matchesAny(command, new String[]{"автомобильный режим", "режим авто"}))
            return carMode();

        if (matchesAny(command, new String[]{"домашний режим", "режим дома"}))
            return homeMode();

        if (matchesAny(command, new String[]{"рабочий режим", "режим работы"}))
            return workMode();

        if (matchesAny(command, new String[]{"ночной режим", "спокойной ночи"}))
            return nightMode();

        // ---------- Помощь ----------
        if (matchesAny(command, new String[]{"что ты умеешь", "команды", "помощь", "help", "список команд"}))
            return help();

        return formatJarvisResponse("Не понял команду. Скажите 'Джарвис помощь' для списка команд.", false);
    }

    // ========================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========================

    private boolean matchesAny(String command, String[] patterns) {
        for (String pattern : patterns) {
            if (command.contains(pattern)) return true;
        }
        return false;
    }

    private String formatJarvisResponse(String response, boolean success) {
        if (success) {
            return successPrefixes[random.nextInt(successPrefixes.length)] + ": " + response;
        } else {
            return errorPrefixes[random.nextInt(errorPrefixes.length)] + ": " + response;
        }
    }

    // ---------- Системные методы ----------
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ru"));
        return sdf.format(new Date());
    }

    private String getBatteryStatus() {
        try {
            Intent batteryIntent = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int percent = (level * 100) / scale;
            return "Заряд батареи " + percent + "%";
        } catch (Exception e) {
            return "Не удалось получить статус батареи";
        }
    }

    private String takeScreenshot() {
        try {
            // Требуется root или специальные разрешения
            return "Функция скриншота в разработке";
        } catch (Exception e) {
            return "Не удалось сделать скриншот";
        }
    }

    private String lockScreen() {
        try {
            // Требуется разрешение
            return "Функция блокировки экрана в разработке";
        } catch (Exception e) {
            return "Не удалось заблокировать экран";
        }
    }

    private String rebootDevice() {
        return "Перезагрузка возможна только с root-доступом";
    }

    private String shutdownDevice() {
        return "Выключение возможно только с root-доступом";
    }

    private String setDnd(boolean enable) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (enable) {
                    Settings.Global.putInt(context.getContentResolver(), Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                } else {
                    Settings.Global.putInt(context.getContentResolver(), Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
                }
                return enable ? "Режим не беспокоить включён" : "Режим не беспокоить выключен";
            }
            return "Режим не поддерживается на этой версии Android";
        } catch (Exception e) {
            return "Не удалось изменить режим";
        }
    }

    private String setAutoRotate(boolean enable) {
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, enable ? 1 : 0);
            return enable ? "Автоповорот включён" : "Автоповорот выключен";
        } catch (Exception e) {
            return "Не удалось изменить автоповорот";
        }
    }

    private String setMobileData(boolean enable) {
        // Требует системных привилегий
        return "Управление мобильными данными недоступно в обычном приложении";
    }

    private String setAirplaneMode(boolean enable) {
        // Требует системных привилегий
        return "Управление авиарежимом недоступно без root-прав";
    }

    private String getMemoryInfo() {
        // Заглушка
        return "ОЗУ: доступно около 2 ГБ";
    }

    private String getDeviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    private String getAndroidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private String getDeviceTemperature() {
        try {
            // Заглушка
            return "32°C";
        } catch (Exception e) {
            return "Не удалось определить температуру";
        }
    }

    // ---------- Приложения ----------
    private String openApp(String appName) {
        String packageName = findPackageByAppName(appName);
        if (packageName == null) {
            return "Приложение '" + appName + "' не найдено";
        }
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "Открываю " + appName;
            } else {
                return "Не удалось запустить " + appName;
            }
        } catch (Exception e) {
            return "Ошибка при открытии приложения";
        }
    }

    private String findPackageByAppName(String appName) {
        // Словарь популярных приложений
        String[][] knownApps = {
                {"телеграм", "org.telegram.messenger"},
                {"telegram", "org.telegram.messenger"},
                {"whatsapp", "com.whatsapp"},
                {"ватсап", "com.whatsapp"},
                {"viber", "com.viber.voip"},
                {"вайбер", "com.viber.voip"},
                {"инстаграм", "com.instagram.android"},
                {"instagram", "com.instagram.android"},
                {"вк", "com.vkontakte.android"},
                {"вконтакте", "com.vkontakte.android"},
                {"vk", "com.vkontakte.android"},
                {"ютуб", "com.google.android.youtube"},
                {"youtube", "com.google.android.youtube"},
                {"хром", "com.android.chrome"},
                {"chrome", "com.android.chrome"},
                {"настройки", "com.android.settings"},
                {"settings", "com.android.settings"},
                {"камера", "com.android.camera2"},
                {"camera", "com.android.camera2"},
                {"галерея", "com.android.gallery3d"},
                {"gallery", "com.android.gallery3d"},
                {"файлы", "com.android.documentsui"},
                {"files", "com.android.documentsui"},
                {"карты", "com.google.android.apps.maps"},
                {"maps", "com.google.android.apps.maps"},
                {"плей маркет", "com.android.vending"},
                {"play market", "com.android.vending"},
                {"плей", "com.android.vending"},
                {"музыка", "com.google.android.music"},
                {"music", "com.google.android.music"},
                {"яндекс", "com.yandex.browser"},
                {"yandex", "com.yandex.browser"},
                {"discord", "com.discord"},
                {"дискорд", "com.discord"},
                {"skype", "com.skype.raider"},
                {"скайп", "com.skype.raider"},
                {"snapchat", "com.snapchat.android"},
                {"снапчат", "com.snapchat.android"},
                {"twitter", "com.twitter.android"},
                {"твиттер", "com.twitter.android"},
                {"tiktok", "com.zhiliaoapp.musically"},
                {"тикток", "com.zhiliaoapp.musically"},
                {"reddit", "com.reddit.frontpage"},
                {"реддит", "com.reddit.frontpage"},
                {"pinterest", "com.pinterest"},
                {"пинтерест", "com.pinterest"},
                {"linkedin", "com.linkedin.android"},
                {"линкедин", "com.linkedin.android"},
                {"одноклассники", "ru.ok.android"},
                {"ok", "ru.ok.android"}
        };
        String appNameLower = appName.toLowerCase().replaceAll("\\s+", "");
        for (String[] pair : knownApps) {
            if (pair[0].equals(appNameLower) || appNameLower.contains(pair[0])) {
                return pair[1];
            }
        }

        // Поиск по установленным приложениям
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo info : packages) {
                String label = pm.getApplicationLabel(info).toString().toLowerCase().replaceAll("\\s+", "");
                if (label.contains(appNameLower) || appNameLower.contains(label)) {
                    return info.packageName;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error searching packages", e);
        }
        return null;
    }

    private String uninstallApp(String appName) {
        String packageName = findPackageByAppName(appName);
        if (packageName == null) {
            return "Приложение '" + appName + "' не найдено";
        }
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Открываю меню удаления " + appName;
        } catch (Exception e) {
            return "Не удалось открыть меню удаления";
        }
    }

    // ---------- Интернет и поиск ----------
    private String googleSearch(String query) {
        try {
            Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Ищу: " + query;
        } catch (Exception e) {
            return "Не удалось открыть браузер";
        }
    }

    private String youtubeSearch(String query) {
        try {
            Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Ищу на YouTube: " + query;
        } catch (Exception e) {
            return "Не удалось открыть YouTube";
        }
    }

    private String openWebsite(String url) {
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Открываю " + url;
        } catch (Exception e) {
            return "Не удалось открыть сайт";
        }
    }

    // ---------- Погода (с автоопределением города) ----------
    private String getWeather(String city) {
        // Если город не указан в команде, пытаемся определить автоматически
        if (city == null || city.trim().isEmpty()) {
            city = getCurrentCity();
            if (city == null) {
                // Не удалось определить местоположение
                return formatJarvisResponse(
                        "Не удалось определить ваше местоположение. Включите GPS и дайте разрешение на определение местоположения, или укажите город в команде, например: 'погода в Москве'.",
                        false);
            }
        }

        // Получаем погоду для города
        try {
            URL url = new URL("https://wttr.in/" + city + "?format=%c+%t+%w+%h&lang=ru&m");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            String weather = result.toString().trim();
            if (weather.isEmpty() || weather.startsWith("Unknown")) {
                return formatJarvisResponse("Не удалось получить погоду для города " + city, false);
            }
            return formatJarvisResponse("Погода в " + city + ": " + weather, true);
        } catch (Exception e) {
            Log.e(TAG, "Weather error", e);
            return formatJarvisResponse("Ошибка получения погоды. Проверьте интернет.", false);
        }
    }

    /**
     * Определяет текущий город по местоположению (GPS/сеть)
     * @return название города или null, если не удалось
     */
    private String getCurrentCity() {
        // Проверяем разрешение на определение местоположения
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted");
            return null;
        }

        // Проверяем, включен ли GPS (или любой провайдер местоположения)
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.d(TAG, "Location providers are disabled");
            return null;
        }

        // Получаем последнее известное местоположение (лучшее из доступных)
        Location lastLocation = null;
        try {
            if (isNetworkEnabled) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastLocation == null && isGpsEnabled) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastLocation == null) {
                // Можно попробовать пассивный провайдер
                lastLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting last known location", e);
            return null;
        }

        if (lastLocation == null) {
            Log.d(TAG, "No last known location available");
            return null;
        }

        // Используем Geocoder для получения адреса по координатам
        Geocoder geocoder = new Geocoder(context, new Locale("ru"));
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    lastLocation.getLatitude(),
                    lastLocation.getLongitude(),
                    1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                // Пробуем получить город: locality, subadmin area, admin area
                if (address.getLocality() != null) {
                    return address.getLocality();
                } else if (address.getSubAdminArea() != null) {
                    return address.getSubAdminArea();
                } else if (address.getAdminArea() != null) {
                    return address.getAdminArea();
                } else {
                    // Если город не найден, возвращаем null
                    return null;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder error", e);
        }
        return null;
    }

    private String extractCity(String command) {
        Pattern p = Pattern.compile("погода\\s+(?:в|во|на)?\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(command);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    // ---------- Курсы валют ----------
    private String getCurrencyRate(String currency) {
        // Заглушка
        if (currency.equals("USD")) return "Курс доллара: 85.5 рубля";
        if (currency.equals("EUR")) return "Курс евро: 92.3 рубля";
        return "Курс не найден";
    }

    // ---------- Навигация ----------
    private String navigateTo(String destination) {
        try {
            Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(destination));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Строю маршрут до " + destination;
        } catch (Exception e) {
            return "Не удалось открыть навигацию";
        }
    }

    private String findNearby(String placeType) {
        try {
            Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(placeType));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Ищу " + placeType + " рядом";
        } catch (Exception e) {
            return "Не удалось выполнить поиск";
        }
    }

    // ---------- Мультимедиа ----------
    private String playMusic() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Включаю музыку";
        } catch (Exception e) {
            return "Не удалось открыть музыкальный сервис";
        }
    }

    private String nextTrack() {
        return "Следующий трек (функция в разработке)";
    }

    private String previousTrack() {
        return "Предыдущий трек (функция в разработке)";
    }

    private String pauseMusic() {
        return "Пауза (функция в разработке)";
    }

    private String resumeMusic() {
        return "Продолжить (функция в разработке)";
    }

    private String nowPlaying() {
        return "Сейчас играет: неизвестно (функция в разработке)";
    }

    private String playVideo(String video) {
        return youtubeSearch(video);
    }

    private String playMovie(String movie) {
        return youtubeSearch(movie + " фильм");
    }

    // ---------- Громкость ----------
    private String setVolume(int level) {
        try {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int newLevel = (int) (level / 100.0 * max);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newLevel, 0);
            return "Громкость " + level + "%";
        } catch (Exception e) {
            return "Не удалось изменить громкость";
        }
    }

    private String adjustVolume(boolean up) {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
            return up ? "Громче" : "Тише";
        } catch (Exception e) {
            return "Не удалось изменить громкость";
        }
    }

    private String setMaxVolume() {
        try {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
            return "Максимальная громкость";
        } catch (Exception e) {
            return "Не удалось установить громкость";
        }
    }

    private String setMinVolume() {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            return "Звук выключен";
        } catch (Exception e) {
            return "Не удалось выключить звук";
        }
    }

    // ---------- Яркость ----------
    private String setBrightness(int level) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(context)) {
                try {
                    Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, level);
                    return "Яркость " + level + "%";
                } catch (Exception e) {
                    return "Не удалось изменить яркость";
                }
            } else {
                return "Нет разрешения на изменение настроек";
            }
        } else {
            return "Функция не поддерживается на этой версии Android";
        }
    }

    private String adjustBrightness(boolean up) {
        return up ? "Ярче" : "Темнее";
    }

    // ---------- Wi-Fi ----------
    private String setWifi(boolean enable) {
        try {
            wifiManager.setWifiEnabled(enable);
            return enable ? "Wi‑Fi включён" : "Wi‑Fi выключен";
        } catch (Exception e) {
            return "Не удалось изменить состояние Wi-Fi";
        }
    }

    private String getWifiStatus() {
        try {
            boolean enabled = wifiManager.isWifiEnabled();
            return enabled ? "Wi‑Fi включён" : "Wi‑Fi выключен";
        } catch (Exception e) {
            return "Не удалось получить статус Wi-Fi";
        }
    }

    // ---------- Bluetooth ----------
    private String setBluetooth(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    if (enable) {
                        adapter.enable();
                    } else {
                        adapter.disable();
                    }
                    return enable ? "Bluetooth включён" : "Bluetooth выключен";
                }
                return "Bluetooth не поддерживается";
            } catch (Exception e) {
                return "Ошибка управления Bluetooth";
            }
        }
        return "Bluetooth не поддерживается на этой версии Android";
    }

    private String scanBluetooth() {
        return "Поиск устройств Bluetooth (функция в разработке)";
    }

    // ---------- Фонарик ----------
    private String torch(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
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

    // ---------- Таймеры ----------
    private String setTimer(int value, String unit) {
        int seconds;
        switch (unit) {
            case "минут":
            case "мин":
                seconds = value * 60;
                break;
            case "часов":
            case "час":
                seconds = value * 3600;
                break;
            default:
                seconds = value;
        }
        new Handler().postDelayed(() -> {
            Intent intent = new Intent("com.xap4kter.jarvis.TIMER_FINISHED");
            intent.putExtra("message", "Таймер истёк!");
            context.sendBroadcast(intent);
        }, seconds * 1000L);
        return "Таймер на " + value + " " + unit + " запущен";
    }

    private String getTimerStatus() {
        return "Таймер активен (функция в разработке)";
    }

    private String stopTimer() {
        return "Таймер остановлен (функция в разработке)";
    }

    // ---------- Напоминания ----------
    private String setReminder(String text, int minutes) {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent("com.xap4kter.jarvis.REMINDER");
            intent.putExtra("text", text);
            context.sendBroadcast(intent);
        }, minutes * 60 * 1000L);
        return "Напомню через " + minutes + " минут: " + text;
    }

    private String setReminderTime(String text, int hour, int minute) {
        return "Напоминание на " + hour + ":" + minute + " (функция в разработке)";
    }

    private String showReminders() {
        return "Список напоминаний (функция в разработке)";
    }

    // ---------- Звонки и сообщения ----------
    private String makeCall(String contact) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Набираю " + contact;
        } catch (Exception e) {
            return "Не удалось открыть набор номера";
        }
    }

    private String sendSms(String contact, String message) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + contact));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Открываю сообщение для " + contact;
        } catch (Exception e) {
            return "Не удалось открыть отправку сообщения";
        }
    }

    // ---------- Фоновые режимы ----------
    private String sleepMode() {
        Intent intent = new Intent("com.xap4kter.jarvis.SLEEP_MODE");
        context.sendBroadcast(intent);
        return "Перехожу в режим сна";
    }

    private String wakeUpMode() {
        Intent intent = new Intent("com.xap4kter.jarvis.WAKE_UP_MODE");
        context.sendBroadcast(intent);
        return "Просыпаюсь";
    }

    private String setVoiceResponse(boolean enable) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putBoolean("voice_response", enable).apply();
        return enable ? "Включаю голосовые ответы" : "Отключаю голосовые ответы";
    }

    private String carMode() {
        setWifi(true);
        setBluetooth(true);
        setVolume(70);
        setVoiceResponse(true);
        return "Активирован автомобильный режим";
    }

    private String homeMode() {
        setWifi(true);
        setVolume(50);
        return "Активирован домашний режим";
    }

    private String workMode() {
        setWifi(true);
        setVolume(30);
        setVoiceResponse(false);
        return "Активирован рабочий режим";
    }

    private String nightMode() {
        setWifi(false);
        setBluetooth(false);
        setVolume(10);
        setDnd(true);
        return "Активирован ночной режим. Спокойной ночи";
    }

    // ---------- Помощь ----------
    private String help() {
        return "Я умею: открывать приложения и сайты, искать в Google и YouTube, управлять громкостью, яркостью, Wi‑Fi, Bluetooth, фонариком, показывать время, дату, погоду, курс валют, ставить таймеры и напоминания, строить маршруты, включать музыку, управлять режимами. Просто скажите: 'Джарвис, открой телеграм' или 'Джарвис, погода в Москве'.";
    }
}