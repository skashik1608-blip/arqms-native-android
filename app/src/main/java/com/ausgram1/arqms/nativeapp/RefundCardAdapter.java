package com.ausgram1.arqms.nativeapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RefundCardAdapter extends RecyclerView.Adapter<RefundCardAdapter.VH> {

    public interface CardListener {
        void onCall(JSONObject item);
        void onUpdateUtr(JSONObject item);
        void onUploadDoc(JSONObject item);
        void onOpenDrive(JSONObject item);
    }

    private final List<JSONObject> items = new ArrayList<>();
    private final CardListener listener;

    public RefundCardAdapter(CardListener listener) {
        this.listener = listener;
    }

    public void setItems(List<JSONObject> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_refund_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject it = items.get(pos);
        String sl = it.optString("slNo", "");
        String name = it.optString("name", "Farmer");
        String scheme = it.optString("scheme", "");
        String gp = it.optString("gp", "");
        String vill = it.optString("village", "");
        String amt = it.optString("amount", "0");
        String bank = it.optString("bank", "");
        String acc = it.optString("accountNo", "");
        String aadhaar = it.optString("aadhaar", "");
        String utr = it.optString("utr", "");
        String status = it.optString("status", "Pending");

        h.tvName.setText("#" + sl + "  " + name);
        h.tvStatus.setText(status);
        String stLow = status.toLowerCase();
        if (stLow.contains("completed") || stLow.contains("refunded") || stLow.contains("done")) {
            h.tvStatus.setBackgroundColor(Color.parseColor("#DCFCE7"));
            h.tvStatus.setTextColor(Color.parseColor("#166534"));
        } else {
            h.tvStatus.setBackgroundColor(Color.parseColor("#FEF3C7"));
            h.tvStatus.setTextColor(Color.parseColor("#B45309"));
        }

        h.tvMeta1.setText(scheme + "  •  GP: " + gp + (vill.isEmpty() ? "" : ("  •  " + vill)));
        h.tvMeta2.setText("Amount: Rs " + amt + (bank.isEmpty() ? "" : ("  •  " + bank)) + (acc.isEmpty() ? "" : (" (" + acc + ")")));
        h.tvMeta3.setText("Aadhaar: " + (aadhaar.isEmpty() ? "-" : aadhaar) + "  •  UTR: " + (utr.isEmpty() ? "Pending" : utr));

        h.btnCall.setOnClickListener(v -> listener.onCall(it));
        h.btnUtr.setOnClickListener(v -> listener.onUpdateUtr(it));
        h.btnUpload.setOnClickListener(v -> listener.onUploadDoc(it));
        h.btnDrive.setOnClickListener(v -> listener.onOpenDrive(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvMeta1, tvMeta2, tvMeta3;
        Button btnCall, btnUtr, btnUpload, btnDrive;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvCardName);
            tvStatus = v.findViewById(R.id.tvCardStatus);
            tvMeta1 = v.findViewById(R.id.tvCardMeta1);
            tvMeta2 = v.findViewById(R.id.tvCardMeta2);
            tvMeta3 = v.findViewById(R.id.tvCardMeta3);
            btnCall = v.findViewById(R.id.btnCardCall);
            btnUtr = v.findViewById(R.id.btnCardUtr);
            btnUpload = v.findViewById(R.id.btnCardUpload);
            btnDrive = v.findViewById(R.id.btnCardDrive);
        }
    }
}
