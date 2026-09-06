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
    // On check, the app resolveUsername + auto-joins this public channel when needed (users need not join manually).
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

    private void onGetMessageSuccess(TLObject response, Delegate delegate) {
        var tag = "#" + getTag();
        final var res = (TLRPC.messages_Messages) response;
        getMessagesController().removeDeletedMessagesFromArray(CHANNEL_METADATA_ID, res.messages);
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
        onLoadSuccess(responses, delegate);
    }

    public static boolean isMetadataChannelConfigured() {
        return CHANNEL_METADATA_ID != 0L;
    }

    public void load() {
        load(false, null);
    }

    public void load(Delegate delegate) {
        load(false, delegate);
    }

    private void load(boolean forceRefreshAccessHash, Delegate delegate) {
        load(forceRefreshAccessHash, false, delegate);
    }

    /**
     * @param accessEnsured true after one resolve+join retry this check (avoids loops)
     */
    private void load(boolean forceRefreshAccessHash, boolean accessEnsured, Delegate delegate) {
        if (!isMetadataChannelConfigured()) {
            reportError("updater_not_configured", delegate);
            return;
        }
        var tag = "#" + getTag();
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 10;
        req.offset_id = 0;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.q = tag;
        req.peer = getMessagesController().getInputPeer(-CHANNEL_METADATA_ID);

        Runnable search = () -> getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                if (!accessEnsured) {
                    // Stale access_hash / no membership → resolve, join public metadata, retry once
                    load(true, true, delegate);
                    return;
                }
                reportError(error.text, delegate);
                return;
            }
            final var res = (TLRPC.messages_Messages) response;
            boolean empty = res.messages == null || res.messages.isEmpty();
            if (empty && !accessEnsured) {
                // Non-members often get an empty search → join + refresh access_hash + retry once.
                // Skip retry when we already know the account is a participant (genuine "latest").
                TLRPC.Chat known = getMessagesController().getChat(CHANNEL_METADATA_ID);
                if (known == null || ChatObject.isNotInChat(known)) {
                    load(true, true, delegate);
                    return;
                }
            }
            onGetMessageSuccess(response, delegate);
        });

        if (req.peer == null || req.peer.access_hash == 0 || forceRefreshAccessHash) {
            TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
            resolve.username = CHANNEL_METADATA_NAME;
            getConnectionsManager().sendRequest(resolve, (response1, error1) -> {
                if (error1 != null) {
                    reportError(error1.text, delegate);
                    return;
                }
                if (!(response1 instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer)) {
                    reportError("USERNAME_NOT_RESOLVED", delegate);
                    return;
                }
                getMessagesController().putUsers(resolvedPeer.users, false);
                getMessagesController().putChats(resolvedPeer.chats, false);
                getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, false, true);
                if (resolvedPeer.chats == null || resolvedPeer.chats.isEmpty()) {
                    reportError("CHANNEL_INVALID", delegate);
                    return;
                }
                TLRPC.Chat chat = resolvedPeer.chats.get(0);
                req.peer = new TLRPC.TL_inputPeerChannel();
                req.peer.channel_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                if (accessEnsured || ChatObject.isNotInChat(chat)) {
                    joinPublicMetadataChannel(chat, search, delegate);
                } else {
                    search.run();
                }
            });
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(CHANNEL_METADATA_ID);
            if (accessEnsured || (chat != null && ChatObject.isNotInChat(chat))) {
                if (chat == null) {
                    load(true, accessEnsured, delegate);
                    return;
                }
                joinPublicMetadataChannel(chat, search, delegate);
            } else {
                search.run();
            }
        }
    }

    /**
     * Auto-join the public metadata channel only. Never attempts private channels.
     */
    private void joinPublicMetadataChannel(TLRPC.Chat chat, Runnable onJoined, Delegate delegate) {
        if (chat == null || !ChatObject.isChannel(chat)) {
            reportError("CHANNEL_INVALID", delegate);
            return;
        }
        if (!ChatObject.isNotInChat(chat)) {
            onJoined.run();
            return;
        }
        // Public metadata only — never auto-join a private channel
        if (chat.id != CHANNEL_METADATA_ID && !ChatObject.isPublic(chat)) {
            reportError("CHANNEL_PRIVATE", delegate);
            return;
        }
        TLRPC.TL_channels_joinChannel join = new TLRPC.TL_channels_joinChannel();
        join.channel = MessagesController.getInputChannel(chat);
        if (join.channel == null || join.channel.access_hash == 0) {
            reportError("CHANNEL_INVALID", delegate);
            return;
        }
        getConnectionsManager().sendRequest(join, (response, error) -> {
            if (error != null) {
                if ("USER_ALREADY_PARTICIPANT".equals(error.text)) {
                    chat.left = false;
                    getMessagesController().putChat(chat, false);
                    onJoined.run();
                    return;
                }
                reportError(error.text, delegate);
                return;
            }
            if (response instanceof TLRPC.Updates) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
            chat.left = false;
            getMessagesController().putChat(chat, false);
            onJoined.run();
        });
    }

    public interface Delegate {
        void onTLResponse(TLRPC.TL_help_appUpdate res, String error);
    }
}
