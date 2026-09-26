package com.franluz.seller;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7010;
    private static final String TRUSTED_HOST = "franluzhomedecor.infinityfree.io";
    private static final String BASE_URL = "https://" + TRUSTED_HOST + "/";
    private static final String LOCAL_SHELL = "file:///android_asset/seller_shell.html";
    private static final String OFFLINE_PAGE = "file:///android_asset/offline.html";

    private static final int MODE_LOCAL = 0;
    private static final int MODE_CHECK_SESSION = 1;
    private static final int MODE_LOGIN = 2;
    private static final int MODE_REMOTE = 3;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri pendingCaptureUri;
    private String sellerUrl;
    private String pendingError = "";
    private String pendingSection = "";
    private int mode = MODE_LOCAL;
    private boolean bridgeAttached = false;
    private boolean showingOffline = false;
    private String pendingLoginUser = "";
    private String pendingLoginPassword = "";
    private boolean pendingLoginRemember = true;
    private boolean loginFormInjected = false;
    private boolean verifyingAfterLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sellerUrl = getString(R.string.seller_url);
        configureSystemBars();
        createWebView();
        registerBackHandling();
        startSessionFlow();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.rgb(255, 248, 239));
        getWindow().setNavigationBarColor(Color.rgb(255, 255, 255));
    }

    private void applySystemBarAppearance(FrameLayout root) {
        root.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController c = root.getWindowInsetsController();
                if (c != null) {
                    c.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                root.setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                root.setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        });
    }

    private void createWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(255, 248, 239));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(255, 248, 239));
        root.addView(
            webView,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );
        setContentView(root);
        applySystemBarAppearance(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                    insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
            } else {
                v.setPadding(
                    0,
                    insets.getSystemWindowInsetTop(),
                    0,
                    insets.getSystemWindowInsetBottom()
                );
            }
            return insets;
        });
        root.requestApplyInsets();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(
            s.getUserAgentString() + " FranLuzSeller/1.1.4 SellerOnly"
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
                pendingCaptureUri = null;

                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);

                    Intent chooser = Intent.createChooser(
                        picker,
                        "Selecionar foto ou arquivo"
                    );

                    if (acceptsImages(params.getAcceptTypes())
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent camera = buildCameraIntent();
                        if (camera != null) {
                            chooser.putExtra(
                                Intent.EXTRA_INITIAL_INTENTS,
                                new Intent[]{camera}
                            );
                        }
                    }

                    startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback = null;
                    pendingCaptureUri = null;
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
            public boolean shouldOverrideUrlLoading(
                WebView view,
                WebResourceRequest request
            ) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();

                showingOffline = url.startsWith(OFFLINE_PAGE);

                if (url.startsWith(LOCAL_SHELL)) {
                    mode = MODE_LOCAL;
                    attachBridge();
                    if (!pendingError.isEmpty()) {
                        String js =
                            "window.FranLuzShell&&window.FranLuzShell.showError("
                                + JSONObject.quote(pendingError)
                                + ");";
                        pendingError = "";
                        view.evaluateJavascript(js, null);
                    }
                    return;
                }

                if (showingOffline) {
                    return;
                }

                Uri uri = Uri.parse(url);

                if (mode == MODE_LOGIN) {
                    handleRealLoginPage(view, uri);
                    return;
                }

                if (mode == MODE_CHECK_SESSION) {
                    if (isSellerPath(uri)) {
                        verifyingAfterLogin = false;
                        pendingError = "";
                        showLocal("home");
                        return;
                    }

                    if (isLoginOrCustomerPath(uri)) {
                        pendingError = verifyingAfterLogin
                            ? "Login realizado, mas esta conta não recebeu acesso ao Seller Center. Verifique a permissão de vendedor/administrador no WordPress."
                            : "";
                        verifyingAfterLogin = false;
                        showLocal("login");
                        return;
                    }
                }

                if (mode == MODE_REMOTE && isTrustedHost(uri)) {
                    if (isBlockedCustomerPath(uri)) {
                        showLocal("home");
                        return;
                    }

                    injectSellerChrome();

                    if (!pendingSection.isEmpty() && isSellerPath(uri)) {
                        String section = pendingSection;
                        pendingSection = "";
                        routeSellerSection(section);
                    }
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
                    showOffline();
                }
            }

            @Override
            public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                SslError error
            ) {
                handler.cancel();
                showOffline();
            }
        });
    }

    private void startSessionFlow() {
        if (hasLoginCookie()) {
            verifySellerSession();
        } else {
            showLocal("login");
        }
    }

    private boolean hasLoginCookie() {
        String cookies = CookieManager.getInstance().getCookie(BASE_URL);
        return cookies != null && cookies.contains("wordpress_logged_in_");
    }

    private void verifySellerSession() {
        mode = MODE_CHECK_SESSION;
        verifyingAfterLogin = false;
        detachBridge();
        webView.setVisibility(android.view.View.INVISIBLE);
        webView.loadUrl(sellerUrl);
    }

    private void showLocal(String screen) {
        mode = MODE_LOCAL;
        pendingSection = "";
        webView.setVisibility(android.view.View.VISIBLE);
        attachBridge();
        webView.loadUrl(LOCAL_SHELL + "#" + screen);
    }

    private void submitLogin(
        String username,
        String password,
        boolean remember
    ) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            pendingError = "Preencha o usuário/e-mail e a senha.";
            showLocal("login");
            return;
        }

        pendingLoginUser = username.trim();
        pendingLoginPassword = password;
        pendingLoginRemember = remember;
        loginFormInjected = false;
        verifyingAfterLogin = false;

        mode = MODE_LOGIN;
        detachBridge();
        webView.setVisibility(android.view.View.INVISIBLE);

        CookieManager.getInstance().flush();
        webView.loadUrl(BASE_URL + "minha-conta/?franluz_seller_login=1");
    }

    private void handleRealLoginPage(WebView view, Uri uri) {
        if (!isTrustedHost(uri)) {
            pendingError = "Não foi possível abrir a autenticação segura da FranLuz.";
            clearPendingLogin();
            showLocal("login");
            return;
        }

        String sessionCheck =
            "(function(){"
                + "var logged=document.body&&document.body.classList.contains('logged-in');"
                + "var admin=!!document.getElementById('wpadminbar');"
                + "return logged||admin;"
                + "})();";

        view.evaluateJavascript(sessionCheck, value -> {
            if ("true".equals(value)) {
                completeRealLogin();
                return;
            }

            if (!isLoginOrCustomerPath(uri)) {
                pendingError = "O login não retornou para a área segura da FranLuz.";
                clearPendingLogin();
                showLocal("login");
                return;
            }

            if (loginFormInjected) {
                pendingError = "O site recusou o login. Confira o usuário/e-mail e a senha exatamente como no acesso pelo navegador.";
                clearPendingLogin();
                showLocal("login");
                return;
            }

            injectRealWooCommerceLogin(view);
        });
    }

    private void injectRealWooCommerceLogin(WebView view) {
        loginFormInjected = true;

        String js =
            "(function(){"
                + "var f=document.querySelector('form.woocommerce-form-login,form.login,form[action*=minha-conta]');"
                + "if(!f)return 'NO_FORM';"
                + "var u=f.querySelector('input[name=username],input[name=log],input[type=email]');"
                + "var p=f.querySelector('input[name=password],input[name=pwd]');"
                + "if(!u||!p)return 'NO_FIELDS';"
                + "u.value=" + JSONObject.quote(pendingLoginUser) + ";"
                + "p.value=" + JSONObject.quote(pendingLoginPassword) + ";"
                + "u.dispatchEvent(new Event('input',{bubbles:true}));"
                + "p.dispatchEvent(new Event('input',{bubbles:true}));"
                + "var r=f.querySelector('input[name=rememberme],input[type=checkbox][name*=remember]');"
                + "if(r)r.checked=" + (pendingLoginRemember ? "true" : "false") + ";"
                + "var b=f.querySelector('button[name=login],button[type=submit],input[type=submit]');"
                + "if(f.requestSubmit){if(b)f.requestSubmit(b);else f.requestSubmit();}"
                + "else if(b&&b.click){b.click();}else{f.submit();}"
                + "return 'SUBMITTED';"
                + "})();";

        view.evaluateJavascript(js, value -> {
            if ("\"NO_FORM\"".equals(value) || "\"NO_FIELDS\"".equals(value)) {
                pendingError = "O formulário de acesso da FranLuz mudou e o aplicativo não conseguiu localizar os campos de login.";
                clearPendingLogin();
                showLocal("login");
            }
        });
    }

    private void completeRealLogin() {
        CookieManager.getInstance().flush();
        clearPendingLogin();
        loginFormInjected = false;
        verifyingAfterLogin = true;
        mode = MODE_CHECK_SESSION;
        webView.loadUrl(sellerUrl);
    }

    private void clearPendingLogin() {
        pendingLoginUser = "";
        pendingLoginPassword = "";
    }

    private void openSellerSection(String section) {
        mode = MODE_REMOTE;
        pendingSection = section == null ? "" : section;
        detachBridge();
        webView.loadUrl(sellerUrl);
    }

    private boolean acceptsImages(String[] types) {
        if (types == null || types.length == 0) {
            return true;
        }

        for (String type : types) {
            if (type == null
                || type.isEmpty()
                || "*/*".equals(type)
                || type.startsWith("image/")) {
                return true;
            }
        }

        return false;
    }

    private Intent buildCameraIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }

        try {
            ContentValues values = new ContentValues();
            values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "franluz_seller_" + System.currentTimeMillis() + ".jpg"
            );
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/FranLuz Seller"
            );

            pendingCaptureUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            );

            if (pendingCaptureUri == null) {
                return null;
            }

            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            camera.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            return camera;
        } catch (Exception ignored) {
            pendingCaptureUri = null;
            return null;
        }
    }

    private boolean handleNavigation(Uri uri) {
        String scheme =
            uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        if ("franluzseller".equals(scheme)) {
            handleSellerScheme(uri);
            return true;
        }

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isTrustedHost(uri)) {
                if ((mode == MODE_LOGIN || mode == MODE_CHECK_SESSION)
                    && isBlockedCustomerPath(uri)) {
                    pendingError = mode == MODE_LOGIN
                        ? "Esta conta não abriu o Seller Center. Use o acesso de vendedor da FranLuz."
                        : "";
                    showLocal("login");
                    return true;
                }

                if (mode == MODE_REMOTE && isBlockedCustomerPath(uri)) {
                    showLocal("home");
                    return true;
                }

                return false;
            }

            openExternal(uri);
            return true;
        }

        if ("tel".equals(scheme)
            || "mailto".equals(scheme)
            || "sms".equals(scheme)
            || "market".equals(scheme)
            || "whatsapp".equals(scheme)) {
            openExternal(uri);
            return true;
        }

        return false;
    }

    private void handleSellerScheme(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();

        if ("home".equals(host)) {
            showLocal("home");
            return;
        }

        if ("logout".equals(host)) {
            performLogout();
            return;
        }

        if ("section".equals(host)) {
            String path = uri.getPath();
            String section =
                path == null ? "" : path.replaceFirst("^/", "");
            openSellerSection(section);
        }
    }

    private boolean isTrustedHost(Uri uri) {
        String host = uri.getHost();
        return host != null
            && (TRUSTED_HOST.equalsIgnoreCase(host)
                || host.endsWith(".infinityfree.io"));
    }

    private boolean isSellerPath(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.startsWith("/seller-franluz");
    }

    private boolean isLoginOrCustomerPath(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.startsWith("/wp-login.php")
            || path.startsWith("/minha-conta")
            || path.startsWith("/vender-franluz")
            || "/".equals(path);
    }

    private boolean isBlockedCustomerPath(Uri uri) {
        String path = uri.getPath() == null ? "/" : uri.getPath();

        return "/".equals(path)
            || path.startsWith("/loja")
            || path.startsWith("/carrinho")
            || path.startsWith("/finalizar-compra")
            || path.startsWith("/checkout")
            || path.startsWith("/categoria-produto")
            || path.startsWith("/living-franluz")
            || path.startsWith("/minha-conta")
            || path.startsWith("/vender-franluz");
    }

    private void injectSellerChrome() {
        String js =
            "(function(){"
                + "var top=document.getElementById('franluzSellerNativeTop');if(top)top.remove();"
                + "var bottom=document.getElementById('franluzSellerNativeBottom');if(bottom)bottom.remove();"
                + "var s=document.getElementById('franluzSellerNativeStyle');"
                + "if(!s){s=document.createElement('style');s.id='franluzSellerNativeStyle';document.head.appendChild(s);}"
                + "s.textContent='"
                + "#wpadminbar,#masthead,#colophon,.site-header,.site-footer,"
                + ".storefront-primary-navigation,.main-navigation,.handheld-navigation,"
                + ".woocommerce-store-notice,.site-search,.whatsapp-float,.floating-whatsapp"
                + "{display:none!important}"
                + "html{margin-top:0!important}"
                + "body{padding-top:0!important;padding-bottom:0!important;background:#f7f3ef!important}"
                + "a[href*=\\"/carrinho/\\"],a[href*=\\"/loja/\\"],a[href*=\\"/categoria-produto/\\"],"
                + "a[href*=\\"/living-franluz/\\"]{display:none!important}"
                + "';"
                + "})();";

        webView.evaluateJavascript(js, null);
    }

    private void routeSellerSection(String section) {
        String keywords = sectionKeywords(section);

        String js =
            "(function(){"
                + "function norm(v){return (v||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').trim();}"
                + "var keys="
                + keywords
                + ".map(norm);"
                + "var nodes=[].slice.call(document.querySelectorAll('a,button,[role=button]'));"
                + "for(var i=0;i<nodes.length;i++){"
                + "var txt=norm(nodes[i].innerText||nodes[i].textContent);"
                + "if(!txt)continue;"
                + "for(var k=0;k<keys.length;k++){"
                + "if(txt.indexOf(keys[k])!==-1){nodes[i].click();return 'clicked';}"
                + "}"
                + "}"
                + "return 'notfound';"
                + "})();";

        webView.evaluateJavascript(js, value -> {
            if ("\"notfound\"".equals(value)) {
                Toast.makeText(
                    MainActivity.this,
                    "Área aberta no Seller Center. Selecione a função desejada nesta tela.",
                    Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private String sectionKeywords(String section) {
        if ("orders".equals(section)) {
            return "['pedidos','gerenciar pedidos','meus pedidos']";
        }
        if ("products".equals(section)) {
            return "['produtos','meus produtos','catalogo']";
        }
        if ("new_product".equals(section)) {
            return "['novo produto','adicionar produto','cadastrar produto']";
        }
        if ("stock".equals(section)) {
            return "['estoque','inventario','stock']";
        }
        if ("marketing".equals(section)) {
            return "['promocoes','marketing','campanhas']";
        }
        if ("coupons".equals(section)) {
            return "['cupons','cupom']";
        }
        if ("messages".equals(section)) {
            return "['mensagens','atendimento','chat']";
        }
        if ("reviews".equals(section)) {
            return "['avaliacoes','avaliacao']";
        }
        if ("analytics".equals(section)) {
            return "['desempenho','relatorios','analytics','vendas']";
        }
        if ("notifications".equals(section)) {
            return "['notificacoes','avisos']";
        }
        if ("account".equals(section)) {
            return "['configuracoes','perfil','conta']";
        }
        return "['seller center']";
    }

    private void attachBridge() {
        if (bridgeAttached) {
            return;
        }
        webView.addJavascriptInterface(new SellerBridge(), "FranLuzApp");
        bridgeAttached = true;
    }

    private void detachBridge() {
        if (!bridgeAttached) {
            return;
        }
        webView.removeJavascriptInterface("FranLuzApp");
        bridgeAttached = false;
    }

    private void performLogout() {
        detachBridge();

        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(value ->
            runOnUiThread(() -> {
                cookies.flush();
                pendingError = "";
                showLocal("login");
            })
        );
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
            Toast.makeText(
                this,
                "Não foi possível abrir este link.",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showOffline() {
        if (showingOffline) {
            return;
        }

        showingOffline = true;
        detachBridge();
        webView.loadUrl(OFFLINE_PAGE);
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        Uri[] result = null;

        if (resultCode == RESULT_OK) {
            result = WebChromeClient.FileChooserParams.parseResult(
                resultCode,
                data
            );

            if ((result == null || result.length == 0)
                && pendingCaptureUri != null) {
                result = new Uri[]{pendingCaptureUri};
            }
        } else if (pendingCaptureUri != null
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                getContentResolver().delete(pendingCaptureUri, null, null);
            } catch (Exception ignored) {
            }
        }

        if (fileCallback != null) {
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }

        pendingCaptureUri = null;
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
        if (mode == MODE_REMOTE) {
            showLocal("home");
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
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

    private class SellerBridge {
        @JavascriptInterface
        public void login(
            String username,
            String password,
            boolean remember
        ) {
            runOnUiThread(
                () -> submitLogin(username, password, remember)
            );
        }

        @JavascriptInterface
        public void openSection(String section) {
            runOnUiThread(() -> openSellerSection(section));
        }

        @JavascriptInterface
        public void logout() {
            runOnUiThread(MainActivity.this::performLogout);
        }

        @JavascriptInterface
        public String getVersion() {
            return "1.1.4";
        }
    }
}
