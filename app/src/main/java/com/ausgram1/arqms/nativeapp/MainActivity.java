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
import android.widget.ScrollView;
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
    private ScrollView tabQuickQuery;
    private RecyclerView rvRefunds;
    private RefundCardAdapter refundAdapter;
    private EditText etRefundSearch, etGlobalSearch;
    private Button btnFilterAll, btnFilterPending, btnFilterCompleted;
    private TextView tvRefundCount, tvTopUser;
    private ProgressBar pbSearch;
    private EditText etQName, etQMobile, etQAadhaar, etQVillage, etQIssue, etQRemarks;
    private Spinner spQScheme, spQGp;
    private Button btnSaveQuery;

    private String activeFilter = "all";
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
        tabQuickQuery = findViewById(R.id.tabQuickQuery);

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

        etQName = findViewById(R.id.etQName);
        etQMobile = findViewById(R.id.etQMobile);
        etQAadhaar = findViewById(R.id.etQAadhaar);
        etQVillage = findViewById(R.id.etQVillage);
        etQIssue = findViewById(R.id.etQIssue);
        etQRemarks = findViewById(R.id.etQRemarks);
        spQScheme = findViewById(R.id.spQScheme);
        spQGp = findViewById(R.id.spQGp);
        btnSaveQuery = findViewById(R.id.btnSaveQuery);

        spQScheme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"PM-KISAN", "Krishak Bandhu (Natun)", "General / Other"}));
        spQGp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, AUSGRAM1_GPS));

        rvRefunds.setLayoutManager(new LinearLayoutManager(this));
        refundAdapter = new RefundCardAdapter(new RefundCardAdapter.CardListener() {
            @Override
            public void onCall(JSONObject item) {
                String mob = item.optString("mobile", "").replaceAll("\\D", "");
                if (mob.length() < 10) {
                    Toast.makeText(MainActivity.this, "No valid mobile number saved.", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + mob)));
            }

            @Override
            public void onUpdateUtr(JSONObject item) {
                showQuickUtrDialog(item);
            }

            @Override
            public void onUploadDoc(JSONObject item) {
                showDocUploadPicker(item);
            }

            @Override
            public void onOpenDrive(JSONObject item) {
                String url = item.optString("folderUrl", "");
                if (url.startsWith("http")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } else {
                    Toast.makeText(MainActivity.this, "No Drive folder linked yet. Tap Upload Doc to create one.", Toast.LENGTH_SHORT).show();
                }
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
        btnSaveQuery.setOnClickListener(v -> submitQuickQuery());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            swipeDashboard.setVisibility(id == R.id.nav_dashboard ? View.VISIBLE : View.GONE);
            tabRefunds.setVisibility(id == R.id.nav_refunds ? View.VISIBLE : View.GONE);
            tabSearch.setVisibility(id == R.id.nav_search ? View.VISIBLE : View.GONE);
            tabQuickQuery.setVisibility(id == R.id.nav_query ? View.VISIBLE : View.GONE);
            if (id == R.id.nav_refunds && refundAdapter.getItemCount() == 0) {
                loadRefunds();
            }
            return true;
        });

        loadDashboard();
    }

    private void handleSessionExpiredIfNeeded(JSONObject json) {
        if (json != null && json.optBoolean("sessionExpired", false)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            SessionStore.clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void loadDashboard() {
        swipeDashboard.setRefreshing(true);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "dashboard");
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    swipeDashboard.setRefreshing(false);
                    if (!json.optBoolean("ok", false)) {
                        handleSessionExpiredIfNeeded(json);
                        Toast.makeText(MainActivity.this, json.optString("error", "Error"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    renderDashboard(json);
                }

                @Override
                public void onError(String message) {
                    swipeDashboard.setRefreshing(false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            swipeDashboard.setRefreshing(false);
        }
    }

    private void renderDashboard(JSONObject json) {
        dashContainer.removeAllViews();
        JSONObject st = json.optJSONObject("stats");
        if (st == null) return;

        addKpiCard("Total Refund Cases", String.valueOf(st.optInt("total")),
                "Completed: " + st.optInt("completed") + "   |   Pending: " + st.optInt("pending"), "#004D28");
        addKpiCard("Recovery Progress", "Rs " + st.optInt("recoveredAmt"),
                "Total Target Amount: Rs " + st.optInt("totalAmt"), "#0369A1");
        addKpiCard("Active Trackers",
                "Queries: " + st.optInt("farmerQueries"),
                "Joint A/c: " + st.optInt("jointAccounts") + "   |   Suspected Deceased: " + st.optInt("suspectedDeceased"), "#B45309");

        TextView hdr = new TextView(this);
        hdr.setText("GP-Wise Pending Breakdown");
        hdr.setTextSize(15f);
        hdr.setTypeface(null, Typeface.BOLD);
        hdr.setTextColor(Color.parseColor("#0F172A"));
        hdr.setPadding(4, 18, 4, 8);
        dashContainer.addView(hdr);

        JSONArray gps = json.optJSONArray("gpSummary");
        if (gps != null) {
            for (int i = 0; i < gps.length(); i++) {
                JSONObject g = gps.optJSONObject(i);
                if (g == null) continue;
                addKpiCard(g.optString("gp", "GP"),
                        "Pending: " + g.optInt("pending"),
                        "Total: " + g.optInt("total") + "   |   Completed: " + g.optInt("completed"), "#334155");
            }
        }
    }

    private void addKpiCard(String title, String bigVal, String sub, String accentHex) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 22;
        card.setLayoutParams(lp);
        card.setRadius(24f);
        card.setCardElevation(6f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(36, 28, 36, 28);

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextSize(13f);
        tvT.setTypeface(null, Typeface.BOLD);
        tvT.setTextColor(Color.parseColor(accentHex));

        TextView tvV = new TextView(this);
        tvV.setText(bigVal);
        tvV.setTextSize(22f);
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
        dashContainer.addView(card);
    }

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
            req.put("page", 1);
            req.put("pageSize", 40);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    swipeRefunds.setRefreshing(false);
                    if (!json.optBoolean("ok", false)) {
                        handleSessionExpiredIfNeeded(json);
                        return;
                    }
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

                @Override
                public void onError(String message) {
                    swipeRefunds.setRefreshing(false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            swipeRefunds.setRefreshing(false);
        }
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
        if (curDate.isEmpty()) {
            curDate = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        }
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
                            @Override
                            public void onResult(JSONObject json) {
                                Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_SHORT).show();
                                if (json.optBoolean("ok", false)) loadRefunds();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
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
            if (bmp == null) {
                Toast.makeText(this, "Could not read selected image.", Toast.LENGTH_SHORT).show();
                return;
            }
            int maxDim = 1400;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w > maxDim || h > maxDim) {
                float sc = Math.min((float) maxDim / w, (float) maxDim / h);
                bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * sc), Math.round(h * sc), true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 78, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            JSONObject req = new JSONObject();
            req.put("action", "upload_doc");
            req.put("slNo", pendingUploadSlNo);
            req.put("docName", pendingUploadDocName);
            req.put("mimeType", "image/jpeg");
            req.put("base64Data", b64);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_LONG).show();
                    if (json.optBoolean("ok", false)) loadRefunds();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Upload failed: " + message, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runSmartSearch() {
        String q = etGlobalSearch.getText().toString().trim();
        if (q.length() < 3) {
            Toast.makeText(this, "Enter at least 3 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        pbSearch.setVisibility(View.VISIBLE);
        searchResultsContainer.removeAllViews();
        try {
            JSONObject req = new JSONObject();
            req.put("action", "smart_search");
            req.put("q", q);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    pbSearch.setVisibility(View.GONE);
                    if (!json.optBoolean("ok", false)) {
                        handleSessionExpiredIfNeeded(json);
                        Toast.makeText(MainActivity.this, json.optString("error", "No matches"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONArray arr = json.optJSONArray("items");
                    if (arr == null || arr.length() == 0) {
                        TextView empty = new TextView(MainActivity.this);
                        empty.setText("No matching farmer records found.");
                        empty.setPadding(20, 40, 20, 20);
                        searchResultsContainer.addView(empty);
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject it = arr.optJSONObject(i);
                        if (it == null) continue;
                        addSearchResultCard(it);
                    }
                }

                @Override
                public void onError(String message) {
                    pbSearch.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            pbSearch.setVisibility(View.GONE);
        }
    }

    private void addSearchResultCard(JSONObject it) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 20;
        card.setLayoutParams(lp);
        card.setRadius(20f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(32, 24, 32, 24);

        TextView tSrc = new TextView(this);
        tSrc.setText(it.optString("source") + "  •  " + it.optString("status"));
        tSrc.setTextSize(11.5f);
        tSrc.setTypeface(null, Typeface.BOLD);
        tSrc.setTextColor(Color.parseColor("#004D28"));

        TextView tName = new TextView(this);
        tName.setText(it.optString("name"));
        tName.setTextSize(16f);
        tName.setTypeface(null, Typeface.BOLD);
        tName.setTextColor(Color.parseColor("#0F172A"));

        TextView tSub = new TextView(this);
        tSub.setText(it.optString("subtitle") + "\n" + it.optString("detail"));
        tSub.setTextSize(12.5f);
        tSub.setTextColor(Color.parseColor("#475569"));

        inner.addView(tSrc);
        inner.addView(tName);
        inner.addView(tSub);
        card.addView(inner);
        searchResultsContainer.addView(card);
    }

    private void submitQuickQuery() {
        String name = etQName.getText().toString().trim();
        String issue = etQIssue.getText().toString().trim();
        if (name.isEmpty() || issue.isEmpty()) {
            Toast.makeText(this, "Farmer Name and Issue are required.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSaveQuery.setEnabled(false);
        btnSaveQuery.setText("Saving Live...");
        try {
            JSONObject req = new JSONObject();
            req.put("action", "quick_query_entry");
            req.put("name", name);
            req.put("mobile", etQMobile.getText().toString().trim());
            req.put("aadhaar", etQAadhaar.getText().toString().trim());
            req.put("scheme", String.valueOf(spQScheme.getSelectedItem()));
            req.put("gp", String.valueOf(spQGp.getSelectedItem()));
            req.put("village", etQVillage.getText().toString().trim());
            req.put("issue", issue);
            req.put("remarks", etQRemarks.getText().toString().trim());
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    btnSaveQuery.setEnabled(true);
                    btnSaveQuery.setText("Save Farmer Query Live");
                    Toast.makeText(MainActivity.this, json.optString("msg", json.optString("error", "Done")), Toast.LENGTH_LONG).show();
                    if (json.optBoolean("ok", false)) {
                        etQName.setText("");
                        etQMobile.setText("");
                        etQAadhaar.setText("");
                        etQVillage.setText("");
                        etQIssue.setText("");
                        etQRemarks.setText("");
                    }
                }

                @Override
                public void onError(String message) {
                    btnSaveQuery.setEnabled(true);
                    btnSaveQuery.setText("Save Farmer Query Live");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            btnSaveQuery.setEnabled(true);
            btnSaveQuery.setText("Save Farmer Query Live");
        }
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
