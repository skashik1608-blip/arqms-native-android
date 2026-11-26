package com.ausgram1.arqms.nativeapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeDashboard, swipeRefunds;
    private LinearLayout dashContainer, tabRefunds, tabSearch, searchResultsContainer;
    private LinearLayout tabModules, moduleSubHeader, modulesContainer;
    private RecyclerView rvRefunds;
    private RefundCardAdapter refundAdapter;
    private EditText etRefundSearch, etGlobalSearch;
    private Button btnFilterAll, btnFilterPending, btnFilterCompleted, btnModuleBack, btnModuleRefresh;
    private TextView tvRefundCount, tvTopUser, tvModuleTitle;
    private ProgressBar pbSearch, pbModule;

    private String activeFilter = "all";
    private String currentModuleKey = "hub";
    private String pendingUploadSlNo = "";
    private String pendingUploadDocName = "Bank Passbook";

    private static final String[] DOC_NAMES = new String[]{
            "Death Certificate", "Refund Letter", "Bank Passbook", "Bank Ac Statement",
            "Refund Deposit Receipt Copy", "Portal Form -A", "Bharatkosh Challan",
            "Aadhaar Card", "Voter ID Card", "Land ROR - Deed", "GP Warishian",
            "Application", "UTR - NEFT - RTGS Slip", "Bank NOC - Certificate"
    };

    private static final String[] AUSGRAM1_GPS = new String[]{
            "Ausgram", "Berenda", "Billagram", "Dignagar-I", "Dignagar-II",
            "Guskara Municipality", "Mankar", "Ukta"
    };

    private static final String[] SCHEMES = new String[]{
            "PM-KISAN", "Krishak Bandhu (Natun)", "General / Other"
    };

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && !pendingUploadSlNo.isEmpty()) {
                    uploadSelectedUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionStore.token == null || SessionStore.token.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        tvTopUser = findViewById(R.id.tvTopUser);
        tvTopUser.setText(SessionStore.name + " (" + SessionStore.role + ") • " + SessionStore.deviceLabel);
        findViewById(R.id.btnLogoutTop).setOnClickListener(v -> doLogout());

        swipeDashboard = findViewById(R.id.swipeDashboard);
        dashContainer = findViewById(R.id.dashContainer);
        tabRefunds = findViewById(R.id.tabRefunds);
        tabSearch = findViewById(R.id.tabSearch);
        tabModules = findViewById(R.id.tabModules);
        moduleSubHeader = findViewById(R.id.moduleSubHeader);
        modulesContainer = findViewById(R.id.modulesContainer);
        btnModuleBack = findViewById(R.id.btnModuleBack);
        btnModuleRefresh = findViewById(R.id.btnModuleRefresh);
        tvModuleTitle = findViewById(R.id.tvModuleTitle);
        pbModule = findViewById(R.id.pbModule);

        swipeRefunds = findViewById(R.id.swipeRefunds);
        rvRefunds = findViewById(R.id.rvRefunds);
        etRefundSearch = findViewById(R.id.etRefundSearch);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterPending = findViewById(R.id.btnFilterPending);
        btnFilterCompleted = findViewById(R.id.btnFilterCompleted);
        tvRefundCount = findViewById(R.id.tvRefundCount);

        etGlobalSearch = findViewById(R.id.etGlobalSearch);
        pbSearch = findViewById(R.id.pbSearch);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);

        rvRefunds.setLayoutManager(new LinearLayoutManager(this));
        refundAdapter = new RefundCardAdapter(new RefundCardAdapter.CardListener() {
            @Override public void onCall(JSONObject item) { dialNumber(item.optString("mobile", "")); }
            @Override public void onUpdateUtr(JSONObject item) { showQuickUtrDialog(item); }
            @Override public void onUploadDoc(JSONObject item) { showDocUploadPicker(item); }
            @Override public void onOpenDrive(JSONObject item) {
                String url = item.optString("folderUrl", "");
                if (url.startsWith("http")) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                else Toast.makeText(MainActivity.this, "No Drive folder yet. Tap Upload Doc.", Toast.LENGTH_SHORT).show();
            }
        });
        rvRefunds.setAdapter(refundAdapter);

        swipeDashboard.setOnRefreshListener(this::loadDashboard);
        swipeRefunds.setOnRefreshListener(this::loadRefunds);
        findViewById(R.id.btnRefundSearchGo).setOnClickListener(v -> loadRefunds());
        btnFilterAll.setOnClickListener(v -> setRefundFilter("all"));
        btnFilterPending.setOnClickListener(v -> setRefundFilter("pending"));
        btnFilterCompleted.setOnClickListener(v -> setRefundFilter("completed"));
        findViewById(R.id.btnGlobalSearchGo).setOnClickListener(v -> runSmartSearch());

        btnModuleBack.setOnClickListener(v -> showModulesHub());
        btnModuleRefresh.setOnClickListener(v -> openModule(currentModuleKey));

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            swipeDashboard.setVisibility(id == R.id.nav_dashboard ? View.VISIBLE : View.GONE);
            tabRefunds.setVisibility(id == R.id.nav_refunds ? View.VISIBLE : View.GONE);
            tabSearch.setVisibility(id == R.id.nav_search ? View.VISIBLE : View.GONE);
            tabModules.setVisibility(id == R.id.nav_modules ? View.VISIBLE : View.GONE);
            if (id == R.id.nav_refunds && refundAdapter.getItemCount() == 0) loadRefunds();
            if (id == R.id.nav_modules) showModulesHub();
            return true;
        });

        loadDashboard();
    }

    private void dialNumber(String raw) {
        String mob = raw.replaceAll("\\D", "");
        if (mob.length() < 10) {
            Toast.makeText(this, "No valid mobile number.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + mob)));
    }

    private void handleSessionExpiredIfNeeded(JSONObject json) {
        if (json != null && json.optBoolean("sessionExpired", false)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            SessionStore.clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    // ==================== TAB 1: DASHBOARD ====================
    private void loadDashboard() {
        swipeDashboard.setRefreshing(true);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "dashboard");
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    swipeDashboard.setRefreshing(false);
                    if (!json.optBoolean("ok", false)) { handleSessionExpiredIfNeeded(json); return; }
                    renderDashboard(json);
                }
                @Override public void onError(String message) {
                    swipeDashboard.setRefreshing(false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) { swipeDashboard.setRefreshing(false); }
    }

    private void renderDashboard(JSONObject json) {
        dashContainer.removeAllViews();
        JSONObject st = json.optJSONObject("stats");
        if (st == null) return;

        addInfoCard(dashContainer, "Total Refund Cases", String.valueOf(st.optInt("total")),
                "Completed: " + st.optInt("completed") + "  |  Pending: " + st.optInt("pending") + "  |  Exempted: " + st.optInt("exempted"), "#004D28");
        addInfoCard(dashContainer, "Recovery Amount", "Rs " + st.optInt("recoveredAmt"),
                "Total Target Amount: Rs " + st.optInt("totalAmt"), "#0369A1");
        addInfoCard(dashContainer, "Live Trackers",
                "Queries: " + st.optInt("farmerQueries") + "  •  Approval Gate: " + st.optInt("approvalGate"),
                "Joint A/c: " + st.optInt("jointAccounts") + "  |  Suspected Deceased: " + st.optInt("suspectedDeceased"), "#B45309");

        TextView hdr = new TextView(this);
        hdr.setText("GP-Wise Pending Breakdown");
        hdr.setTextSize(15f);
        hdr.setTypeface(null, Typeface.BOLD);
        hdr.setTextColor(Color.parseColor("#0F172A"));
        hdr.setPadding(4, 16, 4, 8);
        dashContainer.addView(hdr);

        JSONArray gps = json.optJSONArray("gpSummary");
        if (gps != null) {
            for (int i = 0; i < gps.length(); i++) {
                JSONObject g = gps.optJSONObject(i);
                if (g == null) continue;
                addInfoCard(dashContainer, g.optString("gp"), "Pending: " + g.optInt("pending"),
                        "Total: " + g.optInt("total") + "  |  Completed: " + g.optInt("completed"), "#334155");
            }
        }
    }

    private CardView addInfoCard(LinearLayout parent, String title, String bigVal, String sub, String accentHex) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 20;
        card.setLayoutParams(lp);
        card.setRadius(22f);
        card.setCardElevation(5f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(34, 26, 34, 26);

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextSize(12.5f);
        tvT.setTypeface(null, Typeface.BOLD);
        tvT.setTextColor(Color.parseColor(accentHex));

        TextView tvV = new TextView(this);
        tvV.setText(bigVal);
        tvV.setTextSize(18f);
        tvV.setTypeface(null, Typeface.BOLD);
        tvV.setTextColor(Color.parseColor("#0F172A"));

        TextView tvS = new TextView(this);
        tvS.setText(sub);
        tvS.setTextSize(12.5f);
        tvS.setTextColor(Color.parseColor("#475569"));

        inner.addView(tvT);
        inner.addView(tvV);
        inner.addView(tvS);
        card.addView(inner);
        parent.addView(card);
        return card;
    }

    // ==================== TAB 2: REFUNDS ====================
    private void setRefundFilter(String f) {
        activeFilter = f;
        int green = Color.parseColor("#004D28");
        int gray = Color.parseColor("#E2E8F0");
        int dark = Color.parseColor("#0F172A");
        btnFilterAll.setBackgroundColor(f.equals("all") ? green : gray);
        btnFilterAll.setTextColor(f.equals("all") ? Color.WHITE : dark);
        btnFilterPending.setBackgroundColor(f.equals("pending") ? green : gray);
        btnFilterPending.setTextColor(f.equals("pending") ? Color.WHITE : dark);
        btnFilterCompleted.setBackgroundColor(f.equals("completed") ? green : gray);
        btnFilterCompleted.setTextColor(f.equals("completed") ? Color.WHITE : dark);
        loadRefunds();
    }

    private void loadRefunds() {
        swipeRefunds.setRefreshing(true);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "list_refunds");
            req.put("filter", activeFilter);
            req.put("query", etRefundSearch.getText().toString().trim());
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    swipeRefunds.setRefreshing(false);
                    if (!json.optBoolean("ok", false)) { handleSessionExpiredIfNeeded(json); return; }
                    int tot = json.optInt("totalMatched", 0);
                    JSONArray arr = json.optJSONArray("items");
                    List<JSONObject> list = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o != null) list.add(o);
                        }
                    }
                    tvRefundCount.setText("Showing " + list.size() + " of " + tot + " records (" + activeFilter.toUpperCase() + ")");
                    refundAdapter.setItems(list);
                }
                @Override public void onError(String message) {
                    swipeRefunds.setRefreshing(false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) { swipeRefunds.setRefreshing(false); }
    }

    private void showQuickUtrDialog(JSONObject item) {
        String slNo = item.optString("slNo", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 10);
        EditText etUtr = new EditText(this);
        etUtr.setHint("UTR / Transaction No.");
        etUtr.setText(item.optString("utr", ""));
        box.addView(etUtr);
        EditText etDate = new EditText(this);
        etDate.setHint("Refund Date (dd-MM-yyyy)");
        String curDate = item.optString("refundDate", "");
        if (curDate.isEmpty()) curDate = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        etDate.setText(curDate);
        box.addView(etDate);

        new AlertDialog.Builder(this)
                .setTitle("Update UTR — SL #" + slNo)
                .setView(box)
                .setPositiveButton("Save Live", (d, w) -> {
                    try {
                        JSONObject req = new JSONObject();
                        req.put("action", "quick_utr");
                        req.put("slNo", slNo);
                        req.put("utr", etUtr.getText().toString().trim());
                        req.put("refundDate", etDate.getText().toString().trim());
                        ApiClient.post(req, new ApiClient.Callback() {
                            @Override public void onResult(JSONObject json) {
                                Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_SHORT).show();
                                if (json.optBoolean("ok", false)) loadRefunds();
                            }
                            @Override public void onError(String message) { Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show(); }
                        });
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDocUploadPicker(JSONObject item) {
        pendingUploadSlNo = item.optString("slNo", "");
        new AlertDialog.Builder(this)
                .setTitle("Select Document Name (SL #" + pendingUploadSlNo + ")")
                .setItems(DOC_NAMES, (d, which) -> {
                    pendingUploadDocName = DOC_NAMES[which];
                    pickImageLauncher.launch("image/*");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadSelectedUri(Uri uri) {
        Toast.makeText(this, "Compressing & uploading " + pendingUploadDocName + "...", Toast.LENGTH_SHORT).show();
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bmp == null) return;
            int maxDim = 1400;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w > maxDim || h > maxDim) {
                float sc = Math.min((float) maxDim / w, (float) maxDim / h);
                bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * sc), Math.round(h * sc), true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 78, baos);
            JSONObject req = new JSONObject();
            req.put("action", "upload_doc");
            req.put("slNo", pendingUploadSlNo);
            req.put("docName", pendingUploadDocName);
            req.put("mimeType", "image/jpeg");
            req.put("base64Data", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_LONG).show();
                    if (json.optBoolean("ok", false)) loadRefunds();
                }
                @Override public void onError(String message) { Toast.makeText(MainActivity.this, "Upload failed: " + message, Toast.LENGTH_LONG).show(); }
            });
        } catch (Exception e) { Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // ==================== TAB 3: 360 SEARCH ====================
    private void runSmartSearch() {
        String q = etGlobalSearch.getText().toString().trim();
        if (q.length() < 3) { Toast.makeText(this, "Enter at least 3 characters.", Toast.LENGTH_SHORT).show(); return; }
        pbSearch.setVisibility(View.VISIBLE);
        searchResultsContainer.removeAllViews();
        try {
            JSONObject req = new JSONObject();
            req.put("action", "smart_search");
            req.put("q", q);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    pbSearch.setVisibility(View.GONE);
                    if (!json.optBoolean("ok", false)) { handleSessionExpiredIfNeeded(json); return; }
                    JSONArray arr = json.optJSONArray("items");
                    if (arr == null || arr.length() == 0) {
                        addInfoCard(searchResultsContainer, "Search Result", "No Records Found", "Try another Aadhaar, Mobile, Reg No, or Name.", "#64748B");
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject it = arr.optJSONObject(i);
                        if (it != null) {
                            addInfoCard(searchResultsContainer, it.optString("source") + " • " + it.optString("status"),
                                    it.optString("name"), it.optString("subtitle") + "\n" + it.optString("detail"), "#004D28");
                        }
                    }
                }
                @Override public void onError(String message) { pbSearch.setVisibility(View.GONE); Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show(); }
            });
        } catch (Exception e) { pbSearch.setVisibility(View.GONE); }
    }

    // ==================== TAB 4: ALL PORTAL MODULES HUB ====================
    private void showModulesHub() {
        currentModuleKey = "hub";
        moduleSubHeader.setVisibility(View.GONE);
        pbModule.setVisibility(View.GONE);
        modulesContainer.removeAllViews();

        addHubButton("New Refund Data Entry", "Add a new PM-KISAN / KB refund case directly to Sheet1", "#004D28", "new_refund");
        addHubButton("Farmer Query Ledger", "View all farmer queries, call farmers & update status", "#0369A1", "queries");
        addHubButton("New Farmer Query Entry", "Log a new farmer query (exact 29-col portal format)", "#0284C7", "new_query");
        addHubButton("Joint & Invalid A/c Tracker", "Browse Joint/Invalid A/c cases, call & update status", "#B45309", "joint");
        addHubButton("Suspected Deceased List", "View Suspected Deceased tracker records & call", "#991B1B", "deceased");
        addHubButton("Portal Approval Gate", "Check incoming portal queue waiting for approval", "#6D28D9", "queue");
        addHubButton("Today's Daily Register", "View all Refund & Query entries logged today", "#047857", "daily");
        addHubButton("Admin: Active PCs & Force Logout", "See Home PC / Office PC sessions & remotely force logout", "#DC2626", "admin_pcs");
        addHubButton("1-Click Cloud Database Backup", "Run instant external archive + Drive backup now", "#15803D", "backup_now");
        addHubButton("Recent Activity Audit Log", "View latest 40 portal actions across PC & Mobile", "#334155", "logs");
    }

    private void addHubButton(String title, String sub, String colorHex, String key) {
        CardView c = addInfoCard(modulesContainer, "OPEN MODULE", title, sub, colorHex);
        c.setOnClickListener(v -> openModule(key));
    }

    private void openModule(String key) {
        currentModuleKey = key;
        moduleSubHeader.setVisibility(View.VISIBLE);
        modulesContainer.removeAllViews();

        if (key.equals("new_refund")) { tvModuleTitle.setText("New Refund Entry"); renderNewRefundForm(); return; }
        if (key.equals("new_query")) { tvModuleTitle.setText("New Farmer Query"); renderNewQueryForm(); return; }
        if (key.equals("backup_now")) { tvModuleTitle.setText("Database Backup"); runMobileBackup(); return; }

        pbModule.setVisibility(View.VISIBLE);
        try {
            JSONObject req = new JSONObject();
            if (key.equals("queries")) { tvModuleTitle.setText("Farmer Query Ledger"); req.put("action", "list_queries"); }
            else if (key.equals("joint")) { tvModuleTitle.setText("Joint & Invalid A/c"); req.put("action", "list_joint"); }
            else if (key.equals("deceased")) { tvModuleTitle.setText("Suspected Deceased"); req.put("action", "list_deceased"); }
            else if (key.equals("queue")) { tvModuleTitle.setText("Portal Approval Gate"); req.put("action", "list_queue"); }
            else if (key.equals("daily")) { tvModuleTitle.setText("Today's Daily Register"); req.put("action", "daily_register"); }
            else if (key.equals("admin_pcs")) { tvModuleTitle.setText("Active PCs & Devices"); req.put("action", "admin_sessions"); }
            else if (key.equals("logs")) { tvModuleTitle.setText("Activity Audit Log"); req.put("action", "activity_logs"); }

            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    pbModule.setVisibility(View.GONE);
                    if (!json.optBoolean("ok", false)) {
                        handleSessionExpiredIfNeeded(json);
                        Toast.makeText(MainActivity.this, json.optString("error", "Error"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    renderModuleList(key, json);
                }
                @Override public void onError(String message) {
                    pbModule.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) { pbModule.setVisibility(View.GONE); }
    }

    private void renderModuleList(String key, JSONObject json) {
        modulesContainer.removeAllViews();
        JSONArray arr = json.optJSONArray("items");
        if (arr == null || arr.length() == 0) {
            addInfoCard(modulesContainer, "Empty", "No Records Found", "Nothing to display in this module right now.", "#64748B");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            if (key.equals("logs")) {
                JSONArray row = arr.optJSONArray(i);
                if (row != null) {
                    addInfoCard(modulesContainer, row.optString(0) + " • " + row.optString(2),
                            row.optString(3) + " (" + row.optString(1) + ")", row.optString(4), "#334155");
                }
                continue;
            }
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;

            if (key.equals("queries")) {
                String qId = it.optString("qId");
                CardView c = addInfoCard(modulesContainer, qId + " • " + it.optString("scheme") + " • " + it.optString("status"),
                        it.optString("name"), "Date: " + it.optString("date") + " | GP: " + it.optString("gp") + " | Mob: " + it.optString("mobile") + "\n" + it.optString("details"), "#0369A1");
                c.setOnClickListener(v -> showQueryActionDialog(qId, it.optString("mobile"), it.optString("status")));
            } else if (key.equals("joint")) {
                String slId = it.optString("slId");
                CardView c = addInfoCard(modulesContainer, "SL #" + slId + " • KB: " + it.optString("kbNo") + " • " + it.optString("status"),
                        it.optString("name"), "GP: " + it.optString("gp") + " | Bank: " + it.optString("bank") + " (" + it.optString("accountNo") + ")\nReason: " + it.optString("reason"), "#B45309");
                c.setOnClickListener(v -> showJointActionDialog(slId, it.optString("mobile"), it.optString("status")));
            } else if (key.equals("deceased")) {
                CardView c = addInfoCard(modulesContainer, it.optString("caseId") + " • " + it.optString("status"),
                        it.optString("name") + " (S/o " + it.optString("fatherName") + ")",
                        "GP: " + it.optString("gp") + " | DOD: " + it.optString("dod") + " | Aadhaar: " + it.optString("aadhaar") + " | Mob: " + it.optString("mobile"), "#991B1B");
                c.setOnClickListener(v -> dialNumber(it.optString("mobile")));
            } else if (key.equals("queue")) {
                addInfoCard(modulesContainer, "Queue #" + it.optString("id") + " • " + it.optString("status"),
                        it.optString("name"), it.optString("summary"), "#6D28D9");
            } else if (key.equals("daily")) {
                addInfoCard(modulesContainer, it.optString("type") + " • " + it.optString("status"),
                        it.optString("name"), it.optString("detail") + " | Mob: " + it.optString("mobile"), "#047857");
            } else if (key.equals("admin_pcs")) {
                boolean isCur = it.optBoolean("isCurrent", false);
                String lbl = it.optString("deviceLabel") + (isCur ? " (THIS PHONE)" : "");
                CardView c = addInfoCard(modulesContainer, it.optString("role") + " • Logged in: " + it.optString("loginAt"),
                        lbl, "User: " + it.optString("name") + " (" + it.optString("user") + ") | Last Active: " + it.optString("lastSeenAt") + (isCur ? "" : "\nTap card to Force Logout this PC"), isCur ? "#15803D" : "#DC2626");
                if (!isCur) {
                    String tTok = it.optString("token");
                    String dLbl = it.optString("deviceLabel");
                    c.setOnClickListener(v -> confirmForceLogoutPc(tTok, dLbl));
                }
            }
        }
    }

    private void showQueryActionDialog(String qId, String mob, String curStatus) {
        String[] opts = new String[]{"Call Farmer", "Mark Resolved / Closed", "Mark In Process", "Mark Pending"};
        new AlertDialog.Builder(this)
                .setTitle(qId + " (" + curStatus + ")")
                .setItems(opts, (d, which) -> {
                    if (which == 0) { dialNumber(mob); return; }
                    String nextSt = (which == 1) ? "Resolved" : (which == 2 ? "In Process" : "Pending");
                    try {
                        JSONObject req = new JSONObject();
                        req.put("action", "update_query_status");
                        req.put("qId", qId);
                        req.put("status", nextSt);
                        ApiClient.post(req, new ApiClient.Callback() {
                            @Override public void onResult(JSONObject json) {
                                Toast.makeText(MainActivity.this, json.optString("msg", "Updated"), Toast.LENGTH_SHORT).show();
                                openModule("queries");
                            }
                            @Override public void onError(String m) { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); }
                        });
                    } catch (Exception ignored) {}
                }).show();
    }

    private void showJointActionDialog(String slId, String mob, String curStatus) {
        String[] opts = new String[]{"Call Farmer", "Mark Docs Submitted", "Mark Updated in Portal", "Mark Closed / Resolved"};
        new AlertDialog.Builder(this)
                .setTitle("Joint SL #" + slId + " (" + curStatus + ")")
                .setItems(opts, (d, which) -> {
                    if (which == 0) { dialNumber(mob); return; }
                    String nextSt = (which == 1) ? "Docs Submitted" : (which == 2 ? "Updated in Portal" : "Closed / Resolved");
                    try {
                        JSONObject req = new JSONObject();
                        req.put("action", "update_joint_status");
                        req.put("slId", slId);
                        req.put("status", nextSt);
                        ApiClient.post(req, new ApiClient.Callback() {
                            @Override public void onResult(JSONObject json) {
                                Toast.makeText(MainActivity.this, json.optString("msg", "Updated"), Toast.LENGTH_SHORT).show();
                                openModule("joint");
                            }
                            @Override public void onError(String m) { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); }
                        });
                    } catch (Exception ignored) {}
                }).show();
    }

    private void confirmForceLogoutPc(String targetToken, String deviceLabel) {
        new AlertDialog.Builder(this)
                .setTitle("Force Logout " + deviceLabel + "?")
                .setMessage("This will immediately end the session on " + deviceLabel + " without affecting your other devices.")
                .setPositiveButton("Force Logout", (d, w) -> {
                    try {
                        JSONObject req = new JSONObject();
                        req.put("action", "force_logout_pc");
                        req.put("targetToken", targetToken);
                        ApiClient.post(req, new ApiClient.Callback() {
                            @Override public void onResult(JSONObject json) {
                                Toast.makeText(MainActivity.this, json.optString("msg", "Done"), Toast.LENGTH_SHORT).show();
                                openModule("admin_pcs");
                            }
                            @Override public void onError(String m) { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); }
                        });
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runMobileBackup() {
        pbModule.setVisibility(View.VISIBLE);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "run_backup");
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {
                    pbModule.setVisibility(View.GONE);
                    addInfoCard(modulesContainer, "Backup Result",
                            json.optBoolean("ok", false) ? "Backup Completed" : "Backup Notice",
                            json.optString("msg", json.optString("error", "Done")), "#15803D");
                }
                @Override public void onError(String m) { pbModule.setVisibility(View.GONE); Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); }
            });
        } catch (Exception e) { pbModule.setVisibility(View.GONE); }
    }

    private EditText makeInput(LinearLayout parent, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setBackgroundResource(android.R.drawable.edit_text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        et.setLayoutParams(lp);
        parent.addView(et);
        return et;
    }

    private Spinner makeSpinner(LinearLayout parent, String[] options) {
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        sp.setLayoutParams(lp);
        parent.addView(sp);
        return sp;
    }

    private void renderNewRefundForm() {
        Spinner spScheme = makeSpinner(modulesContainer, new String[]{"PM-KISAN", "Krishak Bandhu (Natun)"});
        EditText etName = makeInput(modulesContainer, "Farmer Full Name *");
        EditText etFather = makeInput(modulesContainer, "Father's / Husband's Name");
        EditText etAadhaar = makeInput(modulesContainer, "Aadhaar No (12 digits)");
        EditText etMobile = makeInput(modulesContainer, "Mobile No (10 digits)");
        Spinner spGp = makeSpinner(modulesContainer, AUSGRAM1_GPS);
        EditText etVill = makeInput(modulesContainer, "Village / Mouza");
        EditText etReg = makeInput(modulesContainer, "PMK / KB Registration No");
        Spinner spReason = makeSpinner(modulesContainer, new String[]{"Death", "Income Tax Payee", "Both Husband & Wife", "Ineligible / Other"});
        EditText etDod = makeInput(modulesContainer, "Date of Death / Ineligible (dd-MM-yyyy)");
        EditText etBank = makeInput(modulesContainer, "Bank Name");
        EditText etBranch = makeInput(modulesContainer, "Branch Name");
        EditText etIfsc = makeInput(modulesContainer, "IFSC Code");
        EditText etAcc = makeInput(modulesContainer, "Bank Account No");
        EditText etAmt = makeInput(modulesContainer, "Refundable Amount (Rs)");
        EditText etRem = makeInput(modulesContainer, "Remarks");

        Button btn = new Button(this);
        btn.setText("Save Refund Record to Sheet1");
        btn.setBackgroundColor(Color.parseColor("#004D28"));
        btn.setTextColor(Color.WHITE);
        modulesContainer.addView(btn);

        btn.setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty()) { Toast.makeText(this, "Farmer Name required", Toast.LENGTH_SHORT).show(); return; }
            btn.setEnabled(false); btn.setText("Saving...");
            try {
                JSONObject req = new JSONObject();
                req.put("action", "add_refund");
                req.put("scheme", String.valueOf(spScheme.getSelectedItem()));
                req.put("name", etName.getText().toString().trim());
                req.put("fatherName", etFather.getText().toString().trim());
                req.put("aadhaar", etAadhaar.getText().toString().trim());
                req.put("mobile", etMobile.getText().toString().trim());
                req.put("gp", String.valueOf(spGp.getSelectedItem()));
                req.put("village", etVill.getText().toString().trim());
                req.put("regNo", etReg.getText().toString().trim());
                req.put("reason", String.valueOf(spReason.getSelectedItem()));
                req.put("dod", etDod.getText().toString().trim());
                req.put("bank", etBank.getText().toString().trim());
                req.put("branch", etBranch.getText().toString().trim());
                req.put("ifsc", etIfsc.getText().toString().trim());
                req.put("accountNo", etAcc.getText().toString().trim());
                req.put("amount", etAmt.getText().toString().trim());
                req.put("remarks", etRem.getText().toString().trim());
                ApiClient.post(req, new ApiClient.Callback() {
                    @Override public void onResult(JSONObject json) {
                        btn.setEnabled(true); btn.setText("Save Refund Record to Sheet1");
                        Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_LONG).show();
                        if (json.optBoolean("ok", false)) showModulesHub();
                    }
                    @Override public void onError(String m) { btn.setEnabled(true); btn.setText("Save Refund Record to Sheet1"); Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) { btn.setEnabled(true); }
        });
    }

    private void renderNewQueryForm() {
        Spinner spScheme = makeSpinner(modulesContainer, SCHEMES);
        Spinner spPriority = makeSpinner(modulesContainer, new String[]{"Normal", "High", "Urgent"});
        EditText etName = makeInput(modulesContainer, "Farmer Full Name *");
        EditText etFather = makeInput(modulesContainer, "Father's / Husband's Name");
        EditText etMob = makeInput(modulesContainer, "Mobile No (10 digits)");
        EditText etAadhaar = makeInput(modulesContainer, "Aadhaar No (12 digits)");
        EditText etEpic = makeInput(modulesContainer, "Voter EPIC No");
        EditText etSchemeId = makeInput(modulesContainer, "Scheme ID (PMK / KB ID)");
        Spinner spGp = makeSpinner(modulesContainer, AUSGRAM1_GPS);
        EditText etVill = makeInput(modulesContainer, "Village / Mouza");
        Spinner spReqType = makeSpinner(modulesContainer, new String[]{"Bank A/c Update", "Death Case / Deletion", "Refund Inquiry", "e-KYC / Land Seeding", "General Query"});
        EditText etIssue = makeInput(modulesContainer, "Request / Issue Details *");
        EditText etRem = makeInput(modulesContainer, "General Remarks");

        Button btn = new Button(this);
        btn.setText("Save Farmer Query (Exact 29-Col)");
        btn.setBackgroundColor(Color.parseColor("#0284C7"));
        btn.setTextColor(Color.WHITE);
        modulesContainer.addView(btn);

        btn.setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty() || etIssue.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Farmer Name & Issue Details required", Toast.LENGTH_SHORT).show();
                return;
            }
            btn.setEnabled(false); btn.setText("Saving...");
            try {
                JSONObject req = new JSONObject();
                req.put("action", "quick_query_entry");
                req.put("scheme", String.valueOf(spScheme.getSelectedItem()));
                req.put("priority", String.valueOf(spPriority.getSelectedItem()));
                req.put("name", etName.getText().toString().trim());
                req.put("fatherName", etFather.getText().toString().trim());
                req.put("mobile", etMob.getText().toString().trim());
                req.put("aadhaar", etAadhaar.getText().toString().trim());
                req.put("epic", etEpic.getText().toString().trim());
                req.put("schemeId", etSchemeId.getText().toString().trim());
                req.put("gp", String.valueOf(spGp.getSelectedItem()));
                req.put("village", etVill.getText().toString().trim());
                req.put("requestType", String.valueOf(spReqType.getSelectedItem()));
                req.put("issue", etIssue.getText().toString().trim());
                req.put("remarks", etRem.getText().toString().trim());
                ApiClient.post(req, new ApiClient.Callback() {
                    @Override public void onResult(JSONObject json) {
                        btn.setEnabled(true); btn.setText("Save Farmer Query (Exact 29-Col)");
                        Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_LONG).show();
                        if (json.optBoolean("ok", false)) showModulesHub();
                    }
                    @Override public void onError(String m) { btn.setEnabled(true); btn.setText("Save Farmer Query (Exact 29-Col)"); Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) { btn.setEnabled(true); }
        });
    }

    private void doLogout() {
        try {
            JSONObject req = new JSONObject();
            req.put("action", "logout");
            ApiClient.post(req, new ApiClient.Callback() {
                @Override public void onResult(JSONObject json) {}
                @Override public void onError(String message) {}
            });
        } catch (Exception ignored) {}
        SessionStore.clearSession();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
