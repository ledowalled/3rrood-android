package com.ledowalled.threerrood;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonResaleActivity extends Activity {

    private static final String BASE =
            "https://www.amazon.eg/s?i=warehouse-deals&srs=27972173031&s=";

    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Set<String> seen = new HashSet<>();

    private GridLayout list;
    private ProgressBar progress;
    private TextView status;
    private Button moreButton;
    private Spinner sortSpinner;
    private Spinner countSpinner;

    private int page = 1;
    private int batchSize = 30;
    private String sortMode = "featured-rank";
    private boolean loading = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        buildUi();
        startPhoneMonitorWithPermission();
        loadPage(false);
    }

    private void startPhoneMonitorWithPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                910
            );
        } else {
            startPhoneMonitorService();
        }
    }

    private void startPhoneMonitorService() {
        try {
            Intent service = new Intent(
                this, AmazonPhoneMonitorService.class
            );

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(
                this,
                "تعذر تشغيل مراقبة الهواتف: " + e.getMessage(),
                android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        );

        if (requestCode == 910) {
            startPhoneMonitorService();

            if (grantResults.length == 0 ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(
                    this,
                    "فعّل إذن الإشعارات حتى تصلك تنبيهات الهواتف الجديدة.",
                    android.widget.Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Spinner makeSpinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        s.setAdapter(adapter);
        return s;
    }

    private void resetAndLoad() {
        if (loading) return;
        page = 1;
        seen.clear();
        list.removeAllViews();
        moreButton.setText("تحميل المزيد من المنتجات");
        moreButton.setEnabled(true);
        loadPage(false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 246, 249));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(Color.rgb(112, 38, 185));

        header.addView(text("Amazon Resale مصر", 21, Color.WHITE, true));

        TextView subtitle = text(
                "منتجات بعروض مستعملة من Amazon.eg",
                13, Color.WHITE, false
        );
        subtitle.setAlpha(.9f);
        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(5);
        header.addView(subtitle, subParams);
        root.addView(header);

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(8), dp(6), dp(8), dp(6));

        Button refresh = new Button(this);
        refresh.setText("تحديث");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> resetAndLoad());
        tools.addView(refresh, new LinearLayout.LayoutParams(-2, dp(44)));

        status = text("يتم تحميل المنتجات...", 12, Color.DKGRAY, false);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(0, -2, 1);
        statusParams.setMargins(dp(7), 0, dp(7), 0);
        tools.addView(status, statusParams);
        root.addView(tools);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(8), dp(2), dp(8), dp(8));

        TextView sortLabel = text("الترتيب:", 12, Color.DKGRAY, true);
        filters.addView(sortLabel);

        sortSpinner = makeSpinner(new String[]{
                "مميز",
                "الأحدث نزولًا",
                "السعر: الأقل للأعلى",
                "السعر: الأعلى للأقل"
        });
        sortSpinner.setSelection(0);
        filters.addView(sortSpinner,
                new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView countLabel = text("العدد:", 12, Color.DKGRAY, true);
        filters.addView(countLabel);

        countSpinner = makeSpinner(new String[]{"30 منتج", "50 منتج"});
        countSpinner.setSelection(0);
        filters.addView(countSpinner,
                new LinearLayout.LayoutParams(dp(112), dp(48)));

        root.addView(filters);

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                String newMode;
                if (position == 1) newMode = "date-desc-rank";
                else if (position == 2) newMode = "price-asc-rank";
                else if (position == 3) newMode = "price-desc-rank";
                else newMode = "featured-rank";

                if (!newMode.equals(sortMode)) {
                    sortMode = newMode;
                    resetAndLoad();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        countSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                int newSize = position == 1 ? 50 : 30;
                if (newSize != batchSize) {
                    batchSize = newSize;
                    resetAndLoad();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(progress);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        list = new GridLayout(this);
        list.setColumnCount(2);
        list.setUseDefaultMargins(false);
        list.setPadding(dp(5), dp(5), dp(5), dp(10));

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        moreButton = new Button(this);
        moreButton.setText("تحميل المزيد من المنتجات");
        moreButton.setAllCaps(false);
        moreButton.setOnClickListener(v -> loadPage(true));
        root.addView(moreButton, new LinearLayout.LayoutParams(-1, dp(52)));

        setContentView(root);
    }

    private void loadPage(boolean next) {
        if (loading) return;

        loading = true;
        progress.setVisibility(View.VISIBLE);
        moreButton.setEnabled(false);

        final int firstPage = page;
        final int targetCount = batchSize;
        final String selectedSort = sortMode;

        status.setText("جاري تحميل " + targetCount + " منتج من Amazon.eg...");

        worker.execute(() -> {
            ArrayList<Product> products = new ArrayList<>();
            String error = null;
            int currentPage = firstPage;
            boolean reachedEnd = false;
            int pagesRead = 0;

            Pattern used = Pattern.compile(
                    "(\\d+)\\s*(?:العروض المستخدمة|عرض مستخدم)"
            );
            Pattern pricePattern = Pattern.compile(
                    "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه|EGP)"
            );

            while (products.size() < targetCount && pagesRead < 12) {
                HttpURLConnection connection = null;

                try {
                    String address = BASE + selectedSort
                            + "&fs=true&ref=lp_27972173031_sar&page="
                            + currentPage;

                    connection = (HttpURLConnection)
                            new URL(address).openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(18000);
                    connection.setReadTimeout(22000);
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
                        throw new Exception("Amazon.eg returned HTTP " + code);
                    }

                    String html = readAll(connection.getInputStream());
                    String low = html.toLowerCase(Locale.ROOT);

                    if (low.contains("captcha") ||
                            low.contains("validatecaptcha")) {
                        throw new Exception(
                                "Amazon.eg طلب التحقق من المتصفح. حاول لاحقًا."
                        );
                    }

                    Document doc = Jsoup.parse(html);
                    Elements cards = doc.select(
                            "div[data-component-type=s-search-result][data-asin]"
                    );

                    if (cards.isEmpty()) {
                        Elements components =
                                doc.getElementsByAttribute("data-component-type");
                        Elements asins =
                                doc.getElementsByAttribute("data-asin");

                        throw new Exception(
                                "لم يتم العثور على بطاقات المنتجات في Amazon.eg"
                                + " | HTTP=" + code
                                + " | components=" + components.size()
                                + " | ASIN=" + asins.size()
                                + " | Title=" + doc.title()
                        );
                    }

                    int addedThisPage = 0;

                    for (Element card : cards) {
                        if (products.size() >= targetCount) break;

                        String asin = card.attr("data-asin").trim();

                        if (asin.isEmpty() || seen.contains(asin)) continue;

                        String cardText = card.text();
                        Matcher usedMatcher = used.matcher(cardText);

                        if (!usedMatcher.find()) continue;

                        Matcher priceMatcher =
                                pricePattern.matcher(cardText);

                        String priceText = "السعر ظاهر في Amazon.eg";
                        double numericPrice = Double.MAX_VALUE;

                        if (priceMatcher.find()) {
                            String raw = priceMatcher.group(1);
                            priceText = raw + " جنيه";
                            try {
                                numericPrice = Double.parseDouble(
                                        raw.replace(",", "")
                                );
                            } catch (Exception ignored) { }
                        }

                        Element heading = card.selectFirst("h2 span");
                        String name = heading == null
                                ? "منتج Amazon"
                                : heading.text().trim();

                        Element imageElement =
                                card.selectFirst("img.s-image");

                        String image = imageElement == null
                                ? ""
                                : imageElement.attr("src");

                        String link = "https://www.amazon.eg/dp/" + asin;

                        seen.add(asin);
                        products.add(new Product(
                                asin, name, priceText,
                                usedMatcher.group(1),
                                image, link, numericPrice
                        ));

                        addedThisPage++;
                    }

                    currentPage++;
                    pagesRead++;

                    if (cards.isEmpty() || addedThisPage == 0 &&
                            cards.size() < 14) {
                        reachedEnd = true;
                        break;
                    }

                } catch (Exception e) {
                    error = e.getMessage() == null
                            ? "تعذر تحميل المنتجات"
                            : e.getMessage();
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }

            if ("price-asc-rank".equals(selectedSort)) {
                Collections.sort(products,
                        (a, b) -> Double.compare(a.numericPrice, b.numericPrice));
            } else if ("price-desc-rank".equals(selectedSort)) {
                Collections.sort(products,
                        (a, b) -> Double.compare(b.numericPrice, a.numericPrice));
            }

            final BatchResult result = new BatchResult(
                    products, error, currentPage, reachedEnd
            );

            runOnUiThread(() -> {
                loading = false;
                progress.setVisibility(View.GONE);
                page = result.nextPage;

                for (Product product : result.products) {
                    addProduct(product);
                }

                if (result.error != null) {
                    status.setText("تم تحميل " + seen.size()
                            + " منتج. " + result.error);
                    Toast.makeText(this, result.error,
                            Toast.LENGTH_LONG).show();
                    moreButton.setEnabled(true);
                } else {
                    status.setText("إجمالي المنتجات المعروضة: "
                            + seen.size());

                    if (result.end) {
                        moreButton.setText("وصلت لنهاية النتائج");
                        moreButton.setEnabled(false);
                    } else {
                        moreButton.setText("تحميل المزيد من المنتجات");
                        moreButton.setEnabled(true);
                    }

                    if (result.products.isEmpty()) {
                        status.setText("لم يتم العثور على منتجات جديدة مؤكدة");
                    }
                }
            });
        });
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

    private void addProduct(Product p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(bg(Color.WHITE, 12));
        card.setElevation(dp(2));

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.height = dp(226);
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        list.addView(card, cardParams);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(bg(Color.rgb(248, 248, 248), 8));

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(-1, dp(105));
        card.addView(image, imageParams);

        TextView name = text(
                p.name, 12, Color.rgb(40, 40, 40), true
        );
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(-1, 0, 1f);
        nameParams.topMargin = dp(4);
        card.addView(name, nameParams);

        TextView price = text(
                p.price, 14, Color.rgb(112, 38, 185), true
        );
        price.setGravity(Gravity.CENTER);
        card.addView(price, new LinearLayout.LayoutParams(-1, -2));

        TextView offers = text(
                p.offers + " عرض مستعمل",
                10, Color.rgb(35, 125, 75), false
        );
        offers.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams offersParams =
                new LinearLayout.LayoutParams(-1, -2);
        offersParams.topMargin = dp(2);
        card.addView(offers, offersParams);

        Button open = new Button(this);
        open.setText("عرض المنتج");
        open.setTextSize(11);
        open.setAllCaps(false);
        open.setMinHeight(0);
        open.setMinimumHeight(0);
        open.setPadding(dp(3), 0, dp(3), 0);
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(
                        Intent.ACTION_VIEW, Uri.parse(p.url)
                ));
            } catch (Exception e) {
                Toast.makeText(this, "تعذر فتح المنتج",
                        Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(36));
        buttonParams.topMargin = dp(3);
        card.addView(open, buttonParams);

        if (!p.image.isEmpty()) {
            worker.execute(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection)
                            new URL(p.image).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0");

                    final android.graphics.Bitmap bitmap =
                            android.graphics.BitmapFactory.decodeStream(
                                    c.getInputStream()
                            );

                    if (bitmap != null) {
                        runOnUiThread(() -> image.setImageBitmap(bitmap));
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    private static class Product {
        final String asin, name, price, offers, image, url;
        final double numericPrice;

        Product(String a, String n, String p, String o,
                String i, String u, double value) {
            asin = a;
            name = n;
            price = p;
            offers = o;
            image = i;
            url = u;
            numericPrice = value;
        }
    }

    private static class BatchResult {
        final ArrayList<Product> products;
        final String error;
        final int nextPage;
        final boolean end;

        BatchResult(ArrayList<Product> p, String e, int n, boolean finished) {
            products = p;
            error = e;
            nextPage = n;
            end = finished;
        }
    }
}
