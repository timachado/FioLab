package com.franluz.seller;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://franluzhomedecor.infinityfree.io/seller-franluz/";
    private static final String SITE_HOST = "franluzhomedecor.infinityfree.io";
    private static final int FILE_REQUEST = 7001;

    private WebView webView;
    private FrameLayout root;
    private ValueCallback<Uri[]> fileCallback;
    private Uri pendingCaptureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(87, 50, 37));
        getWindow().setNavigationBarColor(Color.rgb(250, 246, 240));
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(250, 246, 240));
        setContentView(root);
        createWebView();
        applySystemBarInsets();
        loadInitialUrl(getIntent());
    }

    private void createWebView() {
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " FranLuzSeller/1.0 Android");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost();

                if (("http".equals(scheme) || "https".equals(scheme)) && SITE_HOST.equalsIgnoreCase(host)) {
                    return false;
                }
                if ("tel".equals(scheme) || "mailto".equals(scheme) || "whatsapp".equals(scheme)) {
                    openExternal(uri);
                    return true;
                }
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    openExternal(uri);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                        "(function(){document.documentElement.classList.add('franluz-seller-native-app');})();",
                        null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pendingCaptureUri = null;

                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    Intent chooser = Intent.createChooser(picker, "Selecionar arquivo");

                    if (acceptsImage(params.getAcceptTypes())) {
                        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                        if (pictures != null) {
                            File photo = new File(pictures, "franluz_seller_" + System.currentTimeMillis() + ".jpg");
                            pendingCaptureUri = FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getPackageName() + ".fileprovider",
                                    photo);
                            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
                            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                        }
                    }

                    startActivityForResult(chooser, FILE_REQUEST);
                    return true;
                } catch (Exception ex) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    pendingCaptureUri = null;
                    return false;
                }
            }
        });
    }

    private boolean acceptsImage(String[] types) {
        if (types == null || types.length == 0) return true;
        for (String type : types) {
            if (type == null || type.isEmpty() || "*/*".equals(type) || type.startsWith("image/")) return true;
        }
        return false;
    }

    private void applySystemBarInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    private void loadInitialUrl(Intent intent) {
        if (!isOnline()) {
            showOffline();
            return;
        }
        Uri data = intent == null ? null : intent.getData();
        if (data != null && SITE_HOST.equalsIgnoreCase(data.getHost())) {
            webView.loadUrl(data.toString());
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return true;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline() {
        root.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(56, 56, 56, 56);
        TextView title = new TextView(this);
        title.setText("FranLuz Seller");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(87, 50, 37));
        title.setGravity(Gravity.CENTER);
        TextView message = new TextView(this);
        message.setText("Sem conexão com a internet. Verifique sua rede e tente novamente.");
        message.setTextSize(16);
        message.setTextColor(Color.rgb(92, 79, 72));
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 24, 0, 32);
        Button retry = new Button(this);
        retry.setText("Tentar novamente");
        retry.setOnClickListener(v -> recreate());
        panel.addView(title);
        panel.addView(message);
        panel.addView(retry);
        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri data = intent.getData();
        if (data != null && SITE_HOST.equalsIgnoreCase(data.getHost()) && webView != null) {
            webView.loadUrl(data.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                if ((result == null || result.length == 0) && pendingCaptureUri != null) {
                    result = new Uri[]{pendingCaptureUri};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            pendingCaptureUri = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
