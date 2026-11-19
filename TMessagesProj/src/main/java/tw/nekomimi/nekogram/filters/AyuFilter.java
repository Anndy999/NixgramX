package tw.nekomimi.nekogram.filters;

import android.text.TextUtils;

import androidx.collection.LruCache;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import xyz.nextalone.nagram.NaConfig;

public class AyuFilter {
    private static final Object cacheLock = new Object();
    private static volatile RuleIndex ruleIndex;
    private static volatile long filterRevision;
    private static volatile ArrayList<FilterModel> filterModels;
    private static volatile ArrayList<ChatFilterEntry> chatFilterEntries;
    private static volatile HashSet<Long> excludedDialogs;
    private static volatile HashSet<Long> blockedChannels;
    private static volatile HashSet<Long> customFilteredUsers;
    private static volatile HashMap<Long, CustomFilteredUser> customFilteredUsersData;

    public static ArrayList<FilterModel> getRegexFilters() {
        if (filterModels == null) {
            synchronized (cacheLock) {
                if (filterModels == null) {
                    var str = NaConfig.INSTANCE.getRegexFiltersData().String();
                    FilterModel[] arr = new Gson().fromJson(str, FilterModel[].class);
                    if (arr != null) {
                        var parsed = new ArrayList<>(Arrays.asList(arr));
                        parsed.removeAll(Collections.singleton(null));
                        boolean migrated = false;
                        for (var filter : parsed) {
                            if (filter.migrateFromLegacy(0L)) {
                                migrated = true;
                            }
                            filter.buildPattern();
                        }
                        if (migrated) {
                            NaConfig.INSTANCE.getRegexFiltersData().setConfigString(new Gson().toJson(parsed));
                        }
                        filterModels = parsed;
                    } else {
                        filterModels = new ArrayList<>();
                    }
                }
            }
        }
        return filterModels;
    }

    public static void addFilter(String text, boolean caseInsensitive) {
        var list = new ArrayList<>(getRegexFilters());
        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.enabled = true;
        list.add(0, filterModel);
        saveFilter(list);
    }

    public static void editFilter(int filterIdx, String text, boolean caseInsensitive) {
        var list = new ArrayList<>(getRegexFilters());
        if (filterIdx < 0 || filterIdx >= list.size()) {
            return;
        }
        FilterModel filterModel = list.get(filterIdx);
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        saveFilter(list);
    }

    public static void saveFilter(ArrayList<FilterModel> filterModels1) {
        var str = new Gson().toJson(filterModels1);
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString(str);
        AyuFilter.rebuildCache();
    }

    public static void removeFilter(int filterIdx) {
        var list = new ArrayList<>(getRegexFilters());
        if (filterIdx < 0 || filterIdx >= list.size()) {
            return;
        }
        list.remove(filterIdx);
        saveFilter(list);
    }

    public static CharSequence getMessageText(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        if (selectedObject == null) {
            return null;
        }
        CharSequence messageText = null;
        if (!selectedObject.translated && !selectedObject.isRestrictedMessage && selectedObject.type != MessageObject.TYPE_EMOJIS && selectedObject.type != MessageObject.TYPE_ANIMATED_STICKER && selectedObject.type != MessageObject.TYPE_STICKER) {
            messageText = MessageHelper.getMessagePlainTextFull(selectedObject, selectedObjectGroup);
            if (TextUtils.isEmpty(messageText) || Emoji.fullyConsistsOfEmojis(messageText)) {
                messageText = null;
            }
        }
        CharSequence buttonsText = getInlineKeyboardText(selectedObject.messageOwner);
        if (TextUtils.isEmpty(messageText)) {
            return buttonsText;
        }
        if (TextUtils.isEmpty(buttonsText)) {
            return messageText;
        }
        return new StringBuilder(messageText).append('\n').append(buttonsText);
    }

