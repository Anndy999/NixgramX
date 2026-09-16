package tw.nekomimi.nekogram.drawer;

public enum MainMenuItem {
    DIVIDER(-1),
    ARCHIVE(14),
    NEW_GROUP(2),
    SAVED(11),
    NEW_CHANNEL(3),
    CALLS(10),
    SETTINGS(8),
    QR(17),
    GHOST_MODE(107),
    BOTS(105),
    CONTACTS(6),
    PROFILE(18),
    RECENT_CHATS(108),
    BOOKMARKS(109),
    NEW_CONTACT(110);

    private final int id;

    MainMenuItem(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static MainMenuItem getById(int id) {
        for (MainMenuItem item : values()) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }
}
