package com.simonbaars.androidforceinstall;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_APK_REQUEST = 1;
    
    private Button selectButton;
    private Button installButton;
    private TextView selectedFileText;
    private TextView statusText;
    private TextView rootStatusText;
    private File selectedApkFile;

    static {
        // Set libsu configurations
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectButton = findViewById(R.id.selectButton);
        installButton = findViewById(R.id.installButton);
        selectedFileText = findViewById(R.id.selectedFile);
        statusText = findViewById(R.id.status);
        rootStatusText = findViewById(R.id.rootStatus);

        selectButton.setOnClickListener(v -> selectApkFile());
        installButton.setOnClickListener(v -> installApk());

        checkRootAccess();
    }

    private void checkRootAccess() {
        rootStatusText.setText("Checking root access...");
        
        new Thread(() -> {
            Shell.getShell(shell -> {
                runOnUiThread(() -> {
                    if (shell.isRoot()) {
                        rootStatusText.setText(R.string.root_granted);
                        selectButton.setEnabled(true);
                    } else {
                        rootStatusText.setText(R.string.root_denied);
                        showRootRequiredDialog();
                    }
                });
            });
        }).start();
    }

    private void showRootRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.root_required)
                .setMessage(R.string.root_not_available)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void selectApkFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_APK_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_APK_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String path = uri.getPath();
                
                if (path != null) {
                    selectedApkFile = new File(path);
                    selectedFileText.setText(getString(R.string.selected_file, selectedApkFile.getName()));
                    installButton.setEnabled(true);
                    statusText.setText("");
                }
            }
        }
    }

    private void installApk() {
        if (selectedApkFile == null || !selectedApkFile.exists()) {
            Toast.makeText(this, "Please select a valid APK file", Toast.LENGTH_SHORT).show();
            return;
        }

        installButton.setEnabled(false);
        selectButton.setEnabled(false);
        statusText.setText(R.string.installing);

        new Thread(() -> {
            try {
                String apkPath = selectedApkFile.getAbsolutePath();
                
                // Use pm install with root to force install the APK
                // The -d flag allows downgrading
                // The -r flag replaces existing application
                Shell.Result result = Shell.cmd(
                        "pm install -d -r \"" + apkPath + "\""
                ).exec();

                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        statusText.setText(R.string.install_success);
                        Toast.makeText(MainActivity.this, R.string.install_success, Toast.LENGTH_LONG).show();
                    } else {
                        String error = result.getOut().isEmpty() ? 
                                "Unknown error" : 
                                String.join("\n", result.getOut());
                        statusText.setText(getString(R.string.install_error, error));
                        Toast.makeText(MainActivity.this, getString(R.string.install_error, error), Toast.LENGTH_LONG).show();
                    }
                    
                    installButton.setEnabled(true);
                    selectButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.install_error, e.getMessage()));
                    Toast.makeText(MainActivity.this, getString(R.string.install_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    installButton.setEnabled(true);
                    selectButton.setEnabled(true);
                });
            }
        }).start();
    }
}
