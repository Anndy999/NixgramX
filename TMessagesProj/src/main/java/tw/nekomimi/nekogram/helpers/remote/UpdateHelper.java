package tw.nekomimi.nekogram.helpers.remote;

import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.nextalone.nagram.NaConfig;

public class UpdateHelper extends BaseRemoteHelper {

    public static final int UPDATE_OFF = 0;
    public static final int UPDATE_CHANNEL_RELEASE = 1;
    public static final int UPDATE_CHANNEL_BETA = 2;
    private boolean updateAlways = false;
    private int checkAccount;
    private int checkChannel;
    /** Survives getShouldUpdateVersion clearing updateAlways; lets manual checks show dialog when AutoUpdateChannel==OFF. */
    private boolean manualCheckPending = false;

    public static boolean isChannelConfigured() {
        return CHANNEL_METADATA_ID != 0L;
    }

    public static boolean isAutoCheckEnabled() {
        return isChannelConfigured() && NaConfig.INSTANCE.getAutoUpdateChannel().Int() != UPDATE_OFF;
    }

    /**
     * LaunchActivity pending-update policy for a checkAppUpdate callback:
     * A) res==null && error!=null (query failed) → keep SharedConfig.pendingAppUpdate
     * B) res==null && error==null (success, no update) → clear via setNewAppVersionAvailable(null)
     * When res!=null the caller stores the new update instead.
     */
    public static boolean shouldClearPendingAppUpdate(TLRPC.TL_help_appUpdate res, String error) {
        return res == null && error == null;
    }

