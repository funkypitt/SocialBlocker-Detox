package com.parentcontrol.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private BlockPreferences prefs;
    private MaterialButton toggleButton;
    private TextView statusText;
    private TextView currentScheduleText;
    private TextInputEditText scheduleInput;
    private CheckBox checkYoutube, checkInstagram, checkTiktok;

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startBlocker();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new BlockPreferences(this);
        toggleButton = findViewById(R.id.toggleButton);
        statusText = findViewById(R.id.statusText);
        currentScheduleText = findViewById(R.id.currentScheduleText);
        scheduleInput = findViewById(R.id.scheduleInput);
        MaterialButton saveButton = findViewById(R.id.saveScheduleButton);

        checkYoutube = findViewById(R.id.checkYoutube);
        checkInstagram = findViewById(R.id.checkInstagram);
        checkTiktok = findViewById(R.id.checkTiktok);

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        toggleButton.setOnClickListener(v -> toggleBlocking());
        saveButton.setOnClickListener(v -> saveSchedule());

        // Platform checkbox listeners — save immediately on change
        checkYoutube.setOnCheckedChangeListener((b, checked) -> prefs.setYoutubeBlocked(checked));
        checkInstagram.setOnCheckedChangeListener((b, checked) -> prefs.setInstagramBlocked(checked));
        checkTiktok.setOnCheckedChangeListener((b, checked) -> prefs.setTiktokBlocked(checked));

        loadPreferences();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void loadPreferences() {
        String savedSchedule = prefs.getSchedule();
        if (!savedSchedule.isEmpty()) {
            scheduleInput.setText(savedSchedule);
        }
        checkYoutube.setChecked(prefs.isYoutubeBlocked());
        checkInstagram.setChecked(prefs.isInstagramBlocked());
        checkTiktok.setChecked(prefs.isTiktokBlocked());
    }

    private void toggleBlocking() {
        if (prefs.isBlockingEnabled()) {
            stopBlocker();
        } else {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                vpnPermissionLauncher.launch(prepare);
            } else {
                startBlocker();
            }
        }
    }

    private void startBlocker() {
        prefs.setBlockingEnabled(true);
        Intent intent = new Intent(this, SocialBlockerVpnService.class);
        intent.setAction("START");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateUI();
        requestBatteryOptimizationExemption();
        showAlwaysOnVpnHint();
    }

    private void stopBlocker() {
        prefs.setBlockingEnabled(false);
        Intent intent = new Intent(this, SocialBlockerVpnService.class);
        intent.setAction("STOP");
        startService(intent);
        updateUI();
    }

    private void saveSchedule() {
        String schedule = "";
        if (scheduleInput.getText() != null) {
            schedule = scheduleInput.getText().toString().trim();
        }
        prefs.setSchedule(schedule);
        updateScheduleDisplay();
        Toast.makeText(this, R.string.schedule_saved, Toast.LENGTH_SHORT).show();
    }

    // ========================= Persistence Helpers =========================

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void showAlwaysOnVpnHint() {
        if (prefs.hasShownAlwaysOnHint()) return;
        prefs.setShownAlwaysOnHint(true);

        new AlertDialog.Builder(this)
                .setTitle("VPN permanent")
                .setMessage(getString(R.string.always_on_hint))
                .setPositiveButton("Ouvrir les parametres VPN", (d, w) -> {
                    try {
                        startActivity(new Intent("android.net.vpn.SETTINGS"));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Plus tard", null)
                .show();
    }

    // ========================= UI =========================

    private void updateUI() {
        boolean enabled = prefs.isBlockingEnabled();
        if (enabled) {
            toggleButton.setText("ON");
            toggleButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.green_active)));
            statusText.setText(R.string.blocking_on);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green_active));
        } else {
            toggleButton.setText("OFF");
            toggleButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.red_primary)));
            statusText.setText(R.string.blocking_off);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
        updateScheduleDisplay();
    }

    private void updateScheduleDisplay() {
        String schedule = prefs.getSchedule();
        if (schedule.isEmpty()) {
            currentScheduleText.setText(R.string.no_schedule);
        } else {
            currentScheduleText.setText("Actif : " + schedule + "h");
        }
    }
}