    private static CharSequence getInlineKeyboardText(TLRPC.Message message) {
        if (message == null || message.reply_markup == null) {
            return null;
        }
        StringBuilder builder = null;
        if (message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            final ArrayList<TL_keyboard.KeyboardInlineButtonRow> rows = ((TLRPC.TL_replyInlineMarkup) message.reply_markup).rows;
            if (rows == null) {
                return null;
            }
            for (int i = 0, size = rows.size(); i < size; i++) {
                TL_keyboard.KeyboardInlineButtonRow row = rows.get(i);
                if (row == null || row.buttons == null) {
                    continue;
                }
                for (int j = 0, buttonsSize = row.buttons.size(); j < buttonsSize; j++) {
                    TL_keyboard.KeyboardInlineButton button = row.buttons.get(j);
                    if (button == null || TextUtils.isEmpty(button.getText())) {
                        continue;
                    }
                    if (builder == null) {
                        builder = new StringBuilder();
                    } else {
                        builder.append('\n');
                    }
                    builder.append(button.getText());
                }
            }
        } else if (message.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup) {
            final ArrayList<TL_keyboard.KeyboardButtonRow> rows = ((TLRPC.TL_replyKeyboardMarkup) message.reply_markup).rows;
            if (rows == null) {
                return null;
            }
            for (int i = 0, size = rows.size(); i < size; i++) {
                TL_keyboard.KeyboardButtonRow row = rows.get(i);
                if (row == null || row.buttons == null) {
                    continue;
                }
                for (int j = 0, buttonsSize = row.buttons.size(); j < buttonsSize; j++) {
                    TL_keyboard.KeyboardButton button = row.buttons.get(j);
                    if (button == null || TextUtils.isEmpty(button.getText())) {
                        continue;
                    }
                    if (builder == null) {
                        builder = new StringBuilder();
                    } else {
                        builder.append('\n');
                    }
                    builder.append(button.getText());
                }
            }
        }
        return builder;
    }

    public static void rebuildCache() {
        synchronized (cacheLock) {
            filterModels = null;
            chatFilterEntries = null;
            excludedDialogs = null;
            invalidateFilteredCache();
        }
    }

    public static void invalidateFilteredCache() {
        synchronized (cacheLock) {
            ruleIndex = null;
            filterRevision++;
        }
    }

    public static long getFilterRevision() {
        return filterRevision;
    }

    private record MatchKey(int account, long dialogId, boolean includeGlobal, String text) {}

    // Each generation owns its compiled patterns and results. A slow match from an
    // earlier generation can only populate the cache belonging to that generation.
    private static final class RuleIndex {
        private final ArrayList<Pattern> global = enabledPatterns(getRegexFilters());
        private final HashMap<Long, ArrayList<Pattern>> byDialog = new HashMap<>();
        private final LruCache<MatchKey, Boolean> results = new LruCache<>(512);

        RuleIndex() {
            for (ChatFilterEntry entry : getChatFilterEntries()) {
                if (!byDialog.containsKey(entry.dialogId)) {
                    byDialog.put(entry.dialogId, enabledPatterns(entry.filters));
                }
            }
        }

        private static ArrayList<Pattern> enabledPatterns(ArrayList<FilterModel> models) {
            ArrayList<Pattern> patterns = new ArrayList<>();
            if (models != null) {
                for (FilterModel model : models) {
                    if (model != null && model.enabled && model.pattern != null) {
                        patterns.add(model.pattern);
                    }
                }
            }
            return patterns;
        }

