package com.ausgram1.arqms.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private LinearLayout layoutStep1, layoutStep2;
    private EditText etUserId, etPassword, etDeviceName, etOtp;
    private Button btnSendOtp, btnVerifyOtp, btnBackStep1;
    private ProgressBar pbLogin;
    private TextView tvLoginError, tvOtpHint, tvLoginStepTitle;

    private String pendingUser = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        layoutStep1 = findViewById(R.id.layoutStep1);
        layoutStep2 = findViewById(R.id.layoutStep2);
        etUserId = findViewById(R.id.etUserId);
        etPassword = findViewById(R.id.etPassword);
        etDeviceName = findViewById(R.id.etDeviceName);
        etOtp = findViewById(R.id.etOtp);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        btnBackStep1 = findViewById(R.id.btnBackStep1);
        pbLogin = findViewById(R.id.pbLogin);
        tvLoginError = findViewById(R.id.tvLoginError);
        tvOtpHint = findViewById(R.id.tvOtpHint);
        tvLoginStepTitle = findViewById(R.id.tvLoginStepTitle);

        etUserId.setText(SessionStore.getSavedUid(this));
        etDeviceName.setText(SessionStore.getSavedDeviceName(this));

        btnSendOtp.setOnClickListener(v -> doStep1());
        btnVerifyOtp.setOnClickListener(v -> doStep2());
        btnBackStep1.setOnClickListener(v -> {
            layoutStep2.setVisibility(View.GONE);
            layoutStep1.setVisibility(View.VISIBLE);
            tvLoginStepTitle.setText("Officer Sign-In (2FA Secured)");
            tvLoginError.setVisibility(View.GONE);
        });
    }

    private void setLoading(boolean loading) {
        pbLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendOtp.setEnabled(!loading);
        btnVerifyOtp.setEnabled(!loading);
    }

    private void showError(String msg) {
        tvLoginError.setText(msg);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void doStep1() {
        String u = etUserId.getText().toString().trim();
        String p = etPassword.getText().toString().trim();
        String dev = etDeviceName.getText().toString().trim();
        if (dev.isEmpty()) dev = "Android Phone";
        if (u.isEmpty() || p.isEmpty()) {
            showError("Please enter User ID and Password.");
            return;
        }
        SessionStore.saveDevicePrefs(this, u, dev);
        tvLoginError.setVisibility(View.GONE);
        setLoading(true);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "login_step1");
            req.put("username", u);
            req.put("password", p);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    setLoading(false);
                    if (json.optBoolean("ok", false)) {
                        pendingUser = u;
                        String masked = json.optString("maskedEmail", "your email");
                        String nm = json.optString("name", "Officer");
                        tvLoginStepTitle.setText("2FA OTP Verification");
                        tvOtpHint.setText("Welcome " + nm + "! Enter the 6-digit OTP sent to " + masked);
                        layoutStep1.setVisibility(View.GONE);
                        layoutStep2.setVisibility(View.VISIBLE);
                        etOtp.setText("");
                        etOtp.requestFocus();
                    } else {
                        showError(json.optString("error", "Invalid credentials."));
                    }
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    showError(message);
                }
            });
        } catch (Exception e) {
            setLoading(false);
            showError(e.getMessage());
        }
    }

    private void doStep2() {
        String otp = etOtp.getText().toString().trim();
        String dev = etDeviceName.getText().toString().trim();
        if (dev.isEmpty()) dev = "Android Phone";
        if (otp.length() != 6) {
            showError("Please enter the 6-digit OTP.");
            return;
        }
        tvLoginError.setVisibility(View.GONE);
        setLoading(true);
        try {
            JSONObject req = new JSONObject();
            req.put("action", "login_step2");
            req.put("username", pendingUser);
            req.put("otp", otp);
            req.put("deviceLabel", dev);
            ApiClient.post(req, new ApiClient.Callback() {
                @Override
                public void onResult(JSONObject json) {
                    setLoading(false);
                    if (json.optBoolean("ok", false)) {
                        SessionStore.token = json.optString("token", "");
                        SessionStore.user = json.optString("user", pendingUser);
                        SessionStore.name = json.optString("name", "Officer");
                        SessionStore.role = json.optString("role", "Staff");
                        SessionStore.deviceLabel = json.optString("deviceLabel", "Android Phone");
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showError(json.optString("error", "OTP verification failed."));
                    }
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    showError(message);
                }
            });
        } catch (Exception e) {
            setLoading(false);
            showError(e.getMessage());
        }
    }
}
