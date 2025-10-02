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
                    // Our approach: backup app data, uninstall old app, install new app, restore data
                    // 
                    // Why not replace APK on filesystem?
                    // - Android's runtime verification would prevent the app from running
                    // - PackageManager cache synchronization is complex and error-prone
                    // - Split APK handling would be extremely complicated
                    // See APK_REPLACEMENT_DISCUSSION.md for detailed analysis
                    if (output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || 
                        output.contains("signatures do not match") ||
                        output.contains("Existing package") && output.contains("signatures do not match")) {
                        
                        // Show warning dialog to user about signature mismatch
                        final boolean[] userConfirmed = {false};
                        final Object lock = new Object();
                        
                        runOnUiThread(() -> {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(R.string.signature_mismatch_title)
                                    .setMessage(R.string.signature_mismatch_message)
                                    .setPositiveButton(R.string.signature_mismatch_continue, (dialog, which) -> {
                                        synchronized (lock) {
                                            userConfirmed[0] = true;
                                            lock.notify();
                                        }
                                    })
                                    .setNegativeButton(R.string.signature_mismatch_cancel, (dialog, which) -> {
                                        synchronized (lock) {
                                            userConfirmed[0] = false;
                                            lock.notify();
                                        }
                                    })
                                    .setCancelable(false)
                                    .show();
                        });
                        
                        // Wait for user response
                        synchronized (lock) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                runOnUiThread(() -> {
                                    statusText.setText("Operation cancelled");
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                        }
                        
                        // If user cancelled, stop here
                        if (!userConfirmed[0]) {
                            runOnUiThread(() -> {
                                statusText.setText("Installation cancelled by user");
                                installButton.setEnabled(true);
                                selectButton.setEnabled(true);
                            });
                            return;
                        }
                        
                        runOnUiThread(() -> {
                            statusText.setText("Signature mismatch detected. Attempting to preserve data...");
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
                            String backupPath = "/data/local/tmp/" + packageName + "_backup";
                            String dataPath = "/data/data/" + packageName;
                            String extDataPath = "/storage/emulated/0/Android/data/" + packageName;
                            String extBackupPath = "/data/local/tmp/" + packageName + "_ext_backup";
                            
                            runOnUiThread(() -> {
                                statusText.setText(R.string.detecting_install_location);
                            });
                            
                            // CRITICAL: Detect current install location and user context before uninstall
                            // This fixes two major issues:
                            // 1. Apps reinstalling to wrong location (e.g., user space -> private space)
                            // 2. Apps installing to wrong user profile (e.g., main user -> work profile)
                            
                            // Get the APK installation path to determine storage location
                            Shell.Result pathResult = Shell.cmd(
                                    "pm path " + packageName
                            ).exec();
                            
                            String installLocation = "auto";
                            String userId = "0"; // Default to primary user
                            
                            if (pathResult.isSuccess() && !pathResult.getOut().isEmpty()) {
                                String installedApkPath = pathResult.getOut().get(0).replace("package:", "");
                                
                                // Determine install location from APK path
                                // Internal storage: /data/app/...
                                // External/adoptable storage: /mnt/.../app/... or /storage/...
                                if (installedApkPath.startsWith("/data/app/")) {
                                    installLocation = "internal";
                                } else if (installedApkPath.contains("/mnt/") || installedApkPath.contains("/storage/")) {
                                    installLocation = "external";
                                }
                            }
                            
                            // Detect which user/profile the app is installed for
                            // This is important for devices with work profiles or multiple users
                            Shell.Result userResult = Shell.cmd(
                                    "pm list packages --user all | grep '" + packageName + "$'"
                            ).exec();
                            
                            if (userResult.isSuccess() && !userResult.getOut().isEmpty()) {
                                for (String line : userResult.getOut()) {
                                    // Format: package:<name> uid:<uid> user:<userId>
                                    if (line.contains("user:")) {
                                        String[] parts = line.split("user:");
                                        if (parts.length > 1) {
                                            userId = parts[1].trim();
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            final String finalInstallLocation = installLocation;
                            final String finalUserId = userId;
                            
                            runOnUiThread(() -> {
                                statusText.setText(getString(R.string.backing_up_data, pkgName));
                            });
                            
                            // Get the current UID for later restoration
                            Shell.Result uidResult = Shell.cmd(
                                    "pm list packages -U | grep " + packageName
                            ).exec();
                            
                            String oldUid = null;
                            if (uidResult.isSuccess() && !uidResult.getOut().isEmpty()) {
                                String uidLine = uidResult.getOut().get(0);
                                String[] parts = uidLine.split("uid:");
                                if (parts.length > 1) {
                                    oldUid = parts[1].trim();
                                }
                            }
                            
                            // Backup app data
                            Shell.Result backupResult = Shell.cmd(
                                    "rm -rf \"" + backupPath + "\"",
                                    "cp -a \"" + dataPath + "\" \"" + backupPath + "\"",
                                    "rm -rf \"" + extBackupPath + "\"",
                                    "if [ -d \"" + extDataPath + "\" ]; then cp -a \"" + extDataPath + "\" \"" + extBackupPath + "\"; fi"
                            ).exec();
                            
                            if (!backupResult.isSuccess()) {
                                runOnUiThread(() -> {
                                    String backupError = "Failed to backup app data";
                                    statusText.setText(getString(R.string.backup_error, backupError));
                                    Toast.makeText(MainActivity.this, getString(R.string.backup_error, backupError), Toast.LENGTH_LONG).show();
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                            
                            runOnUiThread(() -> {
                                statusText.setText(getString(R.string.uninstalling_package, pkgName));
                            });
                            
                            // Uninstall the existing package
                            Shell.Result uninstallResult = Shell.cmd(
                                    "pm uninstall " + packageName
                            ).exec();
                            
                            if (uninstallResult.isSuccess()) {
                                runOnUiThread(() -> {
                                    statusText.setText(R.string.installing_after_uninstall);
                                });
                                
                                // Build install command with location and user preservation
                                // This ensures the app reinstalls to the same location and user context
                                // as the original installation, preventing issues like:
                                // - User space apps moving to private space
                                // - Main user apps moving to work profile
                                StringBuilder installCmd = new StringBuilder("pm install -d -r");
                                
                                // Preserve install location using --install-location flag
                                // 0 = auto (let system decide)
                                // 1 = internal storage only
                                // 2 = external storage (SD card or adoptable storage)
                                if ("internal".equals(finalInstallLocation)) {
                                    installCmd.append(" --install-location 1"); // Force internal only
                                } else if ("external".equals(finalInstallLocation)) {
                                    installCmd.append(" --install-location 2"); // Force external only
                                } else {
                                    installCmd.append(" --install-location 0"); // Auto
                                }
                                
                                // Preserve user context using --user flag
                                // This is critical for multi-user devices and work profiles
                                if (!"0".equals(finalUserId)) {
                                    installCmd.append(" --user ").append(finalUserId);
                                }
                                
                                installCmd.append(" \"").append(apkPath).append("\"");
                                
                                // Try installing again with preserved location and user context
                                Shell.Result retryResult = Shell.cmd(installCmd.toString()).exec();
                                
                                if (retryResult.isSuccess()) {
                                    runOnUiThread(() -> {
                                        statusText.setText(R.string.restoring_data);
                                    });
                                    
                                    // Get the new UID
                                    Shell.Result newUidResult = Shell.cmd(
                                            "pm list packages -U | grep " + packageName
                                    ).exec();
                                    
                                    String newUid = oldUid; // Default to old UID if we can't get new one
                                    if (newUidResult.isSuccess() && !newUidResult.getOut().isEmpty()) {
                                        String newUidLine = newUidResult.getOut().get(0);
                                        String[] parts = newUidLine.split("uid:");
                                        if (parts.length > 1) {
                                            newUid = parts[1].trim();
                                        }
                                    }
                                    
                                    // Restore app data
                                    Shell.Result restoreResult = Shell.cmd(
                                            "rm -rf \"" + dataPath + "\"",
                                            "cp -a \"" + backupPath + "\" \"" + dataPath + "\"",
                                            "chown -R " + newUid + ":" + newUid + " \"" + dataPath + "\"",
                                            "restorecon -RF \"" + dataPath + "\"",
                                            "if [ -d \"" + extBackupPath + "\" ]; then rm -rf \"" + extDataPath + "\"; cp -a \"" + extBackupPath + "\" \"" + extDataPath + "\"; chown -R " + newUid + ":" + newUid + " \"" + extDataPath + "\"; restorecon -RF \"" + extDataPath + "\"; fi",
                                            "rm -rf \"" + backupPath + "\"",
                                            "rm -rf \"" + extBackupPath + "\""
                                    ).exec();
                                    
                                    if (restoreResult.isSuccess()) {
                                        runOnUiThread(() -> {
                                            statusText.setText(R.string.data_restore_success);
                                        });
                                    } else {
                                        runOnUiThread(() -> {
                                            statusText.setText(R.string.data_restore_warning);
                                        });
                                    }
                                }
                                
                                result = retryResult; // Update result for final handling
                            } else {
                                // Cleanup backup on uninstall failure
                                Shell.cmd(
                                        "rm -rf \"" + backupPath + "\"",
                                        "rm -rf \"" + extBackupPath + "\""
                                ).exec();
                                
                                runOnUiThread(() -> {
                                    String uninstallError = uninstallResult.getOut().isEmpty() ? 
                                            "Unknown error during uninstall" : 
                                            String.join("\n", uninstallResult.getOut());
                                    statusText.setText(getString(R.string.uninstall_error, uninstallError));
                                    Toast.makeText(MainActivity.this, getString(R.string.uninstall_error, uninstallError), Toast.LENGTH_LONG).show();
                                    installButton.setEnabled(true);
                                    selectButton.setEnabled(true);
                                });
                                return;
                            }
                        } else {
                            runOnUiThread(() -> {
                                statusText.setText("Could not extract package name. Unable to uninstall.");
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
