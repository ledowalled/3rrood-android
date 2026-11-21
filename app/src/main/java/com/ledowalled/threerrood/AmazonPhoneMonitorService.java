package com.ledowalled.threerrood;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

import java.util.regex.*;

public class AmazonPhoneMonitorService extends Service {

    private static final String TAG = "AmazonPhoneMonitor";
    private static final String CHANNEL = "amazon_monitor";
    private static final String ALERT_CHANNEL = "amazon_phone_alerts";
    private static final int SERVICE_ID = 1981;

    private static final String SEARCH =
        "https://www.amazon.eg/s?i=warehouse-deals&srs=27972173031&s=featured-rank&fs=true&ref=lp_27972173031_sar&page=1";

    private ScheduledExecutorService scheduler;
    private PowerManager.WakeLock wakeLock;

    private final Pattern pricePattern = Pattern.compile(
        "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه|EGP)"
    );

    private final Pattern phonePattern = Pattern.compile(
        "(iphone|آيفون|ايفون|smartphone|mobile phone|هاتف|موبايل|جوال|"
        + "samsung galaxy|galaxy [a-z0-9]|redmi|xiaomi|poco|"
        + "oppo|realme|vivo|oneplus|huawei|honor|motorola|"
        + "google pixel|nokia|tecno|infinix)",
        Pattern.CASE_INSENSITIVE
    );

