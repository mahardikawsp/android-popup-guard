package id.local.videopopupguard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import android.content.Intent;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    // UI Containers
    private FrameLayout root;
    private FrameLayout homeLayout;
    private FrameLayout categoryDetailLayout;
    private LinearLayout browserLayout;
    private LinearLayout topControls;
    private Button floatingShowControlsBtn;
    private Button floatingExitFsBtn;

    // Browser elements
    private WebView web; // points to active tab's WebView
    private EditText address;
    private TextView status;
    private CheckBox lockHost;
    private CheckBox blockPopupAds;

    // Multi-tab support
    private final List<WebView> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private HorizontalScrollView tabScrollView;
    private LinearLayout tabBarContainer;   // holds tab chips + new-tab btn
    private FrameLayout webViewContainer;   // swaps active WebView

    // Home elements
    private EditText homeLinkInput;
    private TextView homeTotalBadgeText;
    private LinearLayout categoriesGridContainer;

    // Category detail elements
    private TextView catDetailTitle;
    private TextView catDetailCountText;
    private LinearLayout catDetailListContainer;
    private EditText catDetailSearchInput;
    private String currentSelectedCatId = "";

    // Web state & Guard
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String guard = "";
    private String selectedHost = "";
    private boolean earlyScript;
    private boolean isManualFullscreen;
    private int rejectedWindows;
    private int rejectedNavigations;

    // Density helper
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createShape(int fillColor, int strokeColor, int strokeWidthDp, int cornerRadiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        if (strokeWidthDp > 0) {
            shape.setStroke(dp(strokeWidthDp), strokeColor);
        }
        shape.setCornerRadius(dp(cornerRadiusDp));
        return shape;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Blokir screenshot, screen recording, dan preview di recent apps
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F8F9FA"));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        // Initialize Data (with default categories if empty)
        initDefaultCategoriesIfNeeded();

        // Load saved proxy if configured
        loadSavedProxyOnStartup();

        // Build views
        buildBrowserView();
        buildHomeScreen();
        buildCategoryDetailView();

        // Start by showing the Home Screen as requested
        showHomeView();
    }

    // =============================================================
    // 1. HOME SCREEN (Collections & Quick Save Dashboard)
    // =============================================================
    private void buildHomeScreen() {
        homeLayout = new FrameLayout(this);
        homeLayout.setBackgroundColor(Color.parseColor("#FBFBFD"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        homeLayout.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // Top Navigation Row
        LinearLayout topNav = new LinearLayout(this);
        topNav.setOrientation(LinearLayout.HORIZONTAL);
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        topNav.setPadding(0, 0, 0, dp(14));

        TextView brand = new TextView(this);
        brand.setText("popup guard");
        brand.setTextSize(18);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(Color.parseColor("#18181B"));
        topNav.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button dnsHomeBtn = new Button(this);
        dnsHomeBtn.setText("⚡ DNS");
        dnsHomeBtn.setTextSize(11);
        dnsHomeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        dnsHomeBtn.setTextColor(Color.parseColor("#854D0E"));
        dnsHomeBtn.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#CA8A04"), 1, 14));
        dnsHomeBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        dnsHomeBtn.setMinHeight(dp(32));
        dnsHomeBtn.setMinimumHeight(dp(32));
        LinearLayout.LayoutParams dhp = new LinearLayout.LayoutParams(-2, dp(32));
        dhp.rightMargin = dp(6);
        dnsHomeBtn.setLayoutParams(dhp);
        dnsHomeBtn.setOnClickListener(v -> showDnsDialog());
        topNav.addView(dnsHomeBtn);

        Button clearHomeBtn = new Button(this);
        clearHomeBtn.setText("🧹 Bersihkan");
        clearHomeBtn.setTextSize(11);
        clearHomeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearHomeBtn.setTextColor(Color.parseColor("#B91C1C"));
        clearHomeBtn.setBackground(createShape(Color.parseColor("#FEE2E2"), Color.parseColor("#EF4444"), 1, 14));
        clearHomeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        clearHomeBtn.setMinHeight(dp(32));
        clearHomeBtn.setMinimumHeight(dp(32));
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-2, dp(32));
        chp.rightMargin = dp(6);
        clearHomeBtn.setLayoutParams(chp);
        clearHomeBtn.setOnClickListener(v -> showClearCacheAndHistoryDialog());
        topNav.addView(clearHomeBtn);

        Button openBrowserDirectBtn = new Button(this);
        openBrowserDirectBtn.setText("🌐 Browser");
        openBrowserDirectBtn.setTextSize(11);
        openBrowserDirectBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openBrowserDirectBtn.setTextColor(Color.parseColor("#18181B"));
        openBrowserDirectBtn.setBackground(createShape(Color.WHITE, Color.parseColor("#E4E4E7"), 1, 14));
        openBrowserDirectBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        openBrowserDirectBtn.setMinHeight(dp(32));
        openBrowserDirectBtn.setMinimumHeight(dp(32));
        openBrowserDirectBtn.setOnClickListener(v -> showBrowserView(null));
        topNav.addView(openBrowserDirectBtn);

        content.addView(topNav);

        // Big Headline (Inspired by "Save now. Find anytime.")
        TextView headline = new TextView(this);
        headline.setText("Save now.\nFind anytime.");
        headline.setTextSize(26);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(Color.parseColor("#09090B"));
        headline.setLineSpacing(dp(2), 1.0f);
        content.addView(headline);

        TextView subHeadline = new TextView(this);
        subHeadline.setText("Simpan tautan video favorit & putar bebas iklan/popup.");
        subHeadline.setTextSize(13);
        subHeadline.setTextColor(Color.parseColor("#71717A"));
        subHeadline.setPadding(0, dp(4), 0, dp(14));
        content.addView(subHeadline);

        // Quick Input Bar
        LinearLayout quickBar = new LinearLayout(this);
        quickBar.setOrientation(LinearLayout.HORIZONTAL);
        quickBar.setGravity(Gravity.CENTER_VERTICAL);
        quickBar.setBackground(createShape(Color.WHITE, Color.parseColor("#18181B"), 2, 16));
        quickBar.setPadding(dp(12), dp(4), dp(4), dp(4));

        TextView hashIcon = new TextView(this);
        hashIcon.setText("# ");
        hashIcon.setTextSize(14);
        hashIcon.setTypeface(Typeface.DEFAULT_BOLD);
        hashIcon.setTextColor(Color.parseColor("#A1A1AA"));
        quickBar.addView(hashIcon);

        homeLinkInput = new EditText(this);
        homeLinkInput.setHint("Paste link to quick save");
        homeLinkInput.setHintTextColor(Color.parseColor("#A1A1AA"));
        homeLinkInput.setTextColor(Color.parseColor("#18181B"));
        homeLinkInput.setBackground(null);
        homeLinkInput.setTextSize(13);
        homeLinkInput.setSingleLine(true);
        homeLinkInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        homeLinkInput.setOnEditorActionListener((v, actionId, event) -> {
            handleQuickSaveClick();
            return true;
        });
        quickBar.addView(homeLinkInput, new LinearLayout.LayoutParams(0, -2, 1));

        Button clearHomeInputBtn = new Button(this);
        clearHomeInputBtn.setText("✕");
        clearHomeInputBtn.setTextSize(14);
        clearHomeInputBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearHomeInputBtn.setTextColor(Color.parseColor("#EF4444"));
        clearHomeInputBtn.setBackground(null);
        clearHomeInputBtn.setMinHeight(dp(36));
        clearHomeInputBtn.setMinimumHeight(dp(36));
        clearHomeInputBtn.setMinWidth(dp(32));
        clearHomeInputBtn.setMinimumWidth(dp(32));
        clearHomeInputBtn.setVisibility(View.GONE);
        clearHomeInputBtn.setOnClickListener(v -> {
            homeLinkInput.setText("");
            homeLinkInput.requestFocus();
        });
        quickBar.addView(clearHomeInputBtn);

        homeLinkInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearHomeInputBtn.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button pasteBtn = new Button(this);
        pasteBtn.setText("📋");
        pasteBtn.setTextSize(13);
        pasteBtn.setBackground(null);
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    homeLinkInput.setText(text.toString().trim());
                    homeLinkInput.setSelection(homeLinkInput.getText().length());
                }
            }
        });
        quickBar.addView(pasteBtn);

        Button quickSaveBtn = new Button(this);
        quickSaveBtn.setText("+");
        quickSaveBtn.setTextSize(18);
        quickSaveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        quickSaveBtn.setTextColor(Color.parseColor("#18181B"));
        quickSaveBtn.setBackground(createShape(Color.parseColor("#FCD34D"), Color.parseColor("#18181B"), 2, 12));
        quickSaveBtn.setMinHeight(dp(40));
        quickSaveBtn.setMinimumHeight(dp(40));
        quickSaveBtn.setPadding(dp(14), 0, dp(14), 0);
        quickSaveBtn.setOnClickListener(v -> handleQuickSaveClick());
        quickBar.addView(quickSaveBtn);

        content.addView(quickBar);

        // Alert Banner ("26 new saves waiting")
        LinearLayout alertBanner = new LinearLayout(this);
        alertBanner.setOrientation(LinearLayout.HORIZONTAL);
        alertBanner.setGravity(Gravity.CENTER_VERTICAL);
        alertBanner.setBackground(createShape(Color.parseColor("#F4F4F5"), Color.parseColor("#18181B"), 2, 14));
        LinearLayout.LayoutParams alertParams = new LinearLayout.LayoutParams(-1, -2);
        alertParams.setMargins(0, dp(14), 0, dp(18));
        alertBanner.setLayoutParams(alertParams);
        alertBanner.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView bellIcon = new TextView(this);
        bellIcon.setText("🛡️");
        bellIcon.setTextSize(16);
        bellIcon.setBackground(createShape(Color.parseColor("#DCFCE7"), Color.parseColor("#18181B"), 1, 10));
        bellIcon.setPadding(dp(8), dp(6), dp(8), dp(6));
        alertBanner.addView(bellIcon);

        LinearLayout alertTextWrap = new LinearLayout(this);
        alertTextWrap.setOrientation(LinearLayout.VERTICAL);
        alertTextWrap.setPadding(dp(10), 0, dp(6), 0);

        homeTotalBadgeText = new TextView(this);
        homeTotalBadgeText.setText("Koleksi Tautan Terproteksi");
        homeTotalBadgeText.setTextSize(13);
        homeTotalBadgeText.setTypeface(Typeface.DEFAULT_BOLD);
        homeTotalBadgeText.setTextColor(Color.parseColor("#18181B"));
        alertTextWrap.addView(homeTotalBadgeText);

        TextView alertSub = new TextView(this);
        alertSub.setText("Popup blocker aktif · Klik kategori di bawah");
        alertSub.setTextSize(11);
        alertSub.setTextColor(Color.parseColor("#71717A"));
        alertTextWrap.addView(alertSub);

        alertBanner.addView(alertTextWrap, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(alertBanner);

        // Section: My Collections
        LinearLayout collectionsHeader = new LinearLayout(this);
        collectionsHeader.setOrientation(LinearLayout.HORIZONTAL);
        collectionsHeader.setGravity(Gravity.CENTER_VERTICAL);
        collectionsHeader.setPadding(0, 0, 0, dp(10));

        TextView collTitle = new TextView(this);
        collTitle.setText("My Collections");
        collTitle.setTextSize(16);
        collTitle.setTypeface(Typeface.DEFAULT_BOLD);
        collTitle.setTextColor(Color.parseColor("#18181B"));
        collectionsHeader.addView(collTitle, new LinearLayout.LayoutParams(0, -2, 1));

        Button addCatBtn = new Button(this);
        addCatBtn.setText("+ Kategori");
        addCatBtn.setTextSize(11);
        addCatBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addCatBtn.setTextColor(Color.parseColor("#18181B"));
        addCatBtn.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#18181B"), 1, 8));
        addCatBtn.setPadding(dp(10), dp(2), dp(10), dp(2));
        addCatBtn.setMinHeight(dp(30));
        addCatBtn.setMinimumHeight(dp(30));
        addCatBtn.setOnClickListener(v -> showAddCategoryDialog());
        collectionsHeader.addView(addCatBtn);

        content.addView(collectionsHeader);

        // Categories Grid Container (2 Columns)
        categoriesGridContainer = new LinearLayout(this);
        categoriesGridContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(categoriesGridContainer);

        root.addView(homeLayout, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderCategoriesGrid() {
        if (categoriesGridContainer == null) return;
        categoriesGridContainer.removeAllViews();

        JSONArray categories = getCategoriesArray();
        int totalLinks = 0;

        for (int i = 0; i < categories.length(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(10);
            row.setLayoutParams(rowParams);

            // Card 1
            JSONObject cat1 = categories.optJSONObject(i);
            if (cat1 != null) {
                totalLinks += cat1.optJSONArray("links") != null ? cat1.optJSONArray("links").length() : 0;
                View card1 = buildCategoryFolderCard(cat1);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1);
                p1.rightMargin = dp(6);
                row.addView(card1, p1);
            }

            // Card 2
            if (i + 1 < categories.length()) {
                JSONObject cat2 = categories.optJSONObject(i + 1);
                if (cat2 != null) {
                    totalLinks += cat2.optJSONArray("links") != null ? cat2.optJSONArray("links").length() : 0;
                    View card2 = buildCategoryFolderCard(cat2);
                    LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1);
                    p2.leftMargin = dp(6);
                    row.addView(card2, p2);
                }
            } else {
                View emptySpacer = new View(this);
                row.addView(emptySpacer, new LinearLayout.LayoutParams(0, -2, 1));
            }

            categoriesGridContainer.addView(row);
        }

        if (homeTotalBadgeText != null) {
            homeTotalBadgeText.setText(totalLinks + " Tautan Tersimpan");
        }
    }

    // Build Folder-Tab Styled Category Card (Like user's mockup!)
    private View buildCategoryFolderCard(JSONObject cat) {
        String id = cat.optString("id");
        String name = cat.optString("name", "Untitled");
        String icon = cat.optString("icon", "📁");
        String colorHex = cat.optString("color", "#FDE047");
        JSONArray links = cat.optJSONArray("links");
        int count = links != null ? links.length() : 0;

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        // Top colored tab (Folder tab)
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER_VERTICAL);
        tab.setBackground(createShape(Color.parseColor(colorHex), Color.parseColor("#18181B"), 2, 8));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(dp(44), dp(28));
        tabParams.leftMargin = dp(12);
        tab.setLayoutParams(tabParams);
        tab.setPadding(dp(6), dp(2), dp(6), dp(2));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(13);
        iconView.setGravity(Gravity.CENTER);
        tab.addView(iconView, new LinearLayout.LayoutParams(-1, -1));

        wrapper.addView(tab);

        // Main Card Body with dark 2dp border
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(createShape(Color.WHITE, Color.parseColor("#18181B"), 2, 14));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = -dp(2);
        body.setLayoutParams(bodyParams);
        body.setPadding(dp(12), dp(10), dp(12), dp(12));

        // Row info: items count + NEW tag
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView countText = new TextView(this);
        countText.setText(count + " items");
        countText.setTextSize(10);
        countText.setTextColor(Color.parseColor("#71717A"));
        metaRow.addView(countText, new LinearLayout.LayoutParams(0, -2, 1));

        if (count > 0) {
            TextView newTag = new TextView(this);
            newTag.setText("SAVED");
            newTag.setTextSize(8);
            newTag.setTypeface(Typeface.DEFAULT_BOLD);
            newTag.setTextColor(Color.parseColor("#854D0E"));
            newTag.setBackground(createShape(Color.parseColor("#FEF08A"), 0, 0, 4));
            newTag.setPadding(dp(4), dp(1), dp(4), dp(1));
            metaRow.addView(newTag);
        }

        body.addView(metaRow);

        TextView nameText = new TextView(this);
        nameText.setText(name);
        nameText.setTextSize(13);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        nameText.setTextColor(Color.parseColor("#18181B"));
        nameText.setPadding(0, dp(12), 0, dp(4));
        nameText.setMaxLines(2);
        body.addView(nameText);

        wrapper.addView(body);

        // Click on category -> open category detail
        wrapper.setOnClickListener(v -> promptPasswordThenOpenCategory(id));

        // Long click -> Option to delete category
        wrapper.setOnLongClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Kategori: " + name)
                .setItems(new String[]{"Buka Kategori", "Hapus Kategori"}, (d, which) -> {
                    if (which == 0) promptPasswordThenOpenCategory(id);
                    else if (which == 1) confirmDeleteCategory(id, name);
                })
                .show();
            return true;
        });

        return wrapper;
    }

    // =============================================================
    // 2. CATEGORY DETAIL VIEW (Links inside selected category)
    // =============================================================
    private void buildCategoryDetailView() {
        categoryDetailLayout = new FrameLayout(this);
        categoryDetailLayout.setBackgroundColor(Color.parseColor("#F9FAFB"));
        categoryDetailLayout.setVisibility(View.GONE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        categoryDetailLayout.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // Top Navigation
        LinearLayout topNav = new LinearLayout(this);
        topNav.setOrientation(LinearLayout.HORIZONTAL);
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        topNav.setPadding(dp(16), dp(12), dp(16), dp(10));
        topNav.setBackgroundColor(Color.WHITE);

        Button backBtn = new Button(this);
        backBtn.setText("← Kembali");
        backBtn.setTextSize(12);
        backBtn.setTypeface(Typeface.DEFAULT_BOLD);
        backBtn.setTextColor(Color.parseColor("#18181B"));
        backBtn.setBackground(null);
        backBtn.setOnClickListener(v -> showHomeView());
        topNav.addView(backBtn);

        catDetailTitle = new TextView(this);
        catDetailTitle.setText("Koleksi");
        catDetailTitle.setTextSize(16);
        catDetailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        catDetailTitle.setTextColor(Color.parseColor("#18181B"));
        catDetailTitle.setGravity(Gravity.CENTER);
        topNav.addView(catDetailTitle, new LinearLayout.LayoutParams(0, -2, 1));

        Button addLinkBtn = new Button(this);
        addLinkBtn.setText("+ Link");
        addLinkBtn.setTextSize(11);
        addLinkBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addLinkBtn.setTextColor(Color.parseColor("#18181B"));
        addLinkBtn.setBackground(createShape(Color.parseColor("#FCD34D"), Color.parseColor("#18181B"), 1, 8));
        addLinkBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        addLinkBtn.setMinHeight(dp(30));
        addLinkBtn.setMinimumHeight(dp(30));
        addLinkBtn.setOnClickListener(v -> showAddLinkToCurrentCategoryDialog());
        topNav.addView(addLinkBtn);

        content.addView(topNav);

        // Search Bar
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(createShape(Color.parseColor("#FFFFFF"), Color.parseColor("#E4E4E7"), 1, 14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(40));
        sp.setMargins(dp(16), dp(8), dp(16), dp(6));
        searchBox.setLayoutParams(sp);
        searchBox.setPadding(dp(12), 0, dp(12), 0);

        TextView sIcon = new TextView(this);
        sIcon.setText("🔍 ");
        searchBox.addView(sIcon);

        catDetailSearchInput = new EditText(this);
        catDetailSearchInput.setHint("Cari tautan dalam kategori ini...");
        catDetailSearchInput.setHintTextColor(Color.parseColor("#9CA3AF"));
        catDetailSearchInput.setTextColor(Color.parseColor("#1F2937"));
        catDetailSearchInput.setBackground(null);
        catDetailSearchInput.setTextSize(12);
        catDetailSearchInput.setSingleLine(true);
        searchBox.addView(catDetailSearchInput, new LinearLayout.LayoutParams(0, -2, 1));

        content.addView(searchBox);

        // Yellow Library Stat Card
        LinearLayout statCard = new LinearLayout(this);
        statCard.setOrientation(LinearLayout.HORIZONTAL);
        statCard.setGravity(Gravity.CENTER_VERTICAL);
        statCard.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#18181B"), 2, 14));
        LinearLayout.LayoutParams statParams = new LinearLayout.LayoutParams(-1, -2);
        statParams.setMargins(dp(16), dp(4), dp(16), dp(10));
        statCard.setLayoutParams(statParams);
        statCard.setPadding(dp(14), dp(10), dp(14), dp(10));

        catDetailCountText = new TextView(this);
        catDetailCountText.setText("0 Tautan Tersimpan");
        catDetailCountText.setTextSize(13);
        catDetailCountText.setTypeface(Typeface.DEFAULT_BOLD);
        catDetailCountText.setTextColor(Color.parseColor("#18181B"));
        statCard.addView(catDetailCountText, new LinearLayout.LayoutParams(0, -2, 1));

        Button openBrowserDirect = new Button(this);
        openBrowserDirect.setText("Buka Browser");
        openBrowserDirect.setTextSize(10);
        openBrowserDirect.setTypeface(Typeface.DEFAULT_BOLD);
        openBrowserDirect.setTextColor(Color.parseColor("#18181B"));
        openBrowserDirect.setBackground(createShape(Color.WHITE, Color.parseColor("#18181B"), 1, 8));
        openBrowserDirect.setPadding(dp(8), dp(2), dp(8), dp(2));
        openBrowserDirect.setOnClickListener(v -> showBrowserView(null));
        statCard.addView(openBrowserDirect);

        content.addView(statCard);

        // Scrollable List of links (without thumbnail/preview, one by one delete)
        ScrollView scrollView = new ScrollView(this);
        catDetailListContainer = new LinearLayout(this);
        catDetailListContainer.setOrientation(LinearLayout.VERTICAL);
        catDetailListContainer.setPadding(dp(16), 0, dp(16), dp(24));
        scrollView.addView(catDetailListContainer, new FrameLayout.LayoutParams(-1, -2));

        content.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        catDetailSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCategoryLinksList(currentSelectedCatId, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        root.addView(categoryDetailLayout, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderCategoryLinksList(String catId, String filter) {
        if (catDetailListContainer == null) return;
        catDetailListContainer.removeAllViews();

        JSONObject cat = getCategoryById(catId);
        if (cat == null) return;

        catDetailTitle.setText(cat.optString("icon", "📁") + " " + cat.optString("name", "Koleksi"));
        JSONArray links = cat.optJSONArray("links");
        if (links == null) links = new JSONArray();

        catDetailCountText.setText(links.length() + " Tautan Tersimpan");

        String query = (filter != null) ? filter.trim().toLowerCase(Locale.ROOT) : "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault());
        int rendered = 0;

        for (int i = 0; i < links.length(); i++) {
            final int linkIndex = i;
            JSONObject linkObj = links.optJSONObject(i);
            if (linkObj == null) continue;
            String url = linkObj.optString("url", "");
            long time = linkObj.optLong("time", 0);

            if (!query.isEmpty() && !url.toLowerCase(Locale.ROOT).contains(query)) continue;
            rendered++;

            // Clean card without thumbnail
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(createShape(Color.WHITE, Color.parseColor("#18181B"), 2, 14));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(8);
            card.setLayoutParams(cp);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));

            // Domain Badge & Date
            LinearLayout hRow = new LinearLayout(this);
            hRow.setOrientation(LinearLayout.HORIZONTAL);
            hRow.setGravity(Gravity.CENTER_VERTICAL);

            String host = UrlPolicy.host(url);
            TextView hostBadge = new TextView(this);
            hostBadge.setText(host.isEmpty() ? "Link" : host);
            hostBadge.setTextSize(10);
            hostBadge.setTypeface(Typeface.DEFAULT_BOLD);
            hostBadge.setTextColor(Color.parseColor("#18181B"));
            hostBadge.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#CA8A04"), 1, 6));
            hostBadge.setPadding(dp(6), dp(1), dp(6), dp(1));
            hRow.addView(hostBadge);

            TextView dateView = new TextView(this);
            dateView.setText(time > 0 ? sdf.format(new Date(time)) : "");
            dateView.setTextSize(10);
            dateView.setTextColor(Color.parseColor("#9CA3AF"));
            dateView.setGravity(Gravity.END);
            hRow.addView(dateView, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(hRow);

            // URL
            TextView urlView = new TextView(this);
            urlView.setText(url);
            urlView.setTextSize(13);
            urlView.setTextColor(Color.parseColor("#1F2937"));
            urlView.setMaxLines(2);
            urlView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            urlView.setPadding(0, dp(6), 0, dp(8));
            card.addView(urlView);

            // Actions: Buka, Salin, Hapus (One by one!)
            LinearLayout aRow = new LinearLayout(this);
            aRow.setOrientation(LinearLayout.HORIZONTAL);
            aRow.setGravity(Gravity.CENTER_VERTICAL);

            Button openBtn = new Button(this);
            openBtn.setText("▶ Buka");
            openBtn.setTextSize(11);
            openBtn.setTypeface(Typeface.DEFAULT_BOLD);
            openBtn.setTextColor(Color.parseColor("#18181B"));
            openBtn.setBackground(createShape(Color.parseColor("#FCD34D"), Color.parseColor("#18181B"), 1, 8));
            openBtn.setMinHeight(dp(30));
            openBtn.setMinimumHeight(dp(30));
            openBtn.setPadding(dp(10), dp(2), dp(10), dp(2));
            openBtn.setOnClickListener(v -> showBrowserView(url));
            aRow.addView(openBtn);

            Button copyBtn = new Button(this);
            copyBtn.setText("Salin");
            copyBtn.setTextSize(11);
            copyBtn.setTextColor(Color.parseColor("#374151"));
            copyBtn.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
            copyBtn.setMinHeight(dp(30));
            copyBtn.setMinimumHeight(dp(30));
            copyBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-2, dp(30));
            copyLp.leftMargin = dp(6);
            copyBtn.setLayoutParams(copyLp);
            copyBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("URL", url));
                    Toast.makeText(MainActivity.this, "Tautan disalin ke clipboard", Toast.LENGTH_SHORT).show();
                }
            });
            aRow.addView(copyBtn);

            View spacer = new View(this);
            aRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

            // DELETE BUTTON (One by one!)
            Button deleteBtn = new Button(this);
            deleteBtn.setText("🗑️ Hapus");
            deleteBtn.setTextSize(11);
            deleteBtn.setTypeface(Typeface.DEFAULT_BOLD);
            deleteBtn.setTextColor(Color.parseColor("#DC2626"));
            deleteBtn.setBackground(createShape(Color.parseColor("#FEE2E2"), Color.parseColor("#FCA5A5"), 1, 8));
            deleteBtn.setMinHeight(dp(30));
            deleteBtn.setMinimumHeight(dp(30));
            deleteBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
            deleteBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Hapus Tautan?")
                    .setMessage(url)
                    .setPositiveButton("Hapus", (d, w) -> deleteLinkFromCategory(catId, linkIndex))
                    .setNegativeButton("Batal", null)
                    .show();
            });
            aRow.addView(deleteBtn);

            card.addView(aRow);
            catDetailListContainer.addView(card);
        }

        if (rendered == 0) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada tautan di kategori ini.\nKlik '+ Link' untuk menambahkan.");
            empty.setTextSize(12);
            empty.setTextColor(Color.parseColor("#9CA3AF"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            catDetailListContainer.addView(empty);
        }
    }

    // =============================================================
    // 3. BROWSER VIEW (WebView with Collapsible Top Controls)
    // =============================================================
    private void buildBrowserView() {
        browserLayout = new LinearLayout(this);
        browserLayout.setOrientation(LinearLayout.VERTICAL);
        browserLayout.setVisibility(View.GONE);

        // -------------------------------------------------------------
        // TOP CONTROLS (With Hide / Show Toggle Button)
        // -------------------------------------------------------------
        topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.VERTICAL);
        topControls.setPadding(dp(12), dp(8), dp(12), dp(4));
        topControls.setBackgroundColor(Color.WHITE);

        // Header row with Back to Collections & Hide Bar toggle
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(6));

        Button backHomeBtn = new Button(this);
        backHomeBtn.setText("🏠 Koleksi");
        backHomeBtn.setTextSize(11);
        backHomeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        backHomeBtn.setTextColor(Color.parseColor("#18181B"));
        backHomeBtn.setBackground(createShape(Color.parseColor("#F4F4F5"), Color.parseColor("#E4E4E7"), 1, 8));
        backHomeBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        backHomeBtn.setMinHeight(dp(30));
        backHomeBtn.setMinimumHeight(dp(30));
        backHomeBtn.setOnClickListener(v -> showHomeView());
        headerRow.addView(backHomeBtn);

        TextView title = new TextView(this);
        title.setText("popup guard");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#18181B"));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> showHomeView());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(dp(4), 0, dp(4), 0);
        headerRow.addView(title, titleLp);

        Button dnsBrowserBtn = new Button(this);
        dnsBrowserBtn.setText("⚡ DNS");
        dnsBrowserBtn.setTextSize(10);
        dnsBrowserBtn.setTypeface(Typeface.DEFAULT_BOLD);
        dnsBrowserBtn.setTextColor(Color.parseColor("#854D0E"));
        dnsBrowserBtn.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#CA8A04"), 1, 8));
        dnsBrowserBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        dnsBrowserBtn.setMinHeight(dp(30));
        dnsBrowserBtn.setMinimumHeight(dp(30));
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-2, dp(30));
        dbp.rightMargin = dp(4);
        dnsBrowserBtn.setLayoutParams(dbp);
        dnsBrowserBtn.setOnClickListener(v -> showDnsDialog());
        headerRow.addView(dnsBrowserBtn);

        Button clearBrowserBtn = new Button(this);
        clearBrowserBtn.setText("🧹");
        clearBrowserBtn.setTextSize(12);
        clearBrowserBtn.setTextColor(Color.parseColor("#B91C1C"));
        clearBrowserBtn.setBackground(createShape(Color.parseColor("#FEE2E2"), Color.parseColor("#EF4444"), 1, 8));
        clearBrowserBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        clearBrowserBtn.setMinHeight(dp(30));
        clearBrowserBtn.setMinimumHeight(dp(30));
        clearBrowserBtn.setMinWidth(dp(32));
        clearBrowserBtn.setMinimumWidth(dp(32));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(30));
        clp.rightMargin = dp(4);
        clearBrowserBtn.setLayoutParams(clp);
        clearBrowserBtn.setOnClickListener(v -> showClearCacheAndHistoryDialog());
        headerRow.addView(clearBrowserBtn);

        // TOOLTIP / HIDE BUTTON (User requested button to hide topControls)
        Button hideBarBtn = new Button(this);
        hideBarBtn.setText("▲");
        hideBarBtn.setTextSize(11);
        hideBarBtn.setTypeface(Typeface.DEFAULT_BOLD);
        hideBarBtn.setTextColor(Color.parseColor("#4B5563"));
        hideBarBtn.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
        hideBarBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        hideBarBtn.setMinHeight(dp(30));
        hideBarBtn.setMinimumHeight(dp(30));
        hideBarBtn.setMinWidth(dp(30));
        hideBarBtn.setMinimumWidth(dp(30));
        hideBarBtn.setOnClickListener(v -> hideTopControlsBar());
        headerRow.addView(hideBarBtn);

        topControls.addView(headerRow);

        // Address Bar
        LinearLayout addressBox = new LinearLayout(this);
        addressBox.setOrientation(LinearLayout.HORIZONTAL);
        addressBox.setGravity(Gravity.CENTER_VERTICAL);
        addressBox.setBackground(createShape(Color.parseColor("#F9FAFB"), Color.parseColor("#E5E7EB"), 1, 12));
        addressBox.setPadding(dp(8), dp(2), dp(4), dp(2));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("Ketik atau tempel URL video...");
        address.setHintTextColor(Color.parseColor("#9CA3AF"));
        address.setTextColor(Color.parseColor("#1F2937"));
        address.setBackground(null);
        address.setTextSize(13);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, actionId, event) -> {
            openUrlFromAddress();
            return true;
        });
        addressBox.addView(address, new LinearLayout.LayoutParams(0, -2, 1));

        Button clearAddressBtn = new Button(this);
        clearAddressBtn.setText("✕");
        clearAddressBtn.setTextSize(13);
        clearAddressBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearAddressBtn.setTextColor(Color.parseColor("#EF4444"));
        clearAddressBtn.setBackground(null);
        clearAddressBtn.setMinHeight(dp(34));
        clearAddressBtn.setMinimumHeight(dp(34));
        clearAddressBtn.setMinWidth(dp(30));
        clearAddressBtn.setMinimumWidth(dp(30));
        clearAddressBtn.setVisibility(View.GONE);
        clearAddressBtn.setOnClickListener(v -> {
            address.setText("");
            address.requestFocus();
        });
        addressBox.addView(clearAddressBtn);

        address.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearAddressBtn.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button open = new Button(this);
        open.setText("Buka");
        open.setTextColor(Color.parseColor("#18181B"));
        open.setTextSize(12);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setBackground(createShape(Color.parseColor("#FCD34D"), Color.parseColor("#18181B"), 1, 8));
        open.setPadding(dp(12), 0, dp(12), 0);
        open.setMinHeight(dp(34));
        open.setMinimumHeight(dp(34));
        open.setOnClickListener(v -> openUrlFromAddress());
        addressBox.addView(open);

        topControls.addView(addressBox);

        // Action Toolbar
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(6), 0, dp(4));

        Button back = createModernButton("← Kembali", Color.WHITE, Color.parseColor("#18181B"), Color.parseColor("#D4D4D8"));
        Button reload = createModernButton("↻ Muat", Color.WHITE, Color.parseColor("#18181B"), Color.parseColor("#D4D4D8"));
        Button fullscreenBtn = createModernButton("⛶ Fullscreen", Color.parseColor("#18181B"), Color.WHITE, Color.parseColor("#18181B"));
        Button saveLinkBtn = createModernButton("⭐ Simpan Link", Color.parseColor("#FEF08A"), Color.parseColor("#854D0E"), Color.parseColor("#CA8A04"));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, dp(34), 1);
        btnParams.setMargins(dp(2), 0, dp(2), 0);
        actionRow.addView(back, btnParams);
        actionRow.addView(reload, btnParams);
        actionRow.addView(fullscreenBtn, btnParams);
        actionRow.addView(saveLinkBtn, btnParams);

        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        reload.setOnClickListener(v -> web.reload());
        fullscreenBtn.setOnClickListener(v -> enterFullscreen());
        saveLinkBtn.setOnClickListener(v -> saveCurrentUrlToCategory());

        topControls.addView(actionRow);

        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setGravity(Gravity.CENTER_VERTICAL);
        optionsRow.setPadding(0, dp(4), 0, dp(2));

        lockHost = new CheckBox(this);
        lockHost.setText("🔒 Kunci Domain Utama");
        lockHost.setTextSize(11);
        lockHost.setTextColor(Color.parseColor("#4B5563"));
        optionsRow.addView(lockHost);

        blockPopupAds = new CheckBox(this);
        blockPopupAds.setText("🛡️ Blokir Iklan & Popup");
        blockPopupAds.setChecked(true);
        blockPopupAds.setTextSize(11);
        blockPopupAds.setTextColor(Color.parseColor("#15803D"));
        LinearLayout.LayoutParams bpaLp = new LinearLayout.LayoutParams(-2, -2);
        bpaLp.leftMargin = dp(10);
        blockPopupAds.setLayoutParams(bpaLp);
        optionsRow.addView(blockPopupAds);

        topControls.addView(optionsRow);

        status = new TextView(this);
        status.setTextSize(10);
        status.setTextColor(Color.parseColor("#6B7280"));
        topControls.addView(status);

        browserLayout.addView(topControls);

        // -----------------------------------------------------------------
        // TAB BAR
        // -----------------------------------------------------------------
        tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        tabScrollView.setBackgroundColor(Color.parseColor("#F1F5F9"));
        LinearLayout.LayoutParams tsvp = new LinearLayout.LayoutParams(-1, dp(38));
        tabScrollView.setLayoutParams(tsvp);

        tabBarContainer = new LinearLayout(this);
        tabBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabBarContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabBarContainer.setPadding(dp(6), dp(4), dp(6), dp(4));
        tabScrollView.addView(tabBarContainer, new FrameLayout.LayoutParams(-2, -1));
        browserLayout.addView(tabScrollView);

        // -----------------------------------------------------------------
        // WebView container (swappable per tab)
        // -----------------------------------------------------------------
        webViewContainer = new FrameLayout(this);
        webViewContainer.setBackgroundColor(Color.BLACK);
        browserLayout.addView(webViewContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        // Create first tab
        createNewTab(null);

        // Guard script (loaded once; applied to each new WebView via createNewTab)
        try (InputStream stream = getAssets().open("guard.js")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            guard = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}

        earlyScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);


        root.addView(browserLayout, new FrameLayout.LayoutParams(-1, -1));


        // -------------------------------------------------------------
        // FLOATING SHOW CONTROLS BUTTON (Reveals topControls when hidden)
        // -------------------------------------------------------------
        floatingShowControlsBtn = new Button(this);
        floatingShowControlsBtn.setText("▼ Buka Menu Bar");
        floatingShowControlsBtn.setTextSize(11);
        floatingShowControlsBtn.setTypeface(Typeface.DEFAULT_BOLD);
        floatingShowControlsBtn.setTextColor(Color.parseColor("#18181B"));
        floatingShowControlsBtn.setBackground(createShape(Color.WHITE, Color.parseColor("#18181B"), 2, 16));
        floatingShowControlsBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        floatingShowControlsBtn.setVisibility(View.GONE);

        FrameLayout.LayoutParams showParams = new FrameLayout.LayoutParams(-2, -2);
        showParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        showParams.topMargin = dp(10);
        root.addView(floatingShowControlsBtn, showParams);

        floatingShowControlsBtn.setOnClickListener(v -> showTopControlsBar());

        // -------------------------------------------------------------
        // FLOATING EXIT FULLSCREEN BUTTON
        // -------------------------------------------------------------
        floatingExitFsBtn = new Button(this);
        floatingExitFsBtn.setText("✕ Keluar Layar Penuh");
        floatingExitFsBtn.setTextSize(11);
        floatingExitFsBtn.setTypeface(Typeface.DEFAULT_BOLD);
        floatingExitFsBtn.setTextColor(Color.WHITE);
        floatingExitFsBtn.setBackground(createShape(Color.parseColor("#CC18181B"), Color.parseColor("#66FFFFFF"), 1, 16));
        floatingExitFsBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        floatingExitFsBtn.setVisibility(View.GONE);

        FrameLayout.LayoutParams fsParams = new FrameLayout.LayoutParams(-2, -2);
        fsParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        fsParams.topMargin = dp(12);
        root.addView(floatingExitFsBtn, fsParams);

        floatingExitFsBtn.setOnClickListener(v -> exitFullscreen());
    }

    // =============================================================
    // TAB MANAGEMENT
    // =============================================================
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createNewTab(String url) {
        if (tabs.size() >= 8) {
            Toast.makeText(this, "Maksimal 8 tab dibuka", Toast.LENGTH_SHORT).show();
            return web;
        }

        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.BLACK);

        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setSupportMultipleWindows(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // allow video src dari HTTP di HTTPS
        ws.setSafeBrowsingEnabled(false); // non-aktifkan safe browsing agar & situs sejenis bisa dibuka

        // Matikan header 'X-Requested-With' agar website (x.com / twitter / google) tidak mendeteksi WebView
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            try {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(ws, Collections.emptySet());
            } catch (Exception ignored) {}
        }

        // Gunakan User-Agent Chrome Mobile murni agar lolos deteksi bot X.com / Twitter / Arkose Labs
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");

        // Izinkan cookies & third-party cookies agar login x.com (Twitter OAuth/session) berfungsi
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        if (earlyScript && !guard.isEmpty()) {
            WebViewCompat.addDocumentStartJavaScript(wv, guard, Collections.singleton("*"));
        }

        wv.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (resultMsg != null) {
                    String currentUrl = view.getUrl();
                    boolean isAuth = isAuthOrSocialDomain(currentUrl);

                    // Jika lockHost atau blockPopupAds aktif, tolak pembukaan tab/window baru untuk situs video / iklan
                    boolean blockActive = (lockHost != null && lockHost.isChecked()) ||
                                          (blockPopupAds != null && blockPopupAds.isChecked());

                    if (blockActive && !isAuth) {
                        rejectedWindows++;
                        updateStatus();
                        return false;
                    }

                    if (isAuth || isDialog || (isUserGesture && !blockActive)) {
                        WebView newTab = createNewTab(null);
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        if (transport != null) {
                            transport.setWebView(newTab);
                            resultMsg.sendToTarget();
                            return true;
                        }
                    }
                }
                rejectedWindows++; updateStatus(); return false;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return; }
                fullscreen = view; fullscreenCallback = callback;
                browserLayout.setVisibility(View.GONE);
                root.setBackgroundColor(Color.BLACK);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                try {
                    getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                } catch (Exception ignored) {}
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                android.util.Log.d("WebConsole", "[" + cm.messageLevel() + "] " + cm.message() + " (" + cm.sourceId() + ":" + cm.lineNumber() + ")");
                return true;
            }
            @Override public void onReceivedTitle(WebView view, String title) {
                // Update tab chip title when page title changes
                int idx = tabs.indexOf(view);
                if (idx >= 0) refreshTabBar();
            }
        });

        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (!UrlPolicy.isHttp(u)) {
                    // Tolak intent iklan jika lockHost atau blockPopupAds aktif
                    if ((lockHost != null && lockHost.isChecked()) || (blockPopupAds != null && blockPopupAds.isChecked())) {
                        return true;
                    }
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }
                boolean isAuth = isAuthOrSocialDomain(u) || isAuthOrSocialDomain(view.getUrl());
                String targetHost = UrlPolicy.host(u);
                String currentHost = !selectedHost.isEmpty() ? selectedHost : UrlPolicy.host(view.getUrl());

                // KUNCI DOMAIN: Jika dicentang, tolak semua navigasi keluar dari domain utama!
                if (!isAuth && request.isForMainFrame() && lockHost != null && lockHost.isChecked()) {
                    if (!currentHost.isEmpty() && !isSameBaseDomain(currentHost, targetHost)) {
                        rejectedNavigations++;
                        updateStatus();
                        return true;
                    }
                }

                // BLOKIR IKLAN: Jika target berbeda dari domain saat ini pada frame utama, tolak redirect iklan liar
                if (!isAuth && request.isForMainFrame() && blockPopupAds != null && blockPopupAds.isChecked()) {
                    if (!currentHost.isEmpty() && !isSameBaseDomain(currentHost, targetHost)) {
                        rejectedNavigations++;
                        updateStatus();
                        return true;
                    }
                }

                return false;
            }
            @Override public void onPageStarted(WebView view, String u, Bitmap icon) {
                if (!earlyScript && !guard.isEmpty() && !isAuthOrSocialDomain(u)) {
                    view.evaluateJavascript(guard, null);
                }
                if (view == web) updateStatus();
            }
            @Override public void onPageFinished(WebView view, String u) {
                if (!earlyScript && !guard.isEmpty() && !isAuthOrSocialDomain(u)) {
                    view.evaluateJavascript(guard, null);
                }
                if (view == web) {
                    if (UrlPolicy.isHttp(u)) { address.setText(u); autoRecordHistory(u); }
                    updateStatus();
                }
                refreshTabBar();
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                // Lanjutkan meskipun ada error SSL (misal: sertifikat expired/self-signed)
                handler.proceed();
            }
        });

        tabs.add(wv);
        int newIdx = tabs.size() - 1;
        webViewContainer.addView(wv, new FrameLayout.LayoutParams(-1, -1));

        switchToTab(newIdx);

        if (url != null && !url.isEmpty()) {
            address.setText(url);
            openUrlFromAddress();
        }

        return wv;
    }

    private boolean isAuthOrSocialDomain(String u) {
        if (u == null) return false;
        String lower = u.toLowerCase(Locale.ROOT);
        return lower.contains("x.com") || lower.contains("twitter.com") ||
               lower.contains("twimg.com") || lower.contains("t.co") ||
               lower.contains("google.") || lower.contains("apple.com") ||
               lower.contains("github.com") || lower.contains("facebook.com") || lower.contains("tribunvideo.com") || lower.contains("jwplyr.com");
    }

    private boolean isSameBaseDomain(String h1, String h2) {
        if (h1 == null || h2 == null) return false;
        h1 = h1.toLowerCase(Locale.ROOT).trim();
        h2 = h2.toLowerCase(Locale.ROOT).trim();
        if (h1.isEmpty() || h2.isEmpty()) return false;
        if (h1.equals(h2)) return true;
        return h1.endsWith("." + h2) || h2.endsWith("." + h1);
    }

    private void switchToTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        activeTabIndex = idx;
        web = tabs.get(idx);

        // Show only the active WebView
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }

        // Sync address bar
        String currentUrl = web.getUrl();
        if (currentUrl != null && UrlPolicy.isHttp(currentUrl)) {
            address.setText(currentUrl);
        } else {
            address.setText("");
        }

        refreshTabBar();
        updateStatus();
    }

    private void closeTab(int idx) {
        if (tabs.size() <= 1) {
            // Last tab: clear it instead of closing
            web.loadUrl("about:blank");
            address.setText("");
            refreshTabBar();
            return;
        }
        WebView closing = tabs.get(idx);
        webViewContainer.removeView(closing);
        closing.destroy();
        tabs.remove(idx);

        int nextIdx = Math.min(idx, tabs.size() - 1);
        switchToTab(nextIdx);
    }

    private void refreshTabBar() {
        if (tabBarContainer == null) return;
        tabBarContainer.removeAllViews();

        for (int i = 0; i < tabs.size(); i++) {
            final int tabIdx = i;
            WebView wv = tabs.get(i);
            boolean isActive = (i == activeTabIndex);

            // Tab chip
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(8), 0, dp(4), 0);
            chip.setBackground(createShape(
                isActive ? Color.WHITE : Color.parseColor("#DDE3EC"),
                isActive ? Color.parseColor("#6366F1") : Color.parseColor("#B0B8C8"),
                isActive ? 2 : 1,
                10
            ));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(30));
            cp.setMargins(dp(2), 0, dp(2), 0);
            chip.setLayoutParams(cp);

            // Tab label
            TextView label = new TextView(this);
            String title = wv.getTitle();
            String url = wv.getUrl();
            String displayText;
            if (title != null && !title.isEmpty() && !title.equals("about:blank")) {
                displayText = title.length() > 14 ? title.substring(0, 13) + "…" : title;
            } else if (url != null && !url.isEmpty() && !url.equals("about:blank")) {
                String host = UrlPolicy.host(url);
                displayText = host.isEmpty() ? "Tab " + (i + 1) : (host.length() > 14 ? host.substring(0, 13) + "…" : host);
            } else {
                displayText = "Tab " + (i + 1);
            }
            label.setText(displayText);
            label.setTextSize(11);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextColor(isActive ? Color.parseColor("#18181B") : Color.parseColor("#52525B"));
            label.setPadding(0, 0, dp(4), 0);
            label.setOnClickListener(v -> switchToTab(tabIdx));
            chip.addView(label);

            // Close button on each tab
            TextView closeBtn = new TextView(this);
            closeBtn.setText("✕");
            closeBtn.setTextSize(10);
            closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
            closeBtn.setTextColor(isActive ? Color.parseColor("#6366F1") : Color.parseColor("#9CA3AF"));
            closeBtn.setPadding(dp(2), 0, dp(2), 0);
            closeBtn.setOnClickListener(v -> closeTab(tabIdx));
            chip.addView(closeBtn);

            chip.setOnClickListener(v -> switchToTab(tabIdx));
            tabBarContainer.addView(chip);
        }

        // New tab (+) button
        Button newTabBtn = new Button(this);
        newTabBtn.setText("＋");
        newTabBtn.setTextSize(14);
        newTabBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newTabBtn.setTextColor(Color.parseColor("#6366F1"));
        newTabBtn.setBackground(createShape(Color.parseColor("#EEF2FF"), Color.parseColor("#6366F1"), 1, 10));
        LinearLayout.LayoutParams nbp = new LinearLayout.LayoutParams(dp(34), dp(30));
        nbp.setMargins(dp(4), 0, dp(2), 0);
        newTabBtn.setLayoutParams(nbp);
        newTabBtn.setPadding(0, 0, 0, 0);
        newTabBtn.setOnClickListener(v -> createNewTab(null));
        tabBarContainer.addView(newTabBtn);

        // Scroll to active tab
        tabBarContainer.post(() -> {
            if (tabScrollView != null && activeTabIndex < tabBarContainer.getChildCount()) {
                View activeChip = tabBarContainer.getChildAt(activeTabIndex);
                if (activeChip != null) tabScrollView.smoothScrollTo(activeChip.getLeft(), 0);
            }
        });
    }

    private Button createModernButton(String text, int bgColor, int textColor, int strokeColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(textColor);
        btn.setBackground(createShape(bgColor, strokeColor, 1, 8));
        btn.setMinHeight(dp(34));
        btn.setMinimumHeight(dp(34));
        btn.setPadding(dp(4), 0, dp(4), 0);
        return btn;
    }

    // =============================================================
    // VIEW SWITCHING
    // =============================================================
    private void showHomeView() {
        if (fullscreen != null) exitFullscreen();
        isManualFullscreen = false;
        topControls.setVisibility(View.VISIBLE);
        floatingShowControlsBtn.setVisibility(View.GONE);
        floatingExitFsBtn.setVisibility(View.GONE);

        homeLayout.setVisibility(View.VISIBLE);
        categoryDetailLayout.setVisibility(View.GONE);
        browserLayout.setVisibility(View.GONE);

        renderCategoriesGrid();
    }

    private void showCategoryDetailView(String catId) {
        currentSelectedCatId = catId;
        homeLayout.setVisibility(View.GONE);
        categoryDetailLayout.setVisibility(View.VISIBLE);
        browserLayout.setVisibility(View.GONE);

        if (catDetailSearchInput != null) catDetailSearchInput.setText("");
        renderCategoryLinksList(catId, "");
    }

    private void showBrowserView(String targetUrl) {
        homeLayout.setVisibility(View.GONE);
        categoryDetailLayout.setVisibility(View.GONE);
        browserLayout.setVisibility(View.VISIBLE);

        if (targetUrl != null && !targetUrl.isEmpty()) {
            address.setText(targetUrl);
            openUrlFromAddress();
        }
    }

    // =============================================================
    // TOP CONTROLS HIDE & SHOW TOGGLE (User Request)
    // =============================================================
    private void hideTopControlsBar() {
        topControls.setVisibility(View.GONE);
        floatingShowControlsBtn.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Bar disembunyikan. Tap tombol atas untuk membuka kembali.", Toast.LENGTH_SHORT).show();
    }

    private void showTopControlsBar() {
        topControls.setVisibility(View.VISIBLE);
        floatingShowControlsBtn.setVisibility(View.GONE);
    }

    // =============================================================
    // FULLSCREEN VIDEO (No rotation, strictly vertical, hide inputs)
    // =============================================================
    private void enterFullscreen() {
        isManualFullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        topControls.setVisibility(View.GONE);
        floatingShowControlsBtn.setVisibility(View.GONE);
        floatingExitFsBtn.setVisibility(View.VISIBLE);

        String js = "(function() {" +
            "function tryFs(elem) {" +
            "  if (!elem) return false;" +
            "  var m = ['requestFullscreen', 'webkitRequestFullscreen', 'webkitEnterFullscreen', 'mozRequestFullScreen', 'msRequestFullscreen'];" +
            "  for (var i = 0; i < m.length; i++) {" +
            "    if (typeof elem[m[i]] === 'function') { try { elem[m[i]](); return true; } catch(e){} }" +
            "  }" +
            "  return false;" +
            "}" +
            "var v = Array.from(document.querySelectorAll('video')).find(function(item){ return !item.paused; }) || document.querySelector('video');" +
            "if (v && tryFs(v)) return;" +
            "var iframes = document.querySelectorAll('iframe');" +
            "for (var j = 0; j < iframes.length; j++) {" +
            "  try {" +
            "    var doc = iframes[j].contentDocument || iframes[j].contentWindow.document;" +
            "    if (doc) {" +
            "      var iv = Array.from(doc.querySelectorAll('video')).find(function(item){ return !item.paused; }) || doc.querySelector('video');" +
            "      if (iv && tryFs(iv)) return;" +
            "    }" +
            "  } catch(e) {}" +
            "}" +
            "tryFs(document.documentElement);" +
            "})();";
        web.evaluateJavascript(js, null);
    }

    private void exitFullscreen() {
        isManualFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        topControls.setVisibility(View.VISIBLE);
        floatingShowControlsBtn.setVisibility(View.GONE);
        floatingExitFsBtn.setVisibility(View.GONE);

        if (fullscreen != null) {
            root.removeView(fullscreen);
            fullscreen = null;
            browserLayout.setVisibility(View.VISIBLE);
            root.setBackgroundColor(Color.parseColor("#F8F9FA"));
            if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }

        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } catch (Exception ignored) {}
    }

    // =============================================================
    // URL HANDLING
    // =============================================================
    private void openUrlFromAddress() {
        String url = address.getText().toString().trim();
        if (url.isEmpty()) return;
        url = UrlPolicy.ensureHttps(url);
        address.setText(url);
        address.setSelection(url.length());

        if (!UrlPolicy.isHttp(url)) {
            address.setError("Masukkan URL valid");
            return;
        }
        selectedHost = UrlPolicy.host(url);
        getPreferences(0).edit().putString("lastUrl", url).apply();
        web.stopLoading();
        web.loadUrl(url);

        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
        web.requestFocus();
    }

    private void handleQuickSaveClick() {
        String inputRaw = homeLinkInput.getText().toString().trim();
        if (inputRaw.isEmpty()) return;
        final String raw = UrlPolicy.ensureHttps(inputRaw);
        homeLinkInput.setText(raw);
        homeLinkInput.setSelection(raw.length());

        if (!UrlPolicy.isHttp(raw)) {
            Toast.makeText(this, "Masukkan URL valid", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray cats = getCategoriesArray();
        String[] catNames = new String[cats.length() + 1];
        catNames[0] = "▶ Buka di Browser Sekarang";
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            catNames[i + 1] = "Simpan ke: " + (c != null ? c.optString("icon", "") + " " + c.optString("name", "") : "");
        }

        new AlertDialog.Builder(this)
            .setTitle("Tautan Video Ditemukan")
            .setItems(catNames, (d, which) -> {
                if (which == 0) {
                    showBrowserView(raw);
                } else {
                    JSONObject selectedCat = cats.optJSONObject(which - 1);
                    if (selectedCat != null) {
                        addLinkToCategory(selectedCat.optString("id"), raw);
                        homeLinkInput.setText("");
                        renderCategoriesGrid();
                        Toast.makeText(MainActivity.this, "Tersimpan ke " + selectedCat.optString("name"), Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void saveCurrentUrlToCategory() {
        String currentUrl = address.getText().toString().trim();
        if (!UrlPolicy.isHttp(currentUrl)) {
            Toast.makeText(this, "Tidak ada URL valid untuk disimpan", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray cats = getCategoriesArray();
        String[] catNames = new String[cats.length()];
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            catNames[i] = c != null ? c.optString("icon", "") + " " + c.optString("name", "") : "";
        }

        new AlertDialog.Builder(this)
            .setTitle("Pilih Kategori Penyimpanan")
            .setItems(catNames, (d, which) -> {
                JSONObject selectedCat = cats.optJSONObject(which);
                if (selectedCat != null) {
                    addLinkToCategory(selectedCat.optString("id"), currentUrl);
                    Toast.makeText(MainActivity.this, "Tautan berhasil disimpan ke " + selectedCat.optString("name"), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    // =============================================================
    // DATA LAYER (Categories & Links Storage)
    // =============================================================
    private void initDefaultCategoriesIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("popup_guard_collections", Context.MODE_PRIVATE);
        if (!prefs.contains("categories")) {
            JSONArray arr = new JSONArray();
            arr.put(createCategoryJson("cat_tvshows", "Tvshows", "📺", "#FDE047"));
            arr.put(createCategoryJson("cat_readlater", "Read later", "📖", "#86EFAC"));
            arr.put(createCategoryJson("cat_dating", "Dating spots", "❤️", "#FCA5A5"));
            arr.put(createCategoryJson("cat_fitness", "Stay in shape", "🏋️", "#E5E7EB"));
            prefs.edit().putString("categories", arr.toString()).apply();
        }
    }

    private JSONObject createCategoryJson(String id, String name, String icon, String color) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("icon", icon);
            obj.put("color", color);
            obj.put("links", new JSONArray());
        } catch (Exception ignored) {}
        return obj;
    }

    private JSONArray getCategoriesArray() {
        SharedPreferences prefs = getSharedPreferences("popup_guard_collections", Context.MODE_PRIVATE);
        String raw = prefs.getString("categories", "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveCategoriesArray(JSONArray arr) {
        SharedPreferences prefs = getSharedPreferences("popup_guard_collections", Context.MODE_PRIVATE);
        prefs.edit().putString("categories", arr.toString()).apply();
    }

    private JSONObject getCategoryById(String catId) {
        JSONArray arr = getCategoriesArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && catId.equals(obj.optString("id"))) return obj;
        }
        return null;
    }

    private void addLinkToCategory(String catId, String url) {
        JSONArray arr = getCategoriesArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && catId.equals(obj.optString("id"))) {
                JSONArray links = obj.optJSONArray("links");
                if (links == null) {
                    links = new JSONArray();
                    try { obj.put("links", links); } catch (Exception ignored) {}
                }
                // Avoid immediate duplicate
                if (links.length() > 0 && url.equals(links.optJSONObject(0).optString("url"))) {
                    return;
                }
                JSONObject newLink = new JSONObject();
                try {
                    newLink.put("url", url);
                    newLink.put("time", System.currentTimeMillis());
                } catch (Exception ignored) {}

                // Prepend
                JSONArray updated = new JSONArray();
                updated.put(newLink);
                for (int j = 0; j < links.length() && j < 200; j++) {
                    if (!url.equals(links.optJSONObject(j).optString("url"))) {
                        updated.put(links.optJSONObject(j));
                    }
                }
                try { obj.put("links", updated); } catch (Exception ignored) {}
                break;
            }
        }
        saveCategoriesArray(arr);
    }

    private void deleteLinkFromCategory(String catId, int linkIndex) {
        JSONArray arr = getCategoriesArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && catId.equals(obj.optString("id"))) {
                JSONArray links = obj.optJSONArray("links");
                if (links != null && linkIndex >= 0 && linkIndex < links.length()) {
                    links.remove(linkIndex);
                    saveCategoriesArray(arr);
                    renderCategoryLinksList(catId, catDetailSearchInput != null ? catDetailSearchInput.getText().toString() : "");
                    Toast.makeText(this, "Tautan berhasil dihapus", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
    }

    private void autoRecordHistory(String url) {
        // Automatically save to Tvshows or first category as history
        JSONArray arr = getCategoriesArray();
        if (arr.length() > 0) {
            JSONObject first = arr.optJSONObject(0);
            if (first != null) {
                addLinkToCategory(first.optString("id"), url);
            }
        }
    }

    private void showAddCategoryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView prompt = new TextView(this);
        prompt.setText("Nama Kategori Baru:");
        prompt.setPadding(0, 0, 0, dp(6));
        layout.addView(prompt);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Misal: Anime, Kursus, Olahraga");
        nameInput.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
        nameInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        layout.addView(nameInput);

        TextView iconPrompt = new TextView(this);
        iconPrompt.setText("Pilih Icon / Warna:");
        iconPrompt.setPadding(0, dp(12), 0, dp(6));
        layout.addView(iconPrompt);

        String[] presetIcons = {"📺", "🎬", "🍿", "🎵", "⚽", "📚", "💼", "🎮", "❤️", "⭐"};
        String[] presetColors = {"#FDE047", "#86EFAC", "#FCA5A5", "#93C5FD", "#C4B5FD", "#FDBA74"};

        new AlertDialog.Builder(this)
            .setTitle("Tambah Kategori")
            .setView(layout)
            .setPositiveButton("Simpan", (d, w) -> {
                String catName = nameInput.getText().toString().trim();
                if (catName.isEmpty()) return;

                JSONArray arr = getCategoriesArray();
                String newId = "cat_" + System.currentTimeMillis();
                String icon = presetIcons[(int) (Math.random() * presetIcons.length)];
                String color = presetColors[(int) (Math.random() * presetColors.length)];

                arr.put(createCategoryJson(newId, catName, icon, color));
                saveCategoriesArray(arr);
                renderCategoriesGrid();
                Toast.makeText(MainActivity.this, "Kategori " + catName + " dibuat!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showAddLinkToCurrentCategoryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        EditText linkInput = new EditText(this);
        linkInput.setHint("https://situs-video.com/video");
        linkInput.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
        linkInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        layout.addView(linkInput);

        new AlertDialog.Builder(this)
            .setTitle("Tambah Link ke Kategori")
            .setView(layout)
            .setPositiveButton("Simpan", (d, w) -> {
                String url = linkInput.getText().toString().trim();
                if (url.isEmpty()) return;
                url = UrlPolicy.ensureHttps(url);
                if (!UrlPolicy.isHttp(url)) {
                    Toast.makeText(MainActivity.this, "URL tidak valid", Toast.LENGTH_SHORT).show();
                    return;
                }
                addLinkToCategory(currentSelectedCatId, url);
                renderCategoryLinksList(currentSelectedCatId, "");
                Toast.makeText(MainActivity.this, "Link berhasil disimpan!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void confirmDeleteCategory(String catId, String name) {
        new AlertDialog.Builder(this)
            .setTitle("Hapus Kategori?")
            .setMessage("Kategori '" + name + "' beserta tautan di dalamnya akan dihapus.")
            .setPositiveButton("Hapus", (d, w) -> {
                JSONArray arr = getCategoriesArray();
                JSONArray updated = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null && !catId.equals(obj.optString("id"))) {
                        updated.put(obj);
                    }
                }
                saveCategoriesArray(updated);
                renderCategoriesGrid();
                Toast.makeText(MainActivity.this, "Kategori dihapus", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    // =============================================================
    // PASSWORD PROTECTION (Accepts "11" as modified by user, and "sayasiapa11")
    // =============================================================
    private void promptPasswordThenOpenCategory(String catId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Kunci Riwayat Link");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView prompt = new TextView(this);
        prompt.setText("Masukkan password untuk membuka kategori:");
        prompt.setPadding(0, 0, 0, dp(8));
        prompt.setTextColor(Color.parseColor("#374151"));
        layout.addView(prompt);

        EditText passInput = new EditText(this);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setHint("Password");
        passInput.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
        passInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.addView(passInput);

        builder.setView(layout);
        builder.setPositiveButton("Buka", (dialog, which) -> {
            String entered = passInput.getText().toString().trim();
            if ("11".equals(entered) || "sayasiapa11".equals(entered)) {
                showCategoryDetailView(catId);
            } else {
                Toast.makeText(MainActivity.this, "❌ Password salah!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void updateStatus() {
        if (status != null) {
            status.setText("Jendela ditolak: " + rejectedWindows + " · Navigasi ditolak: " +
                rejectedNavigations + "\n" + (earlyScript ? "JavaScript awal aktif." :
                "WebView lama: perlindungan native aktif."));
        }
    }

    @Override public void onBackPressed() {
        if (fullscreen != null || isManualFullscreen) {
            exitFullscreen();
        } else if (categoryDetailLayout != null && categoryDetailLayout.getVisibility() == View.VISIBLE) {
            showHomeView();
        } else if (browserLayout != null && browserLayout.getVisibility() == View.VISIBLE) {
            if (web != null && web.canGoBack()) {
                web.goBack();
            } else {
                showHomeView();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override protected void onDestroy() {
        exitFullscreen();
        if (web != null) {
            browserLayout.removeView(web);
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
        }
        super.onDestroy();
    }

    // =============================================================
    // DNS & PROXY CONFIGURATION (Bypass Internet Positif / ISP block)
    // =============================================================
    private void showDnsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚡ Pengaturan DNS & Anti-Blokir");

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(10), dp(18), dp(10));
        scroll.addView(layout);

        // Section 1: Android System Private DNS (Best method)
        TextView sec1 = new TextView(this);
        sec1.setText("1. Rekomendasi DNS Bebas Blokir");
        sec1.setTypeface(Typeface.DEFAULT_BOLD);
        sec1.setTextSize(13);
        sec1.setTextColor(Color.parseColor("#18181B"));
        layout.addView(sec1);

        TextView desc1 = new TextView(this);
        desc1.setText("Salin salah satu hostname berikut, lalu buka Pengaturan HP untuk mengaktifkan Private DNS:");
        desc1.setTextSize(11);
        desc1.setTextColor(Color.parseColor("#6B7280"));
        desc1.setPadding(0, dp(2), 0, dp(10));
        layout.addView(desc1);

        String[][] presets = {
            {"one.one.one.one", "⚡ Cloudflare (Cepat & Stabil)"},
            {"dns.adguard-dns.com", "🛡️ AdGuard (Anti-Iklan Tingkat DNS)"},
            {"dns.google", "🔍 Google Public DNS"},
            {"p2.freedns.controld.com", "🚀 Control D (Bypass Sensor)"}
        };

        for (String[] preset : presets) {
            String host = preset[0];
            String label = preset[1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(createShape(Color.parseColor("#F9FAFB"), Color.parseColor("#E5E7EB"), 1, 10));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(6);
            row.setLayoutParams(rp);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView lbl = new TextView(this);
            lbl.setText(label);
            lbl.setTextSize(11);
            lbl.setTypeface(Typeface.DEFAULT_BOLD);
            lbl.setTextColor(Color.parseColor("#18181B"));
            textCol.addView(lbl);

            TextView val = new TextView(this);
            val.setText(host);
            val.setTextSize(11);
            val.setTextColor(Color.parseColor("#4B5563"));
            textCol.addView(val);

            row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

            Button copyBtn = new Button(this);
            copyBtn.setText("Salin");
            copyBtn.setTextSize(10);
            copyBtn.setTypeface(Typeface.DEFAULT_BOLD);
            copyBtn.setTextColor(Color.parseColor("#18181B"));
            copyBtn.setBackground(createShape(Color.parseColor("#FEF08A"), Color.parseColor("#CA8A04"), 1, 6));
            copyBtn.setMinHeight(dp(28));
            copyBtn.setMinimumHeight(dp(28));
            copyBtn.setPadding(dp(10), 0, dp(10), 0);
            copyBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("DNS", host));
                    Toast.makeText(MainActivity.this, "Hostname '" + host + "' disalin!", Toast.LENGTH_SHORT).show();
                }
            });
            row.addView(copyBtn);

            layout.addView(row);
        }

        Button openSettingsBtn = new Button(this);
        openSettingsBtn.setText("⚙️ Buka Pengaturan Private DNS di HP");
        openSettingsBtn.setTextSize(12);
        openSettingsBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openSettingsBtn.setTextColor(Color.parseColor("#18181B"));
        openSettingsBtn.setBackground(createShape(Color.parseColor("#FCD34D"), Color.parseColor("#18181B"), 2, 10));
        openSettingsBtn.setMinHeight(dp(38));
        openSettingsBtn.setMinimumHeight(dp(38));
        LinearLayout.LayoutParams obp = new LinearLayout.LayoutParams(-1, -2);
        obp.topMargin = dp(6);
        obp.bottomMargin = dp(16);
        openSettingsBtn.setLayoutParams(obp);
        openSettingsBtn.setOnClickListener(v -> openAndroidPrivateDnsSettings());
        layout.addView(openSettingsBtn);

        // Section 2: In-App Proxy / SOCKS5 Input (ProxyController)
        TextView sec2 = new TextView(this);
        sec2.setText("2. Custom Proxy / SOCKS5 Aplikasi");
        sec2.setTypeface(Typeface.DEFAULT_BOLD);
        sec2.setTextSize(13);
        sec2.setTextColor(Color.parseColor("#18181B"));
        layout.addView(sec2);

        TextView desc2 = new TextView(this);
        desc2.setText("Atau arahkan browser langsung ke server proxy / WARP lokal:");
        desc2.setTextSize(11);
        desc2.setTextColor(Color.parseColor("#6B7280"));
        desc2.setPadding(0, dp(2), 0, dp(8));
        layout.addView(desc2);

        SharedPreferences prefs = getSharedPreferences("popup_guard_proxy", Context.MODE_PRIVATE);
        String currentProxy = prefs.getString("proxy_rule", "");

        EditText proxyInput = new EditText(this);
        proxyInput.setHint("contoh: socks5://127.0.0.1:40000");
        proxyInput.setText(currentProxy);
        proxyInput.setTextSize(12);
        proxyInput.setTextColor(Color.parseColor("#1F2937"));
        proxyInput.setBackground(createShape(Color.parseColor("#F3F4F6"), Color.parseColor("#D1D5DB"), 1, 8));
        proxyInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        layout.addView(proxyInput);

        LinearLayout proxyBtnRow = new LinearLayout(this);
        proxyBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        proxyBtnRow.setPadding(0, dp(8), 0, dp(4));

        Button applyProxyBtn = new Button(this);
        applyProxyBtn.setText("Terapkan Proxy");
        applyProxyBtn.setTextSize(11);
        applyProxyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        applyProxyBtn.setTextColor(Color.WHITE);
        applyProxyBtn.setBackground(createShape(Color.parseColor("#18181B"), Color.parseColor("#18181B"), 1, 8));
        applyProxyBtn.setMinHeight(dp(34));
        applyProxyBtn.setMinimumHeight(dp(34));
        applyProxyBtn.setOnClickListener(v -> {
            String pRule = proxyInput.getText().toString().trim();
            applyCustomProxy(pRule);
        });
        proxyBtnRow.addView(applyProxyBtn, new LinearLayout.LayoutParams(0, -2, 1));

        Button resetProxyBtn = new Button(this);
        resetProxyBtn.setText("Reset");
        resetProxyBtn.setTextSize(11);
        resetProxyBtn.setTextColor(Color.parseColor("#DC2626"));
        resetProxyBtn.setBackground(createShape(Color.parseColor("#FEE2E2"), Color.parseColor("#FCA5A5"), 1, 8));
        resetProxyBtn.setMinHeight(dp(34));
        resetProxyBtn.setMinimumHeight(dp(34));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(70), dp(34));
        rlp.leftMargin = dp(8);
        resetProxyBtn.setLayoutParams(rlp);
        resetProxyBtn.setOnClickListener(v -> {
            proxyInput.setText("");
            applyCustomProxy("");
        });
        proxyBtnRow.addView(resetProxyBtn);

        layout.addView(proxyBtnRow);

        builder.setView(scroll);
        builder.setPositiveButton("Tutup", null);
        builder.show();
    }

    private void openAndroidPrivateDnsSettings() {
        Intent[] intents = {
            new Intent("android.settings.NETWORK_PROVIDER_SETTINGS"),
            new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
            new Intent(android.provider.Settings.ACTION_SETTINGS)
        };
        for (Intent it : intents) {
            try {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return;
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Buka Pengaturan HP > Koneksi > DNS Pribadi", Toast.LENGTH_LONG).show();
    }

    private void applyCustomProxy(String proxyRule) {
        SharedPreferences prefs = getSharedPreferences("popup_guard_proxy", Context.MODE_PRIVATE);
        if (proxyRule == null || proxyRule.isEmpty()) {
            prefs.edit().remove("proxy_rule").apply();
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(Runnable::run, () -> {
                    Toast.makeText(MainActivity.this, "Proxy dinonaktifkan (kembali ke default)", Toast.LENGTH_SHORT).show();
                });
            }
            return;
        }

        prefs.edit().putString("proxy_rule", proxyRule).apply();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(proxyRule)
                    .build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, Runnable::run, () -> {
                    Toast.makeText(MainActivity.this, "Proxy aktif: " + proxyRule, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Toast.makeText(this, "Format proxy salah: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Perangkat ini tidak mendukung in-app ProxyOverride.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedProxyOnStartup() {
        SharedPreferences prefs = getSharedPreferences("popup_guard_proxy", Context.MODE_PRIVATE);
        String savedProxy = prefs.getString("proxy_rule", "");
        if (!savedProxy.isEmpty()) {
            applyCustomProxy(savedProxy);
        }
    }

    // =============================================================
    // CLEAR CACHE & HISTORY
    // =============================================================
    private void showClearCacheAndHistoryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView desc = new TextView(this);
        desc.setText("Pilih data yang ingin dibersihkan dari browser:");
        desc.setTextColor(Color.parseColor("#4B5563"));
        desc.setTextSize(13);
        desc.setPadding(0, 0, 0, dp(12));
        layout.addView(desc);

        CheckBox cbCache = new CheckBox(this);
        cbCache.setText("Cache Web & Data Offline");
        cbCache.setChecked(true);
        cbCache.setTextSize(13);
        cbCache.setTextColor(Color.parseColor("#1F2937"));
        layout.addView(cbCache);

        CheckBox cbHistory = new CheckBox(this);
        cbHistory.setText("Riwayat Tab & Form Navigasi");
        cbHistory.setChecked(true);
        cbHistory.setTextSize(13);
        cbHistory.setTextColor(Color.parseColor("#1F2937"));
        layout.addView(cbHistory);

        CheckBox cbCookies = new CheckBox(this);
        cbCookies.setText("Cookies & Sesi Web");
        cbCookies.setChecked(true);
        cbCookies.setTextSize(13);
        cbCookies.setTextColor(Color.parseColor("#1F2937"));
        layout.addView(cbCookies);

        CheckBox cbSaved = new CheckBox(this);
        cbSaved.setText("Riwayat Otomatis Link Kategori (Tvshows)");
        cbSaved.setChecked(false);
        cbSaved.setTextSize(13);
        cbSaved.setTextColor(Color.parseColor("#DC2626"));
        layout.addView(cbSaved);

        new AlertDialog.Builder(this)
            .setTitle("🧹 Bersihkan Cache & Riwayat")
            .setView(layout)
            .setPositiveButton("Bersihkan", (d, w) -> {
                performClearBrowserData(cbCache.isChecked(), cbHistory.isChecked(), cbCookies.isChecked(), cbSaved.isChecked());
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void performClearBrowserData(boolean clearCache, boolean clearHistory, boolean clearCookies, boolean clearSavedHistory) {
        try {
            if (clearCache) {
                for (WebView wv : tabs) {
                    wv.clearCache(true);
                }
                WebStorage.getInstance().deleteAllData();
                deleteDir(getCacheDir());
                deleteDir(getCodeCacheDir());
            }

            if (clearHistory) {
                for (WebView wv : tabs) {
                    wv.clearHistory();
                    wv.clearFormData();
                }
                getPreferences(0).edit().remove("lastUrl").apply();
                address.setText("");
                refreshTabBar();
            }

            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
            }

            if (clearSavedHistory) {
                JSONArray arr = getCategoriesArray();
                if (arr.length() > 0) {
                    JSONObject first = arr.optJSONObject(0);
                    if (first != null) {
                        first.put("links", new JSONArray());
                        SharedPreferences prefs = getSharedPreferences("popup_guard_collections", Context.MODE_PRIVATE);
                        prefs.edit().putString("categories", arr.toString()).apply();
                        renderCategoriesGrid();
                    }
                }
            }

            Toast.makeText(this, "✅ Berhasil membersihkan cache dan riwayat!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Selesai dibersihkan.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new java.io.File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }
}