        private static boolean matches(ArrayList<Pattern> patterns, CharSequence text) {
            if (patterns != null) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(text).find()) return true;
                }
            }
            return false;
        }

        boolean isFiltered(MessageObject msg, MessageObject.GroupedMessages group, long dialogId) {
            ArrayList<Pattern> local = byDialog.get(dialogId);
            boolean includeGlobal = !global.isEmpty() && (dialogId <= 0 || NaConfig.INSTANCE.getRegexFiltersEnableInChats().Bool());
            if (!includeGlobal && (local == null || local.isEmpty())) return false;

            CharSequence text = getMessageText(msg, group);
            if (TextUtils.isEmpty(text)) return false;

            // Match the effective input, including the current album, buttons and translation.
            // Keep long messages out of the cache to bound retained text as well as entry count.
            MatchKey key = text.length() <= 512 ? new MatchKey(msg.currentAccount, dialogId, includeGlobal, text.toString()) : null;
            if (key != null) {
                Boolean cached = results.get(key);
                if (cached != null) return cached;
            }
            boolean result = matches(local, text) || includeGlobal && matches(global, text);
            if (key != null) results.put(key, result);
            return result;
        }
    }

    public static boolean isFiltered(MessageObject msg, MessageObject.GroupedMessages group) {
        if (msg == null || !NaConfig.INSTANCE.getRegexFiltersEnabled().Bool() || msg.isOutOwner()) return false;
        long dialogId = msg.getDialogId();
        if (isDialogExcluded(dialogId)) return false;

        RuleIndex rules = ruleIndex;
        if (rules == null) {
            synchronized (cacheLock) {
                rules = ruleIndex;
                if (rules == null) {
                    rules = new RuleIndex();
                    ruleIndex = rules;
                }
            }
        }
        return rules.isFiltered(msg, group, dialogId);
    }

    public static ArrayList<ChatFilterEntry> getChatFilterEntries() {
        if (chatFilterEntries == null) {
            synchronized (cacheLock) {
                if (chatFilterEntries == null) {
                    var str = NaConfig.INSTANCE.getRegexChatFiltersData().String();
                    try {
                        ChatFilterEntry[] arr = new Gson().fromJson(str, ChatFilterEntry[].class);
                        if (arr != null) {
                            var parsed = new ArrayList<>(Arrays.asList(arr));
                            parsed.removeAll(Collections.singleton(null));
                            boolean migrated = false;
                            for (var entry : parsed) {
                                if (entry.filters == null) continue;
                                for (var f : entry.filters) {
                                    if (f == null) continue;
                                    if (f.migrateFromLegacy(entry.dialogId)) {
                                        migrated = true;
                                    }
                                    f.buildPattern();
                                }
                            }
                            if (migrated) {
                                var json = new Gson().toJson(parsed);
                                NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString(json);
                            }
                            chatFilterEntries = parsed;
                        } else {
                            chatFilterEntries = new ArrayList<>();
                        }
                    } catch (Exception e) {
                        chatFilterEntries = new ArrayList<>();
                    }
                }
            }
        }
        return chatFilterEntries;
    }

    public static void saveChatFilterEntries(ArrayList<ChatFilterEntry> entries) {
        var str = new Gson().toJson(entries);
        NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString(str);
        AyuFilter.rebuildCache();
    }

    public static ArrayList<FilterModel> getChatFiltersForDialog(long dialogId) {
        var entries = getChatFilterEntries();
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                return e.filters != null ? e.filters : new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    public static void addChatFilter(long dialogId, String text, boolean caseInsensitive) {
        var entries = new ArrayList<>(getChatFilterEntries());
        ChatFilterEntry target = null;
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                target = e;
                break;
            }
        }
        if (target == null) {
            target = new ChatFilterEntry();
            target.dialogId = dialogId;
            entries.add(target);
        }

        FilterModel filterModel = new FilterModel();
        filterModel.regex = text;
        filterModel.caseInsensitive = caseInsensitive;
        filterModel.enabled = true;
        if (target.filters == null) {
            target.filters = new ArrayList<>();
        }
        target.filters.add(0, filterModel);

        saveChatFilterEntries(entries);
    }

    public static void editChatFilter(long dialogId, int filterIdx, String text, boolean caseInsensitive) {
        var entries = new ArrayList<>(getChatFilterEntries());
        for (var e : entries) {
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    var fm = e.filters.get(filterIdx);
                    fm.regex = text;
                    fm.caseInsensitive = caseInsensitive;
                    saveChatFilterEntries(entries);
                }
                return;
            }
        }
    }

    public static void removeChatFilter(long dialogId, int filterIdx) {
        var entries = new ArrayList<>(getChatFilterEntries());
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            if (e.dialogId == dialogId) {
                if (e.filters != null && filterIdx >= 0 && filterIdx < e.filters.size()) {
                    e.filters.remove(filterIdx);
                    if (e.filters.isEmpty()) {
                        entries.remove(i);
                    }
                    saveChatFilterEntries(entries);
                }
                return;
            }
        }
    }

    private static HashSet<Long> getExcludedDialogs() {
        if (excludedDialogs == null) {
            synchronized (cacheLock) {
                if (excludedDialogs == null) {
                    try {
                        String str = NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().String();
                        Long[] arr = new Gson().fromJson(str, Long[].class);
                        excludedDialogs = arr == null ? new HashSet<>() : new HashSet<>(Arrays.asList(arr));
                    } catch (Exception e) {
                        excludedDialogs = new HashSet<>();
                    }
                }
            }
        }
        return excludedDialogs;
    }

    public static boolean isDialogExcluded(long dialogId) {
        return getExcludedDialogs().contains(dialogId);
    }

    public static void setDialogExcluded(long dialogId, boolean excluded) {
        HashSet<Long> set = new HashSet<>(getExcludedDialogs());
        boolean changed;
        if (excluded) {
            changed = set.add(dialogId);
        } else {
            changed = set.remove(dialogId);
        }
        if (changed) {
            Long[] arr = set.toArray(new Long[0]);
            String str = new Gson().toJson(arr);
            NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString(str);
            synchronized (cacheLock) {
                excludedDialogs = set;
                filterRevision++;
            }
        }
    }

    public static void clearAllFilters() {
        NaConfig.INSTANCE.getRegexFiltersData().setConfigString("[]");
        NaConfig.INSTANCE.getRegexChatFiltersData().setConfigString("[]");
        NaConfig.INSTANCE.getRegexFiltersExcludedDialogs().setConfigString("[]");
        NaConfig.INSTANCE.getCustomFilteredUsersData().setConfigString("[]");
        synchronized (cacheLock) {
            customFilteredUsers = new HashSet<>();
            customFilteredUsersData = new HashMap<>();
        }
        rebuildCache();
    }

    private static HashSet<Long> getBlockedChannels() {
        if (blockedChannels == null) {
            synchronized (cacheLock) {
                if (blockedChannels == null) {
                    try {
                        String str = NaConfig.INSTANCE.getBlockedChannelsData().String();
                        Long[] arr = new Gson().fromJson(str, Long[].class);
                        blockedChannels = new HashSet<>();
                        if (arr != null) {
                            blockedChannels.addAll(Arrays.asList(arr));
                        }
                    } catch (Exception e) {
                        blockedChannels = new HashSet<>();
                    }
                }
            }
        }
        return blockedChannels;
    }

    private static void saveBlockedChannels(HashSet<Long> ids) {
        ArrayList<Long> sorted = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id < 0L) {
                    sorted.add(id);
                }
            }
        }
        Collections.sort(sorted);
        String str = new Gson().toJson(sorted.toArray(new Long[0]));
        NaConfig.INSTANCE.getBlockedChannelsData().setConfigString(str);
        synchronized (cacheLock) {
            blockedChannels = new HashSet<>(sorted);
        }
    }

    public static boolean isBlockedChannel(long dialogId) {
        return NekoConfig.ignoreBlocked.Bool() && getBlockedChannels().contains(dialogId);
    }

    public static boolean isCustomFilteredPeer(long peerId) {
        return NekoConfig.ignoreBlocked.Bool() && peerId > 0L && getCustomFilteredUsers().contains(peerId);
    }

    public static void blockPeer(long dialogId) {
        HashSet<Long> set = new HashSet<>(getBlockedChannels());
        if (set.add(dialogId)) {
            saveBlockedChannels(set);
        }
    }

    public static void unblockPeer(long dialogId) {
        HashSet<Long> set = new HashSet<>(getBlockedChannels());
        if (set.remove(dialogId)) {
            saveBlockedChannels(set);
        }
    }

    public static ArrayList<Long> getBlockedChannelsList() {
        return checkBlockedChannels(getBlockedChannels());
    }

    public static ArrayList<Long> getAllBlockedChannelsList() {
        ArrayList<Long> list = new ArrayList<>(getBlockedChannels());
        Collections.sort(list);
        return list;
    }

    public static int getBlockedChannelsCount() {
        return getBlockedChannels().size();
    }

    public static void clearBlockedChannels() {
        saveBlockedChannels(new HashSet<>());
    }

    public static void setBlockedChannels(ArrayList<Long> dialogIds) {
        HashSet<Long> set = new HashSet<>();
        if (dialogIds != null) {
            for (Long dialogId : dialogIds) {
                if (dialogId != null && dialogId < 0L) {
                    set.add(dialogId);
                }
            }
        }
        saveBlockedChannels(set);
    }

    public static ArrayList<Long> checkBlockedChannels(HashSet<Long> blockedChannels) {
        if (blockedChannels == null || blockedChannels.isEmpty()) return new ArrayList<>();
        ArrayList<Long> filtered = new ArrayList<>();
        try {
            final MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            final MessagesStorage ms = MessagesStorage.getInstance(UserConfig.selectedAccount);
            for (Long did : blockedChannels) {
                if (did == null) continue;
                if (did < 0) {
                    TLRPC.Chat chat = mc.getChat(-did);
                    if (chat == null) {
                        chat = ms.getChatSync(-did);
                    }
                    if (chat != null) {
                        filtered.add(did);
                        mc.putChat(chat, true);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return filtered;
    }

    private static void ensureCustomFilteredUsersLoaded() {
        if (customFilteredUsers != null && customFilteredUsersData != null) {
            return;
        }
        synchronized (cacheLock) {
            if (customFilteredUsers != null && customFilteredUsersData != null) {
                return;
            }
            HashSet<Long> ids = new HashSet<>();
            HashMap<Long, CustomFilteredUser> data = new HashMap<>();
            try {
                String str = NaConfig.INSTANCE.getCustomFilteredUsersData().String();
                CustomFilteredUser[] arr = new Gson().fromJson(str, CustomFilteredUser[].class);
                if (arr != null) {
                    for (CustomFilteredUser item : arr) {
                        if (item != null && item.id > 0L) {
                            ids.add(item.id);
                            data.put(item.id, item);
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            customFilteredUsers = ids;
            customFilteredUsersData = data;
        }
    }

    private static HashSet<Long> getCustomFilteredUsers() {
        ensureCustomFilteredUsersLoaded();
        return customFilteredUsers;
    }

    private static HashMap<Long, CustomFilteredUser> getCustomFilteredUsersDataMap() {
        ensureCustomFilteredUsersLoaded();
        return customFilteredUsersData;
    }

    private static void saveCustomFilteredUsers(HashSet<Long> ids, HashMap<Long, CustomFilteredUser> dataMap) {
        ArrayList<Long> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        ArrayList<CustomFilteredUser> out = new ArrayList<>(sorted.size());
        HashMap<Long, CustomFilteredUser> resultMap = new HashMap<>(sorted.size());
        for (Long id : sorted) {
            if (id == null || id <= 0L) {
                continue;
            }
            CustomFilteredUser user = dataMap.get(id);
            if (user == null) {
                user = new CustomFilteredUser();
                user.id = id;
            }
            out.add(user);
            resultMap.put(user.id, user);
        }
        String str = new Gson().toJson(out.toArray(new CustomFilteredUser[0]));
        NaConfig.INSTANCE.getCustomFilteredUsersData().setConfigString(str);
        synchronized (cacheLock) {
            customFilteredUsers = new HashSet<>(resultMap.keySet());
            customFilteredUsersData = resultMap;
        }
    }

    public static ArrayList<Long> getCustomFilteredUsersList() {
        ArrayList<Long> list = new ArrayList<>(getCustomFilteredUsers());
        Collections.sort(list);
        return list;
    }

    public static ArrayList<CustomFilteredUser> getCustomFilteredUsersDataList() {
        HashMap<Long, CustomFilteredUser> map = getCustomFilteredUsersDataMap();
        ArrayList<Long> sortedIds = getCustomFilteredUsersList();
        ArrayList<CustomFilteredUser> list = new ArrayList<>(sortedIds.size());
        for (Long id : sortedIds) {
            if (id == null || id <= 0L) {
                continue;
            }
            CustomFilteredUser item = map.get(id);
            if (item == null) {
                item = new CustomFilteredUser();
                item.id = id;
            }
            list.add(item);
        }
        return list;
    }

    public static CustomFilteredUser getCustomFilteredUser(long userId) {
        if (userId <= 0L) {
            return null;
        }
        return getCustomFilteredUsersDataMap().get(userId);
    }

    public static void setCustomFilteredUsersData(ArrayList<CustomFilteredUser> users) {
        HashSet<Long> ids = new HashSet<>();
        HashMap<Long, CustomFilteredUser> map = new HashMap<>();
        if (users != null) {
            for (CustomFilteredUser item : users) {
                if (item != null && item.id > 0L) {
                    ids.add(item.id);
                    map.put(item.id, item);
                }
            }
        }
        saveCustomFilteredUsers(ids, map);
    }

    public static void setCustomFilteredUsers(ArrayList<Long> ids) {
        HashSet<Long> set = new HashSet<>();
        HashMap<Long, CustomFilteredUser> current = new HashMap<>(getCustomFilteredUsersDataMap());
        HashMap<Long, CustomFilteredUser> data = new HashMap<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0L) {
                    set.add(id);
                    CustomFilteredUser item = current.get(id);
                    if (item == null) {
                        item = new CustomFilteredUser();
                        item.id = id;
                    }
                    data.put(id, item);
                }
            }
        }
        saveCustomFilteredUsers(set, data);
    }

    public static void updateCustomFilteredUserFromLocalUser(TLRPC.User user) {
        if (user == null || user.id <= 0L || !getCustomFilteredUsers().contains(user.id)) {
            return;
        }
        HashSet<Long> ids = new HashSet<>(getCustomFilteredUsers());
        HashMap<Long, CustomFilteredUser> map = new HashMap<>(getCustomFilteredUsersDataMap());
        CustomFilteredUser current = map.get(user.id);
        if (current == null) {
            current = new CustomFilteredUser();
            current.id = user.id;
        }
        boolean changed = false;
        String username = UserObject.getPublicUsername(user);
        String displayName = UserObject.getUserName(user);
        if (user.access_hash != 0L && current.accessHash != user.access_hash) {
            current.accessHash = user.access_hash;
            changed = true;
        }
        if (!TextUtils.equals(current.username, username)) {
            current.username = username;
            changed = true;
        }
        if (!TextUtils.equals(current.displayName, displayName)) {
            current.displayName = displayName;
            changed = true;
        }
        if (changed) {
            map.put(user.id, current);
            saveCustomFilteredUsers(ids, map);
        }
    }

    public static class FilterModel {
        @Expose
        public String regex;
        @Expose
        public boolean caseInsensitive;
        @Expose
        public boolean enabled = true;
        public Pattern pattern;

        // Legacy fields for deserialization migration only
        public ArrayList<Long> enabledGroups;
        public ArrayList<Long> disabledGroups;

        public void buildPattern() {
            var flags = Pattern.MULTILINE;
            if (caseInsensitive) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            try {
                pattern = Pattern.compile(regex, flags);
            } catch (Exception e) {
                pattern = null;
                FileLog.e(e);
            }
        }

        public boolean migrateFromLegacy(long dialogId) {
            if (enabledGroups == null && disabledGroups == null) {
                return false;
            }
            boolean defaultEnabled = enabledGroups != null && enabledGroups.contains(0L);
            if (defaultEnabled) {
                enabled = disabledGroups == null || !disabledGroups.contains(dialogId);
            } else {
                enabled = enabledGroups != null && enabledGroups.contains(dialogId);
            }
            enabledGroups = null;
            disabledGroups = null;
            return true;
        }
    }

    public static class ChatFilterEntry {
        @Expose
        public long dialogId;
        @Expose
        public ArrayList<FilterModel> filters;
    }

    public static class CustomFilteredUser {
        @Expose
        public long id;
        @Expose
        public long accessHash;
        @Expose
        public String username;
        @Expose
        public String displayName;
    }
}
