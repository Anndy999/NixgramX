package tw.nekomimi.nekogram.drawer;

import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.NixNavigationConfig;

/**
 * Visible/hidden ids as "visible;hidden". Empty string means default.
 * Ghost is never independently enabled here — {@link NekoConfig#showGhostInDrawer}
 * decides whether Ghost is in the available set.
 */
public final class MainMenuLayout {

    private static final String SECTION_SEPARATOR = ";";
    private static final String ID_SEPARATOR = ",";

    private MainMenuLayout() {
    }

    public static List<Integer> getDefaultVisible() {
        ArrayList<Integer> layout = new ArrayList<>();
        layout.add(MainMenuItem.ARCHIVE.getId());
        layout.add(MainMenuItem.NEW_GROUP.getId());
        layout.add(MainMenuItem.SAVED.getId());
        layout.add(MainMenuItem.NEW_CHANNEL.getId());
        layout.add(MainMenuItem.CALLS.getId());
        layout.add(MainMenuItem.SETTINGS.getId());
        return layout;
    }

    public static List<Integer> getCustomizeCandidates() {
        ArrayList<Integer> extra = new ArrayList<>();
        extra.add(MainMenuItem.QR.getId());
        extra.add(MainMenuItem.CONTACTS.getId());
        extra.add(MainMenuItem.PROFILE.getId());
        extra.add(MainMenuItem.RECENT_CHATS.getId());
        extra.add(MainMenuItem.BOOKMARKS.getId());
        extra.add(MainMenuItem.NEW_CONTACT.getId());
        if (NekoConfig.showGhostInDrawer.Bool()) {
            extra.add(MainMenuItem.GHOST_MODE.getId());
        }
        return extra;
    }

    public static boolean isGhostAvailable() {
        return NekoConfig.showGhostInDrawer.Bool();
    }

    public static List<Integer> getVisibleIds() {
        Parsed parsed = parse(NixNavigationConfig.getMainMenuLayout());
        ArrayList<Integer> visible = new ArrayList<>();
        for (Integer id : parsed.visible) {
            if (isAllowed(id)) {
                visible.add(id);
            }
        }
        if (visible.isEmpty()) {
            visible.addAll(getDefaultVisible());
        }
        ensureSettingsIfRequired(visible, parsed.hidden);
        return visible;
    }

    public static List<Integer> getHiddenIds() {
        Parsed parsed = parse(NixNavigationConfig.getMainMenuLayout());
        List<Integer> visible = getVisibleIds();
        ArrayList<Integer> hidden = new ArrayList<>();
        for (Integer id : parsed.hidden) {
            if (isAllowed(id) && !visible.contains(id) && !hidden.contains(id)) {
                hidden.add(id);
            }
        }
        for (Integer id : getCustomizeCandidates()) {
            if (!visible.contains(id) && !hidden.contains(id)) {
                hidden.add(id);
            }
        }
        return hidden;
    }

    public static void save(List<Integer> visible, List<Integer> hidden) {
        ArrayList<Integer> vis = new ArrayList<>();
        ArrayList<Integer> hid = new ArrayList<>();
        if (visible != null) {
            for (Integer id : visible) {
                if (isAllowed(id) && !vis.contains(id)) {
                    vis.add(id);
                }
            }
        }
        if (hidden != null) {
            for (Integer id : hidden) {
                if (isAllowed(id) && !vis.contains(id) && !hid.contains(id)) {
                    hid.add(id);
                }
            }
        }
        if (vis.isEmpty()) {
            vis.addAll(getDefaultVisible());
        }
        ensureSettingsIfRequired(vis, hid);
        NixNavigationConfig.setMainMenuLayout(join(vis) + SECTION_SEPARATOR + join(hid));
    }

    public static void resetDefault() {
        NixNavigationConfig.setMainMenuLayout("");
    }

    public static boolean settingsMustRemainVisible() {
        return NixNavigationConfig.isDrawerEnabled();
    }

    private static boolean isAllowed(int id) {
        MainMenuItem item = MainMenuItem.getById(id);
        if (item == null || item == MainMenuItem.DIVIDER) {
            return false;
        }
        if (item == MainMenuItem.GHOST_MODE) {
            return isGhostAvailable();
        }
        return true;
    }

    static void ensureSettingsIfRequired(List<Integer> visible, List<Integer> hidden) {
        if (!settingsMustRemainVisible()) {
            return;
        }
        Integer settings = MainMenuItem.SETTINGS.getId();
        if (!visible.contains(settings)) {
            visible.add(settings);
        }
        if (hidden != null) {
            hidden.remove(settings);
        }
    }

    private static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        if (raw == null || raw.trim().isEmpty()) {
            parsed.visible.addAll(getDefaultVisible());
            return parsed;
        }
        String[] sections = raw.split(SECTION_SEPARATOR, -1);
        parseIds(sections.length > 0 ? sections[0] : "", parsed.visible);
        parseIds(sections.length > 1 ? sections[1] : "", parsed.hidden);
        if (parsed.visible.isEmpty()) {
            parsed.visible.addAll(getDefaultVisible());
        }
        return parsed;
    }

    private static void parseIds(String section, List<Integer> out) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String part : section.split(ID_SEPARATOR)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int id = Integer.parseInt(trimmed);
                if (isAllowed(id) && !out.contains(id)) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String join(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(ID_SEPARATOR);
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static final class Parsed {
        final ArrayList<Integer> visible = new ArrayList<>();
        final ArrayList<Integer> hidden = new ArrayList<>();
    }
}
