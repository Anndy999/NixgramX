package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import tw.nekomimi.nekogram.utils.GsonUtil;

public class CloudSettingsHelper {
    public static final SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> CloudSettingsHelper.getInstance().doAutoSync(key);
    private static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Context.MODE_PRIVATE);
    private final SparseArray<Long> cloudSyncedDate = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseBooleanArray cloudOperations = new SparseBooleanArray();
    private boolean applyingCloudSettings;
    private int autoSyncAccount;
    private boolean autoSync = preferences.getBoolean("auto_sync", false);

    private static final String SETTINGS_MANIFEST_KEY = "neko_settings_v2_manifest";
    private static final String SETTINGS_CHUNKS_COUNT_KEY = "neko_settings";
    private static final String SETTINGS_CHUNK_KEY_PREFIX = "neko_settings_";
    private static final String SETTINGS_UPDATED_AT_KEY = "neko_settings_updated_at";
    private static final String SETTINGS_ENCODING_KEY = "neko_settings_encoding";
    private static final String SETTINGS_ENCODING_GZIP_BASE64_V1 = "gzip_base64_v1";
    private static final int MAX_CHUNK_CHARS = 3000;
    private static final int RESTORE_BATCH_SIZE = 50;
    private static final int MAX_CHUNKS = 1024;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;

    private final Runnable cloudSyncRunnable = () -> syncToCloud(autoSyncAccount, (success, error) -> {
        if (!success) {
            var global = BulletinFactory.global();
            if (error == null) {
                global.createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed)).show();
            } else {
                global.createSimpleBulletin(R.raw.error, getString(R.string.CloudConfigSyncFailed), error).show();
            }
        }
    });

    public static CloudSettingsHelper getInstance() {
        return InstanceHolder.instance;
    }

    private static String formatDateUntil(long date) {
        try {
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (year == dateYear) {
                return LocaleController.getInstance().getFormatterBannedUntilThisYear().format(new Date(date));
            } else {
                return LocaleController.getInstance().getFormatterBannedUntil().format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public void showDialog(BaseFragment parentFragment) {
        if (parentFragment == null) {
            return;
        }

        Context context = parentFragment.getParentActivity();
        if (context == null) return;
        Theme.ResourcesProvider resourcesProvider = parentFragment.getResourceProvider();
        int selectedAccount = parentFragment.getCurrentAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.CloudConfig));
        builder.setMessage(AndroidUtilities.replaceTags(getString(R.string.CloudConfigDesc)));
        builder.setTopImage(R.drawable.cloud, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        builder.setTopBackgroundColorKey(Theme.key_featuredStickers_addButton, resourcesProvider);

        TextViewSwitcher syncedDate = new TextViewSwitcher(context);
        syncedDate.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setGravity(Gravity.START);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            ThemeBind.textColor(tv, Theme.key_dialogTextGray3, resourcesProvider);
            return tv;
        });
        syncedDate.setInAnimation(context, R.anim.alpha_in);
        syncedDate.setOutAnimation(context, R.anim.alpha_out);
        syncedDate.setText(formatSyncedDate(selectedAccount), false);

        ButtonWithCounterView syncButton = new ButtonWithCounterView(context, true, resourcesProvider).setRound();
        syncButton.setText(getString(R.string.CloudConfigSync), false);
        ButtonWithCounterView restoreButton = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        restoreButton.setText(getString(R.string.CloudConfigRestore), false);
        ButtonWithCounterView deleteButton = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        deleteButton.setText(getString(R.string.DeleteCloudBackup), false);
        deleteButton.setTextColorKey(Theme.key_dialogTextRed);

        class Actions {
            boolean busy = true;
            boolean hasBackup;
            boolean dismissed;

            void update() {
                syncButton.setEnabled(!busy);
                restoreButton.setEnabled(!busy && hasBackup);
                deleteButton.setEnabled(!busy && hasBackup);
                syncedDate.setText(busy ? getString(R.string.Loading) : formatSyncedDate(selectedAccount));
            }

            boolean begin() {
                if (busy || dismissed) return false;
                busy = true;
                update();
                return true;
            }
        }
        Actions actions = new Actions();
        actions.update();
        readSnapshot(CloudStorageHelper.getInstance(selectedAccount), (snapshot, error) -> {
            if (actions.dismissed) return;
            cloudSyncedDate.put(selectedAccount, snapshot == null ? -1L : snapshot.updatedAt());
            actions.hasBackup = snapshot != null;
            actions.busy = false;
            actions.update();
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(syncButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 0, 16, 0));
        linearLayout.addView(restoreButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));
        linearLayout.addView(deleteButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));
        syncButton.setOnClickListener(view -> {
            if (!actions.begin()) return;
            syncToCloud(selectedAccount, (success, error) -> {
                if (actions.dismissed) return;
                actions.busy = false;
                actions.hasBackup |= success;
                actions.update();
                if (!success) showCloudResult(context, resourcesProvider, R.raw.error, R.string.CloudConfigSyncFailed, error);
            });
        });
        restoreButton.setOnClickListener(view -> {
            if (!actions.hasBackup || !actions.begin()) return;
            restoreFromCloud(selectedAccount, (success, error) -> {
                if (actions.dismissed) return;
                actions.busy = false;
                actions.update();
                if (!success) {
                    showCloudResult(context, resourcesProvider, R.raw.error, R.string.CloudConfigRestoreFailed, error);
                } else {
                    AlertDialog restart = new AlertDialog(context, 0);
                    restart.setTitle(getString(R.string.NagramX));
                    restart.setMessage(getString(R.string.RestartAppToTakeEffect));
                    restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
                    restart.show();
                }
            });
        });
        deleteButton.setOnClickListener(view -> {
            if (!actions.hasBackup || !actions.begin()) return;
            deleteCloudBackup(selectedAccount, (success, error) -> {
                if (actions.dismissed) return;
                actions.busy = false;
                if (success || error == null) actions.hasBackup = false;
                actions.update();
                if (success) {
                    showCloudResult(context, resourcesProvider, R.raw.done, R.string.DeleteCloudBackupSuccess, null);
                } else if (error == null) {
                    showCloudResult(context, resourcesProvider, R.raw.info, R.string.CloudConfigNoBackupToDelete, null);
                } else {
                    showCloudResult(context, resourcesProvider, R.raw.error, R.string.DeleteCloudBackupFailed, error);
                }
            });
        });

        MiniCheckBoxCell autoSyncCheck = new MiniCheckBoxCell(context, 8, resourcesProvider);
        autoSyncCheck.setTextAndValueAndCheck(getString(R.string.CloudConfigAutoSync), getString(R.string.CloudConfigAutoSyncDesc), autoSync);
        autoSyncCheck.setOnClickListener(view13 -> {
            autoSync = !autoSync;
            preferences.edit().putBoolean("auto_sync", autoSync).apply();
            if (!autoSync) handler.removeCallbacks(cloudSyncRunnable);
            autoSyncCheck.setChecked(autoSync);
        });
        linearLayout.addView(autoSyncCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

        linearLayout.addView(syncedDate, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 0));

        builder.setView(linearLayout);
        AlertDialog dialog = builder.create();
        parentFragment.showDialog(dialog, dismissed -> actions.dismissed = true);
    }

    private static void showCloudResult(Context context, Theme.ResourcesProvider resources, int animation, int message, String error) {
        var factory = BulletinFactory.of(Bulletin.BulletinWindow.make(context), resources);
        if (error == null) factory.createSimpleBulletin(animation, getString(message)).show();
        else factory.createSimpleBulletin(animation, getString(message), error).show();
    }

    private void doAutoSync(String key) {
        if (!autoSync || applyingCloudSettings || !SettingsBackupHelper.isCloudNekoKey(key)) return;
        autoSyncAccount = UserConfig.selectedAccount;
        handler.removeCallbacks(cloudSyncRunnable);
        handler.postDelayed(cloudSyncRunnable, 1200);
    }

    private Utilities.Callback2<Boolean, String> beginOperation(int account, Utilities.Callback2<Boolean, String> callback) {
        if (cloudOperations.get(account)) {
            callback.run(false, getString(R.string.Loading));
            return null;
        }
        cloudOperations.put(account, true);
        return (success, error) -> {
            cloudOperations.delete(account);
            callback.run(success, error);
        };
    }

    private record Snapshot(String id, int count, String encoding, long updatedAt) {
        String chunkKey(int index) {
            return id == null ? SETTINGS_CHUNK_KEY_PREFIX + index : SETTINGS_CHUNK_KEY_PREFIX + id + "_" + index;
        }
    }

    private static Snapshot parseSnapshot(Map<String, String> values) throws Exception {
        String manifest = values.get(SETTINGS_MANIFEST_KEY);
        if (!TextUtils.isEmpty(manifest)) {
            JSONObject json = new JSONObject(manifest);
            String id = json.getString("id");
            int count = json.getInt("count");
            long updatedAt = json.getLong("updated_at");
            if (!id.matches("[0-9a-f]{32}") || count <= 0 || count > MAX_CHUNKS || updatedAt <= 0) {
                throw new IllegalArgumentException("Invalid backup manifest");
            }
            return new Snapshot(id, count, SETTINGS_ENCODING_GZIP_BASE64_V1, updatedAt);
        }
        String legacyCount = values.get(SETTINGS_CHUNKS_COUNT_KEY);
        if (TextUtils.isEmpty(legacyCount)) return null;
        int count = Integer.parseInt(legacyCount);
        if (count <= 0 || count > MAX_CHUNKS) throw new IllegalArgumentException("Invalid backup chunk count");
        String timestamp = values.get(SETTINGS_UPDATED_AT_KEY);
        return new Snapshot(null, count, values.get(SETTINGS_ENCODING_KEY),
                AndroidUtilities.isNumeric(timestamp) ? Long.parseLong(timestamp) : -1);
    }

    private void readSnapshot(CloudStorageHelper storage, Utilities.Callback2<Snapshot, String> callback) {
        storage.getItems(new String[]{SETTINGS_MANIFEST_KEY, SETTINGS_CHUNKS_COUNT_KEY, SETTINGS_ENCODING_KEY,
                SETTINGS_UPDATED_AT_KEY}, (values, error) -> {
            if (error != null || values == null) {
                callback.run(null, error);
                return;
            }
            Snapshot snapshot;
            try {
                snapshot = parseSnapshot(values);
            } catch (Exception e) {
                callback.run(null, e.getLocalizedMessage());
                return;
            }
            callback.run(snapshot, null);
        });
    }

    private void syncToCloud(int account, Utilities.Callback2<Boolean, String> callback) {
        var complete = beginOperation(account, callback);
        if (complete == null) return;
        CloudStorageHelper storage = CloudStorageHelper.getInstance(account);
        readSnapshot(storage, (previous, readError) -> Utilities.globalQueue.postRunnable(() -> {
            try {
                String payload = gzipBase64Encode(SettingsBackupHelper.backupSettingsJson(true, 0));
                int count = (payload.length() + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS;
                if (count <= 0 || count > MAX_CHUNKS) throw new IllegalArgumentException("Cloud backup is too large");
                String id = UUID.randomUUID().toString().replace("-", "");
                long now = System.currentTimeMillis();
                Snapshot snapshot = new Snapshot(id, count, SETTINGS_ENCODING_GZIP_BASE64_V1, now);
                String manifest = new JSONObject().put("id", id).put("count", count).put("updated_at", now).toString();
                AndroidUtilities.runOnUIThread(() -> syncChunk(storage, snapshot, payload, 0, error -> {
                    if (error != null) {
                        removeChunks(storage, snapshot);
                        complete.run(false, error);
                        return;
                    }
                    storage.setItem(SETTINGS_MANIFEST_KEY, manifest, (response, commitError) -> {
                        if (commitError != null) {
                            complete.run(false, commitError);
                            return;
                        }
                        cloudSyncedDate.put(account, now);
                        preferences.edit().putLong("updated_at_" + account, now).apply();
                        if (previous != null && previous.id() != null) removeChunks(storage, previous);
                        complete.run(true, null);
                    });
                }));
            } catch (Exception error) {
                AndroidUtilities.runOnUIThread(() -> complete.run(false, error.getLocalizedMessage()));
            }
        }));
    }

    private void syncChunk(CloudStorageHelper storage, Snapshot snapshot, String payload, int index,
                           Utilities.Callback<String> callback) {
        if (index == snapshot.count()) {
            callback.run(null);
            return;
        }
        int start = index * MAX_CHUNK_CHARS;
        String chunk = payload.substring(start, Math.min(start + MAX_CHUNK_CHARS, payload.length()));
        storage.setItem(snapshot.chunkKey(index), chunk, (res, error) -> {
            if (error != null) callback.run(error);
            else syncChunk(storage, snapshot, payload, index + 1, callback);
        });
    }

    private void removeChunks(CloudStorageHelper storage, Snapshot snapshot) {
        for (int start = 0; start < snapshot.count(); start += RESTORE_BATCH_SIZE) {
            int end = Math.min(start + RESTORE_BATCH_SIZE, snapshot.count());
            String[] keys = new String[end - start];
            for (int i = start; i < end; i++) keys[i - start] = snapshot.chunkKey(i);
            storage.removeItems(keys, null);
        }
    }

    private void restoreFromCloud(int account, Utilities.Callback2<Boolean, String> callback) {
        var complete = beginOperation(account, callback);
        if (complete == null) return;
        CloudStorageHelper storage = CloudStorageHelper.getInstance(account);
        readSnapshot(storage, (snapshot, metaError) -> {
            if (snapshot == null || metaError != null) {
                complete.run(false, metaError);
                return;
            }
            fetchChunksFromCloud(storage, snapshot, 0, new StringBuilder(), (payload, chunksError) -> {
                if (chunksError != null || payload == null) {
                    complete.run(false, chunksError);
                    return;
                }
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        String json = SETTINGS_ENCODING_GZIP_BASE64_V1.equals(snapshot.encoding()) ? gzipBase64Decode(payload) : payload;
                        var settings = GsonUtil.toJsonObject(json);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (account != UserConfig.selectedAccount) {
                                complete.run(false, null);
                                return;
                            }
                            applyingCloudSettings = true;
                            try {
                                SettingsBackupHelper.importCloudSettings(settings);
                                preferences.edit().putLong("updated_at_" + account, System.currentTimeMillis()).apply();
                                complete.run(true, null);
                            } catch (Exception e) {
                                complete.run(false, e.getLocalizedMessage());
                            } finally {
                                applyingCloudSettings = false;
                            }
                        });
                    } catch (Exception e) {
                        AndroidUtilities.runOnUIThread(() -> complete.run(false, e.getLocalizedMessage()));
                    }
                });
            });
        });
    }

    private void fetchChunksFromCloud(CloudStorageHelper storage, Snapshot snapshot, int offset, StringBuilder sb,
                                      Utilities.Callback2<String, String> callback) {
        int count = snapshot.count();
        if (offset >= count) {
            callback.run(sb.toString(), null);
            return;
        }
        int end = Math.min(offset + RESTORE_BATCH_SIZE, count);
        String[] keys = new String[end - offset];
        for (int i = offset; i < end; i++) keys[i - offset] = snapshot.chunkKey(i);
        storage.getItems(keys, (values, error) -> {
            if (error != null || values == null) {
                callback.run(null, error);
                return;
            }
            for (String key : keys) {
                String chunk = values.get(key);
                if (chunk == null || chunk.length() > MAX_CHUNK_CHARS) {
                    callback.run(null, "Invalid cloud backup chunk");
                    return;
                }
                sb.append(chunk);
            }
            fetchChunksFromCloud(storage, snapshot, end, sb, callback);
        });
    }

    private static String gzipBase64Encode(String input) throws Exception {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(inputBytes);
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    @SuppressWarnings("StringOperationCanBeSimplified") // API 33
    private static String gzipBase64Decode(String input) throws Exception {
        byte[] compressed = Base64.decode(input, Base64.DEFAULT);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                if (baos.size() + read > MAX_JSON_BYTES) throw new IllegalArgumentException("Cloud backup is too large");
                baos.write(buffer, 0, read);
            }
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void deleteCloudBackup(int account, Utilities.Callback2<Boolean, String> callback) {
        var complete = beginOperation(account, callback);
        if (complete == null) return;
        CloudStorageHelper storage = CloudStorageHelper.getInstance(account);
        storage.getKeys((keys, error) -> {
            if (error != null) {
                complete.run(false, error);
                return;
            }
            if (keys == null || keys.length == 0) {
                complete.run(false, null);
                return;
            }

            ArrayList<String> nekoKeys = new ArrayList<>();
            for (String key : keys) {
                if (key.startsWith("neko_settings")) {
                    nekoKeys.add(key);
                }
            }

            if (nekoKeys.isEmpty()) {
                complete.run(false, null);
                return;
            }

            storage.removeItems(nekoKeys.toArray(new String[0]), (res_, error_) -> {
                if (error_ == null) {
                    cloudSyncedDate.put(account, -1L);
                    complete.run(true, null);
                } else {
                    complete.run(false, error_);
                }
            });
        });
    }

    private String formatSyncedDate(int account) {
        long local = preferences.getLong("updated_at_" + account, -1);
        Long cloud = cloudSyncedDate.get(account, -1L);
        return LocaleController.formatString(R.string.CloudConfigSyncDate,
                local > 0 ? formatDateUntil(local) : getString(R.string.CloudConfigSyncDateNever),
                cloud > 0 ? formatDateUntil(cloud) : getString(R.string.CloudConfigSyncDateNever));
    }

    private static final class InstanceHolder {
        private static final CloudSettingsHelper instance = new CloudSettingsHelper();
    }

    @SuppressLint("ViewConstructor")
    private static class MiniCheckBoxCell extends FrameLayout {

        private final TextView textView;
        private final TextView valueTextView;
        private final CheckBoxSquare checkBox;

        public MiniCheckBoxCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            ScaleStateListAnimator.apply(this, .02f, 1.2f);

            setForeground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 16, 16));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            textView = new TextView(context);
            ThemeBind.textColor(textView, Theme.key_dialogTextBlack, resourcesProvider);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            valueTextView = new TextView(context);
            ThemeBind.textColor(valueTextView, Theme.key_dialogIcon, resourcesProvider);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 22 + padding : padding, 4, LocaleController.isRTL ? padding : 22 + padding, 4));

            checkBox = new CheckBoxSquare(context, true, resourcesProvider);
            addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? padding : 4, 0, LocaleController.isRTL ? 4 : padding, 0));
        }

        public void setTextAndValueAndCheck(String text, String value, boolean checked) {
            textView.setText(text);
            valueTextView.setText(value);
            checkBox.setChecked(checked, false);
        }

        public boolean isChecked() {
            return checkBox.isChecked();
        }

        public void setChecked(boolean checked) {
            checkBox.setChecked(checked, true);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            AccessibilityNodeInfoCompat.wrap(info).setChecked(checkBox.isChecked()
                    ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE
                    : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
            StringBuilder sb = new StringBuilder();
            sb.append(textView.getText());
            if (!TextUtils.isEmpty(valueTextView.getText())) {
                sb.append('\n');
                sb.append(valueTextView.getText());
            }
            info.setContentDescription(sb);
        }
    }
}
