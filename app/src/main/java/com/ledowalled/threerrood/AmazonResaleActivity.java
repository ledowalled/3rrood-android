package com.ledowalled.threerrood;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonResaleActivity extends Activity {
    private static final String SEARCH = "https://www.amazon.eg/s?i=warehouse-deals&srs=27972173031&s=popularity-rank&fs=true&ref=lp_27972173031_sar&page=";
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Set<String> seen = new HashSet<>();
    private LinearLayout list;
    private ProgressBar progress;
    private TextView status;
    private Button moreButton;
    private int page = 1;
    private boolean loading = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        loadPage(false);
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private void buildUi() {
        LinearLayout pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setBackgroundColor(Color.rgb(246, 246, 249));
        pageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(Color.rgb(112, 38, 185));
        TextView title = text("Amazon Resale مصر", 21, Color.WHITE, true);
        header.addView(title);
        TextView subtitle = text("منتجات بعروض مستعملة من Amazon.eg", 13, Color.WHITE, false);
        subtitle.setAlpha(.9f); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin=dp(5); header.addView(subtitle, sp);
        pageRoot.addView(header);

        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL); tools.setPadding(dp(12), dp(8), dp(12), dp(8));
        Button refresh = new Button(this); refresh.setText("تحديث"); refresh.setAllCaps(false); refresh.setOnClickListener(v -> { page=1; seen.clear(); list.removeAllViews(); loadPage(false); });
        tools.addView(refresh, new LinearLayout.LayoutParams(-2, dp(44)));
        status = text("يتم تحميل المنتجات...", 13, Color.DKGRAY, false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(0, -2, 1); stp.setMargins(dp(10),0,0,0); tools.addView(status, stp);
        pageRoot.addView(tools);
        progress = new ProgressBar(this); progress.setIndeterminate(true); pageRoot.addView(progress);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(10), dp(4), dp(10), dp(12));
        scroll.addView(list); pageRoot.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        moreButton = new Button(this); moreButton.setText("تحميل المزيد من المنتجات"); moreButton.setAllCaps(false); moreButton.setOnClickListener(v -> loadPage(true));
        pageRoot.addView(moreButton, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(pageRoot);
    }

    private void loadPage(boolean next) {
        if (loading) return;
        if (next) page++;
        loading = true; progress.setVisibility(View.VISIBLE); moreButton.setEnabled(false);
        status.setText("جاري الاتصال بـ Amazon.eg — الصفحة " + page);
        final int requestedPage = page;
        worker.execute(() -> {
            ArrayList<Product> products = new ArrayList<>();
            String error = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SEARCH + requestedPage);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(18000); connection.setReadTimeout(22000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");
                connection.setRequestProperty("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.8");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                int code = connection.getResponseCode();
                if (code != 200) throw new Exception("Amazon.eg returned HTTP " + code);
                String html = readAll(connection.getInputStream());
                String low = html.toLowerCase(Locale.ROOT);
                if (low.contains("captcha") || low.contains("validatecaptcha")) throw new Exception("Amazon.eg طلب التحقق من المتصفح. حاول لاحقًا من غير تكرار الطلبات.");
                Document doc = Jsoup.parse(html);
                Elements cards = doc.select("div[data-component-type=s-search-result][data-asin]");
                Pattern used = Pattern.compile("(\\d+)\\s*(?:العروض المستخدمة|عرض مستخدم)");
                Pattern price = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه|EGP)");
                for (Element card : cards) {
                    String asin = card.attr("data-asin").trim();
                    if (asin.isEmpty() || seen.contains(asin)) continue;
                    String cardText = card.text();
                    Matcher um = used.matcher(cardText);
                    if (!um.find()) continue; // Only show listings with an explicit used-offer marker.
                    Matcher pm = price.matcher(cardText);
                    String priceText = pm.find() ? pm.group(1) + " جنيه" : "السعر ظاهر في Amazon.eg";
                    Element h = card.selectFirst("h2 span");
                    String name = h == null ? "منتج Amazon" : h.text().trim();
                    Element img = card.selectFirst("img.s-image");
                    String image = img == null ? "" : img.attr("src");
                    String link = "https://www.amazon.eg/dp/" + asin;
                    products.add(new Product(asin, name, priceText, um.group(1), image, link));
                }
                if (cards.isEmpty()) throw new Exception("لم أتمكن من قراءة بطاقات المنتجات. قد تكون صفحة Amazon.eg تغيرت أو تطلب تحققًا.");
            } catch (Exception e) { error = e.getMessage() == null ? "تعذر تحميل المنتجات" : e.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            final String finalError = error;
            runOnUiThread(() -> {
                loading = false; progress.setVisibility(View.GONE); moreButton.setEnabled(true);
                if (finalError != null) {
                    status.setText(finalError);
                    Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
                    if (requestedPage > 1) page--;
                    return;
                }
                for (Product product : products) { if (seen.add(product.asin)) addProduct(product); }
                status.setText("تم عرض " + seen.size() + " منتجًا مؤكدًا بعروض مستعملة");
                if (products.isEmpty()) status.setText("لا توجد منتجات جديدة مؤكدة في هذه الصفحة");
            });
        });
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n=in.read(b))!=-1) out.write(b,0,n); in.close(); return out.toString("UTF-8");
    }

    private void addProduct(Product p) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10)); card.setBackground(bg(Color.WHITE, 12));
        card.setElevation(dp(2)); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin=dp(9); list.addView(card, cp);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setBackground(bg(Color.rgb(248,248,248),8));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(108), dp(108)); ip.setMargins(0,0,dp(10),0); card.addView(image, ip);
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); card.addView(info, new LinearLayout.LayoutParams(0,-2,1));
        TextView name = text(p.name, 14, Color.rgb(40,40,40), true); name.setMaxLines(3); info.addView(name);
        TextView price = text(p.price, 16, Color.rgb(112,38,185), true); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(7); info.addView(price,pp);
        TextView offers = text(p.offers + " عرض مستعمل", 12, Color.rgb(35,125,75), false); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2); op.topMargin=dp(4); info.addView(offers,op);
        Button open = new Button(this); open.setText("عرض المنتج على أمازون"); open.setAllCaps(false); open.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.url))); } catch(Exception e) { Toast.makeText(this,"تعذر فتح المنتج",Toast.LENGTH_SHORT).show(); } });
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(42)); bp.topMargin=dp(5); info.addView(open,bp);
        if (!p.image.isEmpty()) worker.execute(() -> {
            try { HttpURLConnection c=(HttpURLConnection)new URL(p.image).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setRequestProperty("User-Agent","Mozilla/5.0"); final android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(c.getInputStream()); c.disconnect();
                runOnUiThread(() -> image.setImageBitmap(bitmap));
            } catch(Exception ignored) { }
        });
    }

    @Override public void onDestroy() { super.onDestroy(); worker.shutdownNow(); }

    private static class Product {
        final String asin,name,price,offers,image,url;
        Product(String a,String n,String p,String o,String i,String u) { asin=a;name=n;price=p;offers=o;image=i;url=u; }
    }
}
