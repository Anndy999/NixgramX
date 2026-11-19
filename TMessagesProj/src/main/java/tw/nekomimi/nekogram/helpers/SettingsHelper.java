package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoAboutActivity;
import tw.nekomimi.nekogram.settings.NekoChatSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoEmojiSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

public class SettingsHelper {

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2 || !"nasettings".equals(segments.get(0))) {
            unknown.run();
            return;
        }
        BaseFragment fragment;
        BaseNekoSettingsActivity neko_fragment = null;
        BaseNekoXSettingsActivity nekox_fragment = null;
        if (segments.size() == 1) {
            fragment = new NekoSettingsActivity();
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = neko_fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "about":
                    fragment = new NekoAboutActivity();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = nekox_fragment = new NekoChatSettingsActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = nekox_fragment = new NekoExperimentalSettingsActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new NekoEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = nekox_fragment = new NekoGeneralSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseNekoSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (nekox_fragment != null) {
                BaseNekoXSettingsActivity finalNekoX_fragment = nekox_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    // The cached index holds only row metadata. Each navigation opens a fresh fragment.
    private record SearchEntry(int guid, String title, String category, int icon, String key,
                               Supplier<BaseNekoXSettingsActivity> factory) {}
    private record SearchScope(LocaleController.LocaleInfo language, int languageVersion, int baseVersion,
                               int account, boolean debugRows) {}

    private static volatile List<SearchEntry> searchIndex;
    private static SearchScope searchScope;
    private static boolean observingSearchPreferences;
    private static final SharedPreferences.OnSharedPreferenceChangeListener searchPreferencesListener =
            (preferences, key) -> searchIndex = null;

    private static synchronized List<SearchEntry> getSearchIndex() {
        if (!observingSearchPreferences) {
            NekoConfig.getPreferences().registerOnSharedPreferenceChangeListener(searchPreferencesListener);
            observingSearchPreferences = true;
        }
        LocaleController.LocaleInfo language = LocaleController.getInstance().getCurrentLocaleInfo();
        SearchScope scope = new SearchScope(language, language != null ? language.version : 0,
                language != null ? language.baseVersion : 0, UserConfig.selectedAccount, BuildVars.LOGS_ENABLED);
        List<SearchEntry> index = searchIndex;
        if (scope.equals(searchScope) && index != null) {
            return index;
        }

        ArrayList<SearchEntry> entries = new ArrayList<>();
        indexPage(entries, NekoGeneralSettingsActivity::new);
        indexPage(entries, NekoChatSettingsActivity::new);
        indexPage(entries, NekoExperimentalSettingsActivity::new);
        indexPage(entries, NekoTranslatorSettingsActivity::new);
        searchScope = scope;
        index = List.copyOf(entries);
        searchIndex = index;
        return index;
    }

    private static void indexPage(ArrayList<SearchEntry> entries, Supplier<BaseNekoXSettingsActivity> factory) {
        BaseNekoXSettingsActivity fragment = factory.get();
        int guid = fragment.getBaseGuid();
        int icon = fragment.getDrawable();
        String category = fragment.getTitle();
        ArrayList<Map.Entry<Integer, String>> rows = new ArrayList<>(fragment.getRowMapReverse().entrySet());
        rows.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, String> row : rows) {
            int position = row.getKey();
            String key = row.getValue();
            if (key.equals(String.valueOf(position))) continue;

            String title = getString(key);
            if (!TextUtils.isEmpty(title)) {
                entries.add(new SearchEntry(guid + position, title, category, icon, key, factory));
            }
        }
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        String title = getString(R.string.NekoSettings);
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        for (SearchEntry entry : getSearchIndex()) {
            Runnable open = () -> {
                BaseNekoXSettingsActivity fragment = entry.factory().get();
                callback.presentFragment(fragment);
                AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(entry.key(), null));
            };
            items.add(new SettingsSearchResult(entry.guid(), entry.title(), title, entry.category(), entry.icon(), open));
        }
        return items;
    }
}
