package com.franluz.seller;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7010;
    private static final String HOST = "franluzhomedecor.infinityfree.io";
    private static final String OFFLINE = "file:///android_asset/offline.html";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private String sellerUrl;
    private boolean redirectingToSeller = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sellerUrl = getString(R.string.seller_url);
        createWebView();
        registerBackHandling();
        webView.loadUrl(sellerUrl);
    }

    private void createWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 248, 239));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(255, 248, 239));
        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        applySystemBars(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
            } else {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(
            settings.getUserAgentString() + " FranLuzSellerLocked/2.2.1"
        );

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
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

                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(
                        Intent.createChooser(intent, "Selecionar arquivo"),
                        FILE_CHOOSER_REQUEST
                    );
                    return true;
                } catch (ActivityNotFoundException error) {
                    fileCallback = null;
                    Toast.makeText(
                        MainActivity.this,
                        "Não foi possível abrir o seletor de arquivos.",
                        Toast.LENGTH_SHORT
                    ).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl(), request.isForMainFrame());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url), true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();

                if (url.startsWith(OFFLINE)) {
                    return;
                }

                Uri uri = Uri.parse(url);
                if (!isTrusted(uri)) {
                    return;
                }

                injectLockedChrome(view);

                String path = safePath(uri);
                if (isAuthPath(path)) {
                    detectLoginAndReturnToSeller(view);
                } else if (!isSellerPath(path)) {
                    returnToSeller();
                } else {
                    redirectingToSeller = false;
                }
            }

            @Override
            public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
            ) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    view.loadUrl(OFFLINE);
                }
            }

            @Override
            public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                SslError error
            ) {
                handler.cancel();
                view.loadUrl(OFFLINE);
            }
        });
    }

    private void applySystemBars(FrameLayout root) {
        getWindow().setStatusBarColor(Color.rgb(255, 248, 239));
        getWindow().setNavigationBarColor(Color.rgb(255, 248, 239));

        root.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = root.getWindowInsetsController();
                if (controller != null) {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            }
        });
    }

    private boolean handleNavigation(Uri uri, boolean mainFrame) {
        if (!mainFrame) {
            return false;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (!isTrusted(uri)) {
                returnToSeller();
                return true;
            }

            String path = safePath(uri);
            if (isSellerPath(path) || isAuthPath(path)) {
                return false;
            }

            returnToSeller();
            return true;
        }

        if ("mailto".equals(scheme) || "tel".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
            return true;
        }

        return true;
    }

    private boolean isTrusted(Uri uri) {
        String host = uri.getHost();
        return host != null && HOST.equalsIgnoreCase(host);
    }

    private String safePath(Uri uri) {
        String path = uri.getPath();
        return path == null ? "/" : path;
    }

    private boolean isSellerPath(String path) {
        return path.equals("/seller-franluz")
            || path.startsWith("/seller-franluz/");
    }

    private boolean isAuthPath(String path) {
        return path.equals("/minha-conta")
            || path.startsWith("/minha-conta/")
            || path.equals("/wp-login.php");
    }

    private void returnToSeller() {
        if (redirectingToSeller || webView == null) {
            return;
        }
        redirectingToSeller = true;
        webView.post(() -> webView.loadUrl(sellerUrl));
    }

    private void detectLoginAndReturnToSeller(WebView view) {
        String js =
            "(function(){"
                + "var body=document.body;"
                + "var logged=!!(body&&body.classList&&body.classList.contains('logged-in'));"
                + "var logout=!!document.querySelector('a[href*=customer-logout],a[href*=logout]');"
                + "return logged||logout;"
                + "})();";

        view.evaluateJavascript(js, result -> {
            if ("true".equals(result)) {
                returnToSeller();
            }
        });
    }

    private void injectLockedChrome(WebView view) {
        String css =
            "#wpadminbar,#masthead,#colophon,.site-header,.site-footer,"
                + ".storefront-primary-navigation,.main-navigation,.handheld-navigation,"
                + ".site-search,.woocommerce-store-notice,.whatsapp-float,.floating-whatsapp,"
                + ".franluz-bottom-nav,.flc-bottom-nav,.mobile-bottom-nav"
                + "{display:none!important}"
                + "html{margin-top:0!important;background:#fff8ef!important}"
                + "body{margin-top:0!important;background:#fff8ef!important}";

        String js =
            "(function(){"
                + "var id='franluzSellerLockedStyle';"
                + "var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;document.head.appendChild(s);}"
                + "s.textContent=" + org.json.JSONObject.quote(css) + ";"
                + "document.querySelectorAll('a[href]').forEach(function(a){"
                + "try{var u=new URL(a.href,location.href);"
                + "if(u.origin===location.origin){"
                + "var p=u.pathname;"
                + "var ok=(p==='/seller-franluz'||p.indexOf('/seller-franluz/')===0||p==='/minha-conta'||p.indexOf('/minha-conta/')===0||p==='/wp-login.php');"
                + "if(!ok){a.setAttribute('href'," + org.json.JSONObject.quote(sellerUrl) + ");}"
                + "}"
                + "}catch(e){}"
                + "});"
                + "})();";

        view.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        if (fileCallback != null) {
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private void registerBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::handleBack
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleBack();
        }
    }

    private void handleBack() {
        if (webView == null) {
            finish();
            return;
        }

        String path = safePath(Uri.parse(webView.getUrl() == null ? sellerUrl : webView.getUrl()));
        if (isAuthPath(path) && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        if (isSellerPath(path)) {
            finish();
            return;
        }

        returnToSeller();
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