    private final Pattern accessoryPattern = Pattern.compile(
        "(جراب|كفر|غطاء|حافظة|واقي|حامي|حامل|ستاند|شاحن|"
        + "كابل|سلك|سماعة|عدسة|لاصق|screen protector|"
        + "phone case|case for|cover for|holder|mount|"
        + "stand for|charger|charging cable|usb cable|"
        + "headphone|earphone|earbuds|adapter|replacement battery)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onCreate() {
        super.onCreate();

        createChannels();

        getSharedPreferences("phone_monitor", MODE_PRIVATE)
            .edit().putBoolean("enabled", true).apply();

        Notification notification = buildServiceNotification();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(SERVICE_ID, notification);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(
            this::checkAmazon,
            0,
            30,
            TimeUnit.SECONDS
        );

        Log.i(TAG, "Phone monitor started.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null &&
            "STOP_MONITOR".equals(intent.getAction())) {

            getSharedPreferences("phone_monitor", MODE_PRIVATE)
                .edit().putBoolean("enabled", false).apply();

            if (scheduler != null) scheduler.shutdownNow();

            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL,
                "مراقبة Amazon Resale",
                NotificationManager.IMPORTANCE_LOW
            );

            serviceChannel.setDescription(
                "حالة مراقبة الهواتف المستعملة"
            );
            serviceChannel.setShowBadge(false);

            NotificationChannel alertChannel = new NotificationChannel(
                ALERT_CHANNEL,
                "هواتف مستعملة جديدة",
                NotificationManager.IMPORTANCE_HIGH
            );

            alertChannel.setDescription(
                "تنبيه عند اكتشاف هاتف مستعمل جديد على Amazon.eg"
            );
            alertChannel.enableVibration(true);

            NotificationManager nm =
                getSystemService(NotificationManager.class);

            nm.createNotificationChannel(serviceChannel);
            nm.createNotificationChannel(alertChannel);
        }
    }

    private PendingIntent stopPendingIntent() {
        Intent stop = new Intent(this, AmazonPhoneMonitorService.class);
        stop.setAction("STOP_MONITOR");

        return PendingIntent.getService(
            this, 1982, stop,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Notification buildServiceNotification() {
        Notification.Builder b;

        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_LOW);
        }

        Intent launch = getPackageManager()
            .getLaunchIntentForPackage(getPackageName());

        PendingIntent open = null;

        if (launch != null) {
            open = PendingIntent.getActivity(
                this, 1983, launch,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
            );
        }

        b.setSmallIcon(android.R.drawable.stat_notify_sync)
         .setColor(Color.rgb(112, 38, 185))
         .setContentTitle("3rrood: مراقبة الهواتف نشطة")
         .setContentText("فحص Amazon Resale مصر كل 30 ثانية")
         .setOngoing(true)
         .setCategory(Notification.CATEGORY_SERVICE)
         .addAction(
             android.R.drawable.ic_menu_close_clear_cancel,
             "إيقاف المراقبة",
             stopPendingIntent()
         );

        if (open != null) b.setContentIntent(open);

        return b.build();
    }

    private void checkAmazon() {
        HttpURLConnection connection = null;

        try {
            PowerManager pm =
                (PowerManager) getSystemService(POWER_SERVICE);

            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "3rrood:AmazonPhoneCheck"
            );

            wakeLock.acquire(20000);

            connection = (HttpURLConnection)
                new URL(SEARCH).openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
            );

            connection.setRequestProperty(
                "Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8"
            );

            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            );

            connection.setRequestProperty("Accept-Encoding", "identity");

            int code = connection.getResponseCode();

            if (code != 200) {
                Log.w(TAG, "Amazon HTTP " + code);
                return;
            }

            String html = readAll(connection.getInputStream());
            String lower = html.toLowerCase(Locale.ROOT);

            if (lower.contains("captcha") ||
                lower.contains("validatecaptcha")) {
                Log.w(TAG, "Amazon requested verification.");
                return;
            }

            Document doc = Jsoup.parse(html);

            Elements cards = doc.select(
                "div[data-component-type=s-search-result][data-asin]"
            );

            if (cards.isEmpty()) {
                Log.w(TAG, "No Amazon product cards found.");
                return;
            }

            SharedPreferences prefs =
                getSharedPreferences("phone_monitor", MODE_PRIVATE);

            boolean initialized =
                prefs.getBoolean("initialized", false);

            Set<String> oldSet = new HashSet<>(
                prefs.getStringSet(
                    "phone_asins",
                    new HashSet<>()
                )
            );

            Set<String> currentSet = new HashSet<>(oldSet);

            int found = 0;

            for (Element card : cards) {

                String asin = card.attr("data-asin").trim();

                if (asin.isEmpty()) continue;

                Element heading = card.selectFirst("h2 span");

                if (heading == null) continue;

                String name = heading.text().trim();

                if (name.isEmpty()) continue;

                if (accessoryPattern.matcher(name).find()) continue;

                if (!phonePattern.matcher(name).find()) continue;

                currentSet.add(asin);
                found++;

                if (initialized && !oldSet.contains(asin)) {

                    Matcher priceMatcher = pricePattern.matcher(card.text());

                    String price = priceMatcher.find()
                        ? priceMatcher.group(1) + " جنيه"
                        : "السعر ظاهر على Amazon.eg";

                    String link =
                        "https://www.amazon.eg/dp/" + asin;

                    sendPhoneNotification(
                        asin, name, price, link
                    );

                    Log.i(TAG, "New phone detected: " + name);
                }
            }

            prefs.edit()
                .putStringSet("phone_asins", currentSet)
                .putBoolean("initialized", true)
                .apply();

            Log.i(TAG, "Check complete. Phones found: " + found);

        } catch (Exception e) {
            Log.e(TAG, "Amazon check failed: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();

            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void sendPhoneNotification(
        String asin, String name, String price, String link
    ) {
        try {
            Intent open = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(link)
            );

            PendingIntent click = PendingIntent.getActivity(
                this,
                asin.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder b;

            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(this, ALERT_CHANNEL);
            } else {
                b = new Notification.Builder(this);
                b.setPriority(Notification.PRIORITY_HIGH);
            }

            b.setSmallIcon(android.R.drawable.stat_notify_more)
             .setColor(Color.rgb(112, 38, 185))
             .setContentTitle("موبايل مستعمل جديد على Amazon.eg")
             .setContentText(name + " | " + price)
             .setStyle(new Notification.BigTextStyle()
                .bigText(name + "\nالسعر: " + price))
             .setAutoCancel(true)
             .setContentIntent(click);

            NotificationManager nm =
                (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);

            int notificationId =
                3000 + (asin.hashCode() & 0x0fffffff);

            nm.notify(notificationId, b.build());

        } catch (Exception e) {
            Log.e(TAG, "Notification error", e);
        }
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;

        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }

        in.close();
        return out.toString("UTF-8");
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
