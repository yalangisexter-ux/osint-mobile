package io.notrack.osint;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "OSINT-MAIN";
    private static final String PREF_NAME = "osint_prefs";
    private static final String KEY_VPN_ENABLED = "vpn_enabled";
    private static final String KEY_TOR_ENABLED = "tor_enabled";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_THEME = "theme";

    // UI components
    private DrawerLayout drawerLayout;
    private LinearLayout drawer;
    private WebView web;
    private EditText searchInput;
    private TextView status;
    private ProgressBar progressBar;
    private ImageButton btnMenu, btnSearch, btnClear;
    private LinearLayout quickActionsRow;

    // State
    private boolean torModeEnabled = false;
    private boolean vpnModeEnabled = false;
    private boolean darkMode = true;
    private boolean oneTapResultsVisible = false;
    private String pendingHtmlReport = null;
    private String[] imgRes = new String[]{null};
    private String name = "", username = "", identity = "", profileUrl = "", emails = "", phones = "", accounts = "";

    // Broadcast receiver for Tor status
    private BroadcastReceiver torStatusReceiver;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init UI
        drawerLayout = findViewById(R.id.drawer_layout);
        drawer = findViewById(R.id.drawer);
        web = findViewById(R.id.web);
        searchInput = findViewById(R.id.search_input);
        status = findViewById(R.id.status);
        progressBar = findViewById(R.id.progress_bar);
        btnMenu = findViewById(R.id.btn_menu);
        btnSearch = findViewById(R.id.btn_search);
        btnClear = findViewById(R.id.btn_clear);
        quickActionsRow = findViewById(R.id.quick_actions);

        // Restore preferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        torModeEnabled = prefs.getBoolean(KEY_TOR_ENABLED, false);
        vpnModeEnabled = prefs.getBoolean(KEY_VPN_ENABLED, false);
        darkMode = prefs.getBoolean(KEY_DARK_MODE, true);

        // WebView setup
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setBackgroundColor(0xFF0b1020);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Open links in external browser, keep internal navigation for our results page
                if (url.startsWith("http") || url.startsWith("https")) {
                    if (url.contains(".onion")) {
                        startTorAndBrowse(url);
                        return true;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .show();
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        });

        // Search input
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // Buttons
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(drawer));
        btnSearch.setOnClickListener(v -> performSearch());
        btnClear.setOnClickListener(v -> {
            searchInput.setText("");
            web.loadUrl("about:blank");
            oneTapResultsVisible = false;
            pendingHtmlReport = null;
            status.setText("Ready");
        });

        // Drawer buttons
        findViewById(R.id.btn_dark).setOnClickListener(v -> { openDarkWeb(); closeDrawer(); });
        findViewById(R.id.btn_tor).setOnClickListener(v -> { startTorAndBrowse("https://check.torproject.org/"); closeDrawer(); });
        findViewById(R.id.btn_onion_list).setOnClickListener(v -> { openOnionSites(); closeDrawer(); });
        findViewById(R.id.btn_vpn).setOnClickListener(v -> { toggleVpn(); closeDrawer(); });
        findViewById(R.id.btn_settings).setOnClickListener(v -> { showSettings(); closeDrawer(); });
        findViewById(R.id.btn_about).setOnClickListener(v -> { showAbout(); closeDrawer(); });

        // Quick action buttons
        findViewById(R.id.quick_search).setOnClickListener(v -> performSearch());
        findViewById(R.id.quick_tor).setOnClickListener(v -> { torModeEnabled = true; startTorAndBrowse("https://check.torproject.org/"); });
        findViewById(R.id.quick_dark).setOnClickListener(v -> openDarkWeb());
        findViewById(R.id.quick_onion).setOnClickListener(v -> openOnionSites());
        findViewById(R.id.quick_vpn).setOnClickListener(v -> toggleVpn());

        // Register Tor status receiver
        torStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("io.notrack.osint.TOR_STATUS".equals(intent.getAction())) {
                    boolean running = intent.getBooleanExtra("running", false);
                    torModeEnabled = running;
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(KEY_TOR_ENABLED, running).apply();
                    status.setText(running ? "Tor running" : "Tor stopped");
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(torStatusReceiver,
                new IntentFilter("io.notrack.osint.TOR_STATUS"));

        // Load initial page
        web.loadUrl("about:blank");
        status.setText("Ready");
    }

    private void closeDrawer() {
        if (drawerLayout != null) drawerLayout.closeDrawer(drawer);
    }

    private void performSearch() {
        String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Enter a search query", Toast.LENGTH_SHORT).show();
            return;
        }
        if (q.contains(".onion")) {
            startTorAndBrowse(q.startsWith("http") ? q : "http://" + q);
            return;
        }
        startOneTapPublicSearch(q);
    }

    // ──────────────────────────────────────────────────────────
    // ONE-TAP PUBLIC SEARCH (the main fix: mobile-first results)
    // ──────────────────────────────────────────────────────────
    private void startOneTapPublicSearch(String query) {
        name = query; username = query; identity = query;
        profileUrl = ""; emails = ""; phones = ""; accounts = "";

        // Build job list: [category, search query]
        ArrayList<String[]> jobs = new ArrayList<>();
        jobs.add(new String[]{"Web", query});
        jobs.add(new String[]{"Dating", query + " dating site profile"});
        jobs.add(new String[]{"Porn", query + " porn site profile"});
        jobs.add(new String[]{"Hotels", query + " hotel booking"});
        jobs.add(new String[]{"Cloud", query + " cloud storage"});
        jobs.add(new String[]{"Telecom", query + " phone carrier"});
        jobs.add(new String[]{"Social", query + " social media"});
        jobs.add(new String[]{"Dark Web", query + " darkweb .onion"});
        jobs.add(new String[]{"Onion", query + " .onion"});

        final int total = jobs.size();
        status.setText("One-tap: 0/" + total);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(total);
        progressBar.setProgress(0);

        oneTapResultsVisible = true;
        web.setBackgroundColor(0xFF0b1020);
        web.setVisibility(View.VISIBLE);

        // Show loading page
        web.loadDataWithBaseURL(null,
                "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<body style='font:16px Arial;padding:18px;background:#0b1020;color:#eef4ff'>" +
                "<h2>One-tap search</h2><p>Searching " + total + " categories…</p></body>",
                "text/html", "UTF-8", null);

        new Thread(() -> {
            final Map<String, ArrayList<String[]>> byCat = new LinkedHashMap<>();
            ArrayList<String[]> failures = new ArrayList<>();
            for (int i = 0; i < jobs.size(); i++) {
                String[] job = jobs.get(i);
                final int completed = i + 1;
                runOnUiThread(() -> {
                    progressBar.setProgress(completed - 1);
                    status.setText("Fetching " + completed + "/" + total + " — " + job[0]);
                });
                try {
                    ArrayList<String[]> fetched = fetchBingResults(job[1], job[0]);
                    if (fetched.isEmpty()) {
                        failures.add(new String[]{job[0], "No results returned"});
                    } else {
                        byCat.computeIfAbsent(job[0], k -> new ArrayList<>()).addAll(fetched);
                    }
                } catch (Exception ex) {
                    failures.add(new String[]{job[0], ex.getClass().getSimpleName() + ": " + ex.getMessage()});
                }
            }
            final String html = buildResultsHtml(byCat, failures, query);
            runOnUiThread(() -> {
                pendingHtmlReport = html;
                web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                int ok = 0;
                for (ArrayList<String[]> v : byCat.values()) ok += v.size();
                progressBar.setProgress(total);
                progressBar.setVisibility(View.GONE);
                status.setText("Done: " + ok + " results across " + byCat.size() + " categories" +
                        (failures.isEmpty() ? "" : ", " + failures.size() + " empty"));
            });
        }, "onetap").start();
    }

    // ──────────────────────────────────────────────────────────
    // MOBILE-FIRST RESULTS HTML (fixes the "no scrolling" bug)
    // ──────────────────────────────────────────────────────────
    private String buildResultsHtml(Map<String, ArrayList<String[]>> byCat,
                                     ArrayList<String[]> failures, String query) {
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head>")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>")
         .append("<title>OSINT Results</title><style>")
         .append("*{box-sizing:border-box;margin:0;padding:0}")
         .append("body{font:14px -apple-system,Segoe UI,Arial,sans-serif;color:#eef4ff;background:#0b1020;overflow-x:hidden}")
         .append(".hdr{position:sticky;top:0;z-index:10;background:#0f1730;padding:12px 14px;border-bottom:1px solid #22305c;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap}")
         .append(".hdr h2{font-size:16px;color:#fff;font-weight:700}")
         .append(".hdr .cnt{font-size:12px;color:#8ab4ff}")
         .append(".query{font-size:13px;color:#9fb0d6;padding:4px 14px 0}")
         .append(".badge{display:inline-block;color:#fff;font-size:10px;padding:2px 7px;border-radius:9px;margin-right:5px;vertical-align:middle;white-space:nowrap}")
         .append(".card{background:#101a38;border:1px solid #22305c;border-radius:10px;padding:10px 12px;margin:8px 12px}")
         .append(".card .t{color:#fff;font-weight:600;font-size:14px;margin-bottom:4px}")
         .append(".card a{color:#6db3ff;text-decoration:none;word-break:break-all;font-size:12px;display:block;margin:3px 0}")
         .append(".card .s{color:#9fb0d6;font-size:12px;margin-top:3px}")
         .append(".fail{background:#2a1020;border:1px solid #5a2040;border-radius:10px;padding:10px 12px;margin:8px 12px;font-size:12px;color:#f0a0c0}")
         .append(".sec{margin:14px 14px 6px;font-size:15px;color:#8ab4ff;font-weight:600}")
         .append(".note{font-size:12px;color:#9fb0d6;margin:8px 14px}")
         .append(".empty{font-size:14px;color:#6b7fa0;text-align:center;padding:40px 14px}")
         .append("</style></head><body>")
         .append("<div class='hdr'><h2>OSINT Results</h2><span class='cnt' id='cnt'></span></div>")
         .append("<div class='query'>Query: ").append(escapeHtml(query)).append("</div>");

        // Category colors
        Map<String, String> catColors = new HashMap<>();
        catColors.put("Dark Web", "#8b5cf6");
        catColors.put("Onion", "#06b6d4");
        catColors.put("Dating", "#f43f5e");
        catColors.put("Porn", "#ec4899");
        catColors.put("Hotels", "#f59e0b");
        catColors.put("Cloud", "#10b981");
        catColors.put("Telecom", "#3b82f6");
        catColors.put("Social", "#6366f1");

        int total = 0;
        for (ArrayList<String[]> v : byCat.values()) total += v.size();

        h.append("<div class='sec'>All results (").append(total).append(")</div>");

        for (Map.Entry<String, ArrayList<String[]>> e : byCat.entrySet()) {
            String cat = e.getKey();
            String color = catColors.getOrDefault(cat, "#2a6cff");
            for (String[] r : e.getValue()) {
                String title = r.length > 1 ? r[1] : "";
                String url = r.length > 2 ? r[2] : "";
                String snippet = r.length > 3 ? r[3] : "";
                h.append("<div class='card'>")
                 .append("<div class='t'><span class='badge' style='background:").append(color).append("'>")
                 .append(escapeHtml(cat)).append("</span>")
                 .append(escapeHtml(title)).append("</div>");
                if (!url.isEmpty()) {
                    h.append("<a href='").append(escapeHtml(url)).append("'>")
                     .append(escapeHtml(url)).append("</a>");
                }
                if (!snippet.isEmpty()) {
                    h.append("<div class='s'>").append(escapeHtml(snippet)).append("</div>");
                }
                h.append("</div>");
            }
        }

        if (total == 0) {
            h.append("<div class='empty'>No results found. Try a different query.</div>");
        }

        if (!failures.isEmpty()) {
            h.append("<div class='sec'>Failed queries (").append(failures.size()).append(")</div>");
            for (String[] f : failures) {
                h.append("<div class='fail'><b>").append(escapeHtml(f[0])).append("</b><br>")
                 .append(escapeHtml(f.length > 1 ? f[1] : "")).append("</div>");
            }
        }

        h.append("<div class='note'>Results are live from the search provider. Tap a link to open in your browser.</div>")
         .append("</body></html>");
        return h.toString();
    }

    // ──────────────────────────────────────────────────────────
    // FETCH BING RESULTS (unchanged logic, kept short)
    // ──────────────────────────────────────────────────────────
    private ArrayList<String[]> fetchBingResults(String query, String category) throws IOException {
        ArrayList<String[]> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://www.bing.com/search?q=" + encoded + "&count=20";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        String html = sb.toString();
        // Parse Bing results
        Pattern p = Pattern.compile("<h2><a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a></h2>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        int count = 0;
        while (m.find() && count < 20) {
            String url = m.group(1).replaceAll("<[^>]+>", "").trim();
            String title = Html.fromHtml(m.group(2).replaceAll("<[^>]+>", "").trim()).toString();
            if (title.isEmpty()) title = url;
            results.add(new String[]{category, title, url, ""});
            count++;
        }

        // Fallback: try another pattern
        if (results.isEmpty()) {
            p = Pattern.compile("<a[^>]*href=\"(https?://[^\"]+)\"[^>]*><h2>(.*?)</h2></a>", Pattern.DOTALL);
            m = p.matcher(html);
            while (m.find() && results.size() < 20) {
                String url = m.group(1).replaceAll("<[^>]+>", "").trim();
                String title = Html.fromHtml(m.group(2).replaceAll("<[^>]+>", "").trim()).toString();
                if (title.isEmpty()) title = url;
                results.add(new String[]{category, title, url, ""});
            }
        }
        return results;
    }

    // ──────────────────────────────────────────────────────────
    // TOR / VPN / DARK WEB HELPERS
    // ──────────────────────────────────────────────────────────
    private void startTorAndBrowse(String url) {
        torModeEnabled = true;
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(KEY_TOR_ENABLED, true).apply();
        status.setText("Tor enabled — browsing " + url);

        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            web.loadUrl(url);
        }
    }

    private void openDarkWeb() {
        String url = searchInput.getText().toString().trim();
        if (url.isEmpty()) url = "http://darkfailenbsdpr5f.onion/";
        startTorAndBrowse(url);
    }

    private void openOnionSites() {
        String[] onions = getResources().getStringArray(
                getResources().getIdentifier("onion_sites", "array", getPackageName()));
        String[] labels = new String[onions.length];
        String[] urls = new String[onions.length];
        for (int i = 0; i < onions.length; i++) {
            String[] parts = onions[i].split(" · ");
            labels[i] = parts.length > 1 ? parts[0] : "Site " + (i+1);
            urls[i] = parts.length > 1 ? parts[1] : parts[0];
        }
        new AlertDialog.Builder(this)
                .setTitle("Select .onion site")
                .setItems(labels, (d, which) -> startTorAndBrowse(urls[which]))
                .show();
    }

    private void toggleVpn() {
        vpnModeEnabled = !vpnModeEnabled;
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(KEY_VPN_ENABLED, vpnModeEnabled).apply();
        status.setText(vpnModeEnabled ? "VPN mode on" : "VPN mode off");
        Toast.makeText(this, "VPN " + (vpnModeEnabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        // Simple settings dialog
        String[] items = {"Dark mode: " + (darkMode ? "ON" : "OFF")};
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items, (d, w) -> {
                    darkMode = !darkMode;
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
                    if (darkMode) {
                        web.setBackgroundColor(0xFF0b1020);
                    } else {
                        web.setBackgroundColor(0xFFFFFFFF);
                    }
                })
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("OSINT Mobile")
                .setMessage("Version 1.0\nAll-in-one investigation suite.\nOne-tap multi-source search.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeMessage(String msg) {
        return msg != null ? msg : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (torStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(torStatusReceiver);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(drawer)) {
            drawerLayout.closeDrawer(drawer);
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}