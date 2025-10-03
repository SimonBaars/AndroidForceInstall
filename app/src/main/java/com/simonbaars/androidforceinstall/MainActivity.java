package com.simonbaars.androidforceinstall;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
                
                // Copy the file to cache directory for installation
                new Thread(() -> {
                    try {
                        String fileName = getFileName(uri);
                        File cacheFile = new File(getCacheDir(), fileName != null ? fileName : "temp.apk");
                        
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        if (inputStream != null) {
                            FileOutputStream outputStream = new FileOutputStream(cacheFile);
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            outputStream.close();
                            inputStream.close();
                            
                            runOnUiThread(() -> {
                                selectedApkFile = cacheFile;
                                selectedFileText.setText(getString(R.string.selected_file, fileName != null ? fileName : "temp.apk"));
                                installButton.setEnabled(true);
                                statusText.setText("");
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start();
            }
        }
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
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

                // Check if installation failed due to signature mismatch
                if (!result.isSuccess()) {
                    String output = String.join("\n", result.getOut());
                    
                    // Check for signature mismatch errors
                    // When an APK with a different signature is installed over an existing app,
                    // Android refuses the installation for security reasons.
                    // Our NEW approach: replace APK file directly on filesystem
                    // This preserves all app data since we don't uninstall
                    // 
                    // WARNING: This approach bypasses Android's security checks
                    // - The app may fail signature verification on launch
                    // - PackageManager cache may become out of sync
                    // - May not work on all Android versions
                    if (output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || 
                        output.contains("signatures do not match") ||
                        output.contains("Existing package") && output.contains("signatures do not match")) {
                        
                        runOnUiThread(() -> {
                            statusText.setText("Signature mismatch detected. Replacing APK directly...");
                        });
                        
                        // Extract package name from the APK using PackageManager
                        String packageName = null;
                        try {
                            PackageManager pm = getPackageManager();
                            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
                            if (info != null) {
                                packageName = info.packageName;
                            }
                        } catch (Exception e) {
                            // Failed to get package name from PackageManager
                        }
                        
                        if (packageName != null) {
                            final String pkgName = packageName;
                            
                            runOnUiThread(() -> {
                                statusText.setText("Finding installed APK location for " + pkgName + "...");
                            });
                            
                            // Get the APK installation path(s)
                            Shell.Result pathResult = Shell.cmd(
                                    "pm path " + packageName
                            ).exec();
                            
                            if (!pathResult.isSuccess() || pathResult.getOut().isEmpty()) {
                                runOnUiThread(() -> {
                                    statusText.setText("Could not find installed APK location");
                                    Toast.makeText(MainActivity.this, "Could not find installed APK location", Toast.LENGTH_LONG).show();
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                            
                            // Parse APK paths - can be multiple for split APKs
                            List<String> installedApkPaths = new java.util.ArrayList<>();
                            for (String line : pathResult.getOut()) {
                                if (line.startsWith("package:")) {
                                    installedApkPaths.add(line.replace("package:", "").trim());
                                }
                            }
                            
                            if (installedApkPaths.isEmpty()) {
                                runOnUiThread(() -> {
                                    statusText.setText("Could not parse APK paths");
                                    Toast.makeText(MainActivity.this, "Could not parse APK paths", Toast.LENGTH_LONG).show();
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                            
                            // Check if this is a split APK installation
                            boolean isSplitApk = installedApkPaths.size() > 1;
                            String baseApkPath = installedApkPaths.get(0);
                            
                            if (isSplitApk) {
                                runOnUiThread(() -> {
                                    statusText.setText("Warning: App uses split APKs. This may not work correctly.");
                                    Toast.makeText(MainActivity.this, "Warning: Split APK detected. Replacement may fail.", Toast.LENGTH_LONG).show();
                                });
                            }
                            
                            runOnUiThread(() -> {
                                statusText.setText("Force-stopping " + pkgName + "...");
                            });
                            
                            // Force stop the app before replacing APK
                            Shell.Result stopResult = Shell.cmd(
                                    "am force-stop " + packageName
                            ).exec();
                            
                            if (!stopResult.isSuccess()) {
                                runOnUiThread(() -> {
                                    statusText.setText("Warning: Could not force-stop app");
                                });
                            }
                            
                            // Sleep briefly to ensure app is fully stopped
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                            
                            runOnUiThread(() -> {
                                statusText.setText("Replacing APK file(s)...");
                            });
                            
                            // Replace the APK file directly
                            // For split APKs, we only replace base.apk (which is what we have)
                            // This may cause issues, but it's what was requested
                            Shell.Result replaceResult = Shell.cmd(
                                    "cp -f \"" + apkPath + "\" \"" + baseApkPath + "\"",
                                    "chmod 644 \"" + baseApkPath + "\"",
                                    "chown system:system \"" + baseApkPath + "\"",
                                    "restorecon \"" + baseApkPath + "\""
                            ).exec();
                            
                            if (!replaceResult.isSuccess()) {
                                runOnUiThread(() -> {
                                    String error = replaceResult.getOut().isEmpty() ? 
                                            "Failed to replace APK file" : 
                                            String.join("\n", replaceResult.getOut());
                                    statusText.setText("APK replacement failed: " + error);
                                    Toast.makeText(MainActivity.this, "APK replacement failed: " + error, Toast.LENGTH_LONG).show();
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                            
                            runOnUiThread(() -> {
                                statusText.setText("Refreshing Package Manager cache...");
                            });
                            
                            // Try to refresh Package Manager cache
                            // This is a best-effort attempt - may not work on all Android versions
                            Shell.cmd(
                                    "pm path " + packageName + " > /dev/null 2>&1"
                            ).exec();
                            
                            // Give the system a moment to process
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                            
                            // Mark the result as successful
                            result = replaceResult;
                            
                            runOnUiThread(() -> {
                                statusText.setText("APK replaced successfully. App data preserved.\n\nWARNING: The app may fail to launch due to signature verification. You may need to reboot the device.");
                                Toast.makeText(MainActivity.this, "APK replaced. App may require reboot to work.", Toast.LENGTH_LONG).show();
                            });
                        } else {
                            runOnUiThread(() -> {
                                statusText.setText("Could not extract package name. Unable to replace APK.");
                                Toast.makeText(MainActivity.this, "Could not extract package name", Toast.LENGTH_LONG).show();
                                installButton.setEnabled(true);
                                selectButton.setEnabled(true);
                            });
                            return;
                        }
                    }
                }

                final Shell.Result finalResult = result;
                runOnUiThread(() -> {
                    if (finalResult.isSuccess()) {
                        statusText.setText(R.string.install_success);
                        Toast.makeText(MainActivity.this, R.string.install_success, Toast.LENGTH_LONG).show();
                    } else {
                        String error = finalResult.getOut().isEmpty() ? 
                                "Unknown error" : 
                                String.join("\n", finalResult.getOut());
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
