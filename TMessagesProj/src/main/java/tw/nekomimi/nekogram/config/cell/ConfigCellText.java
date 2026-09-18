package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import tw.nekomimi.nekogram.config.CellGroup;

public class ConfigCellText extends AbstractConfigCell implements WithKey, WithOnClick {
    private static final Set<String> MISSING_LOCALIZATION_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final String key;
    private final String value;
    private final Runnable onClick;
    private boolean enabled = true;
    private TextSettingsCell cell;

    public ConfigCellText(String key, String customValue, Runnable onClick) {
        this.key = key;
        this.value = (customValue == null) ? "" : customValue;
        this.onClick = onClick;
    }

    public ConfigCellText(String key, Runnable onClick) {
        this(key, null, onClick);
    }

    /**
     * Settings titles are commonly resolved from a persisted config key.  Do
     * not let a bad/missing dynamic resource hide an otherwise usable row.
     */
    public static String getLocalizedTitle(String key) {
        String title = getString(key);
        if (title != null && !title.isEmpty() && !title.startsWith("LOC_ERR:")) {
            return title;
        }
        if (MISSING_LOCALIZATION_KEYS.add(String.valueOf(key))) {
            FileLog.d("LOCALIZATION_KEY_MISSING key=" + key);
        }
        String fallback = getString(R.string.NekoSettings);
        return fallback == null || fallback.isEmpty() || fallback.startsWith("LOC_ERR:")
                ? "N-Settings" : fallback;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
    }

    public String getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.cell != null) this.cell.setEnabled(this.enabled);
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextSettingsCell cell = (TextSettingsCell) holder.itemView;
        this.cell = cell;
        String title = getLocalizedTitle(key);
        cell.setTextAndValue(title, value, cellGroup.needSetDivider(this));
        cell.setEnabled(enabled);
    }

    public void onClick() {
        if (!enabled) return;
        if (onClick != null) {
            try {
                onClick.run();
            } catch (Exception ignored) {}
        }
    }
}