    /** Apply check outcome to the device-wide pending update without clearing on failures. */
    public static void applyPendingUpdateCheckResult(TLRPC.TL_help_appUpdate res, String error) {
        if (res != null) {
            SharedConfig.setNewAppVersionAvailable(res);
        } else if (shouldClearPendingAppUpdate(res, error)) {
            SharedConfig.setNewAppVersionAvailable(null);
        }
    }

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    public static void cleanAppUpdate() {
        if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.document != null) {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            if (path != null && path.exists()) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (!path.delete()) path.deleteOnExit();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        SharedConfig.pendingAppUpdate = null;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.UPDATE_FAILED, 0);
        manualCheckPending = false;
        if (delegate != null) {
            delegate.onTLResponse(null, text);
        }
    }

    /**
     * Official Telegram beta always hits the beta endpoint. NixgramX mirrors that:
     * a beta/staging APK looks at {@code #updateBeta} unless the user explicitly
     * picked the Release auto-check lane. {@code #updateDebug} is never published.
     */
    public static boolean isBetaApk() {
        return BuildConfig.DEBUG || "beta".equals(BuildConfig.NIXGRAMX_CHANNEL);
    }

    @Override
    protected String getTag() {
        return getTag(NaConfig.INSTANCE.getAutoUpdateChannel().Int());
    }

    private static String getTag(int channel) {
        if (channel == UPDATE_CHANNEL_BETA) {
            return "updateBeta";
        }
        if (channel == UPDATE_CHANNEL_RELEASE) {
            return "updateRelease";
        }
        return isBetaApk() ? "updateBeta" : "updateRelease";
    }

    @SuppressWarnings("ConstantConditions")
    private int getPreferredAbiFile(Map<String, Integer> files) {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (files.containsKey(abi)) {
                return files.get(abi);
            }
        }
        return files.getOrDefault("universal", files.get("arm64-v8a"));
    }

    private Map<String, Integer> jsonToMap(JSONObject obj) {
        Map<String, Integer> map = new HashMap<>();
        List<String> abis = new ArrayList<>();
        abis.add("arm64-v8a");
        abis.add("universal");
        try {
            for (var abi : abis) {
                map.put(abi, obj.getInt(abi));
            }
        } catch (JSONException ignored) {
        }
        return map;
    }

    private Update getShouldUpdateVersion(List<JSONObject> responses) {
        int currentVersion = BuildConfig.VERSION_CODE;
        long buildTimestamp = BuildConfig.BUILD_TIMESTAMP;
        Update ref = null;
        for (var string : responses) {
            try {
                int remoteVersion = string.getInt("version_code");
                org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.UPDATE_VERSION, remoteVersion);
                long remoteBuildTimestamp = string.optLong("build_timestamp", 0L);
                boolean shouldUpdate = false;
                if (remoteVersion > currentVersion) {
                    shouldUpdate = true;
                } else if (remoteVersion == currentVersion && remoteBuildTimestamp > buildTimestamp) {
                    shouldUpdate = true;
                }
                if (shouldUpdate || updateAlways) {
                    if (updateAlways) {
                        updateAlways = false;
                    }
                    ref = new Update(
                            string.getBoolean("can_not_skip"),
                            string.getString("version"),
                            remoteVersion,
                            string.getInt("sticker"),
                            string.getInt("message"),
                            jsonToMap(string.getJSONObject("document")),
                            string.getString("url")
                    );
                    break;
                }
            } catch (JSONException ignored) {
                org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.UPDATE_PARSE_FAILED, 0);
            }
        }
        return ref;
    }

    private void getNewVersionMessagesCallback(Delegate delegate, Update json, HashMap<String, Integer> ids, TLObject response) {
        var update = new TLRPC.TL_help_appUpdate();
        update.version = json.version;
        update.can_not_skip = json.canNotSkip;
        if (json.url != null) {
            update.url = json.url;
            update.flags |= 4;
        }
        // Manual long-press check must still show the dialog when AutoUpdateChannel is OFF.
        // updateAlways is cleared earlier in getShouldUpdateVersion; use manualCheckPending instead.
        if (checkChannel == UPDATE_OFF && !update.can_not_skip && !manualCheckPending) {
            delegate.onTLResponse(null, null);
            return;
        }
        manualCheckPending = false;
        if (response != null) {
            var res = (TLRPC.messages_Messages) response;
            MessagesController.getInstance(checkAccount).removeDeletedMessagesFromArray(CHANNEL_METADATA_ID, res.messages);
            var messages = new HashMap<Integer, TLRPC.Message>();
            for (var message : res.messages) {
                messages.put(message.id, message);
            }
            if (ids.containsKey("sticker")) {
                var sticker = messages.get(ids.get("sticker"));
                if (sticker != null && sticker.media != null) {
                    update.sticker = sticker.media.document;
                    update.flags |= 8;
                }
            }
            if (ids.containsKey("message")) {
                var message = messages.get(ids.get("message"));
                if (message != null) {
                    update.text = message.message;
                    update.entities = message.entities;
                }
            }
            if (ids.containsKey("document")) {
                var file = messages.get(ids.get("document"));
                if (file != null && file.media != null) {
                    update.document = file.media.document;
                    update.flags |= 2;
                }
            }
        }
        delegate.onTLResponse(update, null);
    }

    @Override
    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate,
                                 int account, TLRPC.InputChannel channel) {
        if (responses.isEmpty()) {
            onError("UPDATE_METADATA_EMPTY", delegate);
            return;
        }
        var update = getShouldUpdateVersion(responses);
        if (update == null) {
            manualCheckPending = false;
            delegate.onTLResponse(null, null);
            return;
        }
        var ids = new HashMap<String, Integer>();
        // sticker/message id 0 is a placeholder — never fetch getMessages(id=0).
        // With a real positive sticker id, getNewVersionMessagesCallback sets
        // update.sticker (document) and flags |= 8 so UpdateAppAlertDialog shows the duck.
        if (update.sticker != null && update.sticker > 0) {
            ids.put("sticker", update.sticker);
        }
        if (update.message != null && update.message > 0) {
            ids.put("message", update.message);
        }
        if (update.document != null && !update.document.isEmpty()) {
            Integer documentId = getPreferredAbiFile(update.document);
            if (documentId != null && documentId > 0) {
                ids.put("document", documentId);
            }
        }
        if (ids.isEmpty()) {
            getNewVersionMessagesCallback(delegate, update, null, null);
        } else {
            var req = new TLRPC.TL_channels_getMessages();
            req.channel = channel;
            req.id = new ArrayList<>(ids.values());
            ConnectionsManager.getInstance(account).sendRequest(req, (response1, error1) -> {
                if (error1 == null) {
                    getNewVersionMessagesCallback(delegate, update, ids, response1);
                } else {
                    delegate.onTLResponse(null, error1.text);
                }
            });
        }
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        checkNewVersionAvailable(delegate, updateAlways, false);
    }

    /**
     * @param updateAlways show dialog even when remote is not newer
     * @param manualUserCheck user-initiated check (e.g. long-press); must not be swallowed when AutoUpdateChannel==OFF
     */
    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways, boolean manualUserCheck) {
        final int account = UserConfig.selectedAccount;
        final int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (!isChannelConfigured()) {
            if (delegate != null) {
                delegate.onTLResponse(null, "updater_not_configured");
            }
            return;
        }
        if (!updateAlways && !manualUserCheck && channel == UPDATE_OFF) {
            if (delegate != null) {
                delegate.onTLResponse(null, null);
            }
            return;
        }
        org.telegram.messenger.diagnostics.Diagnostics.event(org.telegram.messenger.diagnostics.Diagnostics.Event.UPDATE_CHECK, BuildConfig.VERSION_CODE);
        // Each invocation owns its flags and account, including overlapping manual/background checks.
        var check = new UpdateHelper();
        check.checkAccount = account;
        check.checkChannel = channel;
        check.updateAlways = updateAlways;
        check.manualCheckPending = updateAlways || manualUserCheck;
        check.load(account, getTag(channel), delegate);
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }

    public static class Update {
        public Boolean canNotSkip;
        public String version;
        public Integer versionCode;
        public Integer sticker;
        public Integer message;
        public Map<String, Integer> document;
        public String url;

        public Update(Boolean canNotSkip, String version, int versionCode, int sticker, int message, Map<String, Integer> document, String url) {
            this.canNotSkip = canNotSkip;
            this.version = version;
            this.versionCode = versionCode;
            this.sticker = sticker;
            this.message = message;
            this.document = document;
            this.url = url;
        }
    }
}
