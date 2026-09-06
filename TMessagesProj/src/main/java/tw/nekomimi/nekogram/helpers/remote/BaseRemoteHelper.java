package tw.nekomimi.nekogram.helpers.remote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public abstract class BaseRemoteHelper {
    // CHANNEL_METADATA_* = second PUBLIC metadata channel (@NixgramXMetadata) that receives
    // #updateRelease / #updateBeta JSON (HELPER_BOT_CANARY_TARGET). Public @NixgramX is for APKs only.
    // Must stay PUBLIC so logged-in user accounts can messages.search; a private channel breaks checks.
    // ID=0 → updater_not_configured (isMetadataChannelConfigured). After the first successful
    // publish to @NixgramXMetadata, paste the positive CHANNEL_METADATA_ID candidate from upload.py log.
    // Do NOT use 3819693045 (that is the public APK channel). Do not point at NagramX author endpoints.
    // Resolve and search the public channel without requiring membership or joining it.
    public static final long CHANNEL_METADATA_ID = 4419000687L;
    public static final String CHANNEL_METADATA_NAME = "NixgramXMetadata";

    protected static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("nekoremoteconfig", Activity.MODE_PRIVATE);

    protected MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    protected ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    protected MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    protected FileLoader getFileLoader() {
        return FileLoader.getInstance(UserConfig.selectedAccount);
    }

    abstract protected void onError(String text, Delegate delegate);

    abstract protected String getTag();

    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate) {
        var tag = getTag();
        var json = responses.size() > 0 ? responses.get(0) : null;
        if (json == null) {
            preferences.edit()
                    .remove(tag + "_update_time")
                    .remove(tag)
                    .apply();
        } else {
            preferences.edit()
                    .putLong(tag + "_update_time", System.currentTimeMillis())
                    .putString(tag, json.toString())
                    .apply();
        }
    }

    private void reportError(String text, Delegate delegate) {
        if (delegate != null) {
            onError(text, delegate);
        }
    }

    private void onGetMessageSuccess(TLObject response, Delegate delegate, int account, String requestTag, TLRPC.InputChannel channel) {
        var tag = "#" + requestTag;
        final var res = (TLRPC.messages_Messages) response;
        MessagesController.getInstance(account).removeDeletedMessagesFromArray(CHANNEL_METADATA_ID, res.messages);
        ArrayList<JSONObject> responses = new ArrayList<>();
        for (var message : res.messages) {
            if (TextUtils.isEmpty(message.message) || !message.message.startsWith(tag)) {
                continue;
            }
            try {
                responses.add(new JSONObject(message.message.substring(tag.length()).trim()));
            } catch (JSONException e) {
                FileLog.e(e);
            }
        }
        onLoadSuccess(responses, delegate, account, channel);
    }

    public static boolean isMetadataChannelConfigured() {
        return CHANNEL_METADATA_ID != 0L;
    }

    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate,
                                 int account, TLRPC.InputChannel channel) {
        onLoadSuccess(responses, delegate);
    }

    public void load() {
        load(null);
    }

    public void load(Delegate delegate) {
        load(UserConfig.selectedAccount, getTag(), delegate);
    }

    protected void load(int account, String tag, Delegate delegate) {
        if (!isMetadataChannelConfigured()) {
            reportError("updater_not_configured", delegate);
            return;
        }
        resolveAndSearch(account, tag, delegate, false);
    }

    private void resolveAndSearch(int account, String tag, Delegate delegate, boolean retried) {
        var controller = MessagesController.getInstance(account);
        var connections = ConnectionsManager.getInstance(account);
        var resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = CHANNEL_METADATA_NAME;
        connections.sendRequest(resolve, (response, error) -> {
            if (error != null) {
                reportError(error.text, delegate);
                return;
            }
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer resolved)) {
                reportError("USERNAME_NOT_RESOLVED", delegate);
                return;
            }
            TLRPC.Chat metadata = null;
            for (var chat : resolved.chats) {
                if (chat.id == CHANNEL_METADATA_ID && ChatObject.isChannel(chat)) {
                    metadata = chat;
                    break;
                }
            }
            if (metadata == null || metadata.access_hash == 0) {
                reportError("CHANNEL_INVALID", delegate);
                return;
            }
            controller.putUsers(resolved.users, false);
            controller.putChats(resolved.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(resolved.users, resolved.chats, false, true);
            // Carry the resolved hash through search and getMessages; neither needs a cached chat.
            TLRPC.InputChannel channel = MessagesController.getInputChannel(metadata);
            var req = new TLRPC.TL_messages_search();
            req.limit = 10;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.q = "#" + tag;
            req.peer = new TLRPC.TL_inputPeerChannel();
            req.peer.channel_id = channel.channel_id;
            req.peer.access_hash = channel.access_hash;
            connections.sendRequest(req, (searchResponse, searchError) -> {
                boolean empty = searchResponse instanceof TLRPC.messages_Messages messages
                        && (messages.messages == null || messages.messages.isEmpty());
                if ((searchError != null || empty) && !retried) {
                    // Local membership is not proof that search returned complete metadata.
                    resolveAndSearch(account, tag, delegate, true);
                } else if (searchError != null) {
                    reportError(searchError.text, delegate);
                } else if (empty) {
                    reportError("UPDATE_METADATA_EMPTY", delegate);
                } else if (!(searchResponse instanceof TLRPC.messages_Messages)) {
                    reportError("UPDATE_METADATA_INVALID", delegate);
                } else {
                    onGetMessageSuccess(searchResponse, delegate, account, tag, channel);
                }
            });
        });
    }

    public interface Delegate {
        void onTLResponse(TLRPC.TL_help_appUpdate res, String error);
    }
}
