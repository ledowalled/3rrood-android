package com.ledowalled.threerrood;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    private static final String HOME = "https://deals.3rrood.com/";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private SwipeRefreshLayout swipeRefreshLayout;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private FrameLayout root;
    private View splashScreen;
    private View offlineScreen;
    private boolean firstPageFinished = false;
    private Button resaleButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        root = new FrameLayout(this);

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(
                Color.rgb(112, 38, 185),
                Color.rgb(244, 123, 32)
        );

        webView = new WebView(this);
        swipeRefreshLayout.addView(
                webView, new FrameLayout.LayoutParams(-1, -1)
        );
        swipeRefreshLayout.setOnChildScrollUpCallback(
                (parent, child) -> webView.getScrollY() > 0
        );
        root.addView(
                swipeRefreshLayout, new FrameLayout.LayoutParams(-1, -1)
        );

        progressBar = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setMax(100);
        progressBar.setIndeterminate(false);

        FrameLayout.LayoutParams barParams =
                new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        root.addView(progressBar, barParams);

        splashScreen = createSplashScreen();
        root.addView(splashScreen, new FrameLayout.LayoutParams(-1, -1));

        offlineScreen = createOfflineScreen();
        offlineScreen.setVisibility(View.GONE);
        root.addView(offlineScreen, new FrameLayout.LayoutParams(-1, -1));

        resaleButton = new Button(this);
        resaleButton.setText("Amazon Resale مصر");
        resaleButton.setTextColor(Color.WHITE);
        resaleButton.setTextSize(13);
        resaleButton.setAllCaps(false);
        resaleButton.setPadding(dp(14), 0, dp(14), 0);
        resaleButton.setBackground(makeRoundedBackground(Color.rgb(112, 38, 185), dp(24)));
        resaleButton.setElevation(dp(8));
        resaleButton.setVisibility(View.GONE);
        resaleButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AmazonResaleActivity.class))
        );
        FrameLayout.LayoutParams resaleParams = new FrameLayout.LayoutParams(
                -2, dp(48), Gravity.BOTTOM | Gravity.END
        );
        resaleParams.setMargins(dp(12), dp(12), dp(16), dp(22));
        root.addView(resaleButton, resaleParams);

        setContentView(root);
        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request
            ) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, String url
            ) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false);
                CookieManager.getInstance().flush();

                if (!firstPageFinished) {
                    firstPageFinished = true;
                    splashScreen.setVisibility(View.GONE);
                }

                if (resaleButton != null) resaleButton.setVisibility(View.VISIBLE);
                offlineScreen.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    swipeRefreshLayout.setRefreshing(false);
                    showOfflineScreen();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg
            ) {
                final WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);

                popup.setWebViewClient(new WebViewClient() {
                    private boolean routePopup(String url) {
                        Uri uri = Uri.parse(url);
                        String host = uri.getHost() == null
                                ? "" : uri.getHost().toLowerCase();

                        if (host.equals("3rrood.com")
                                || host.endsWith(".3rrood.com")) {
                            webView.loadUrl(url);
                        } else {
                            handleUrl(url);
                        }

                        root.post(() -> {
                            root.removeView(popup);
                            popup.stopLoading();
                            popup.destroy();
                        });

                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView v, WebResourceRequest r
                    ) {
                        return routePopup(r.getUrl().toString());
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView v, String url
                    ) {
                        return routePopup(url);
                    }
                });

                root.addView(
                        popup,
                        new FrameLayout.LayoutParams(1, 1)
                );

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);

                if (progress < 100 && offlineScreen.getVisibility() != View.VISIBLE) {
                    progressBar.setVisibility(View.VISIBLE);
                } else if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                Intent intent;
                try {
                    intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "لا يوجد تطبيق لاختيار الملفات",
                            Toast.LENGTH_SHORT
                    ).show();
                    return false;
                }

                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback
            ) {
                callback.invoke(origin, false, false);
            }

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    JsResult result
            ) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(
                                "موافق",
                                (d, w) -> result.confirm()
                        )
                        .setOnCancelListener(d -> result.cancel())
                        .show();

                return true;
            }
        });

        webView.setDownloadListener(
                (url, userAgent, contentDisposition, mimeType, contentLength) -> {
                    try {
                        DownloadManager.Request request =
                                new DownloadManager.Request(Uri.parse(url));

                        String cookie =
                                CookieManager.getInstance().getCookie(url);

                        if (cookie != null) {
                            request.addRequestHeader("Cookie", cookie);
                        }

                        if (userAgent != null) {
                            request.addRequestHeader("User-Agent", userAgent);
                        }

                        request.setTitle("عروض مطرقعة - تحميل ملف");
                        request.setDescription("جارٍ تنزيل الملف");
                        request.setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        );
                        request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                "3rrood_" + System.currentTimeMillis()
                        );

                        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE))
                                .enqueue(request);

                        Toast.makeText(
                                this, "بدأ التحميل", Toast.LENGTH_SHORT
                        ).show();

                    } catch (Exception e) {
                        Toast.makeText(
                                this, "تعذر بدء التحميل", Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME);
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    2001
            );
        }
    }

    private View createSplashScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(112, 38, 185));
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(dp(125), dp(125));
        logoParams.bottomMargin = dp(22);
        layout.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("عروض مطرقعة");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("أفضل العروض في مكان واحد");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setAlpha(0.85f);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(10);
        layout.addView(subtitle, subParams);

        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);

        LinearLayout.LayoutParams loadingParams =
                new LinearLayout.LayoutParams(dp(36), dp(36));
        loadingParams.topMargin = dp(35);
        layout.addView(loading, loadingParams);

        return layout;
    }

    private View createOfflineScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.WHITE);
        layout.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView icon = new TextView(this);
        icon.setText("⚠");
        icon.setTextSize(58);
        icon.setTextColor(Color.rgb(244, 123, 32));
        icon.setGravity(Gravity.CENTER);
        layout.addView(icon);

        TextView title = new TextView(this);
        title.setText("لا يوجد اتصال بالإنترنت");
        title.setTextColor(Color.rgb(55, 55, 55));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(15);
        layout.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText("تأكد من اتصال هاتفك بالإنترنت، ثم حاول مرة أخرى.");
        message.setTextColor(Color.GRAY);
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(12);
        layout.addView(message, messageParams);

        Button retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(16);
        retry.setAllCaps(false);
        retry.setBackground(makeRoundedBackground(
                Color.rgb(112, 38, 185), dp(12)
        ));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(28);
        layout.addView(retry, buttonParams);

        retry.setOnClickListener(v -> {
            offlineScreen.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);

            String currentUrl = webView.getUrl();
            webView.loadUrl(
                    currentUrl == null || currentUrl.isEmpty()
                            ? HOME : currentUrl
            );
        });

        return layout;
    }

    private GradientDrawable makeRoundedBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void showOfflineScreen() {
        progressBar.setVisibility(View.GONE);
        if (resaleButton != null) resaleButton.setVisibility(View.GONE);

        if (!firstPageFinished) {
            splashScreen.setVisibility(View.GONE);
        }

        offlineScreen.setVisibility(View.VISIBLE);
    }

    private boolean handleUrl(String raw) {
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme() == null
                ? "" : uri.getScheme();
        String host = uri.getHost() == null
                ? "" : uri.getHost().toLowerCase();

        if (scheme.equals("http") || scheme.equals("https")) {
            if (host.equals("3rrood.com")
                    || host.endsWith(".3rrood.com")) {
                return false;
            }

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }

            return true;
        }

        if (scheme.equals("tel") || scheme.equals("mailto")
                || scheme.equals("sms") || scheme.equals("intent")) {
            try {
                startActivity(
                        Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
                );
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
            }

            return true;
        }

        return true;
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result =
                    WebChromeClient.FileChooserParams.parseResult(
                            resultCode, data
                    );

            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }
}
