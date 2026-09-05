package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.ConnectionResult;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.GooglePushListenerServiceProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UnifiedPushService;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput2;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoGeneralSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "general";
    }

    private ValueAnimator statusBarColorAnimator;
    private Parcelable recyclerViewState = null;

    private boolean wasCentered = false;
    private boolean wasCenteredAtBeginning = false;
    private float centeredMeasure = -1;

    private final CellGroup cellGroup = new CellGroup(this);

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell customTitleRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomTitle(),
        getString(R.string.CustomTitleHint), null,
        (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getCustomTitle().defaultValue : input));
    private final AbstractConfigCell folderNameAsTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getFolderNameAsTitle()));
    private final AbstractConfigCell customTitleUserNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCustomTitleUserName()));
    private final AbstractConfigCell disableNumberRoundingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNumberRounding, "4.8K -> 4777"));
    private final AbstractConfigCell preferCommonGroupsTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPreferCommonGroupsTab(), getString(R.string.PreferCommonGroupsTabNotice)));
    private final AbstractConfigCell usePersianCalendarRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.usePersianCalendar, getString(R.string.UsePersianCalendarInfo)));
    private final AbstractConfigCell displayPersianCalendarByLatinRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.displayPersianCalendarByLatin));
    private final AbstractConfigCell showIdAndDcRow = cellGroup.appendCell(new ConfigCellSelectBox("ShowIdAndDc", NaConfig.INSTANCE.getIdDcType(), new String[]{
            getString(R.string.Disable),
            "Telegram API",
            "Bot API"
    }, null));
    private final AbstractConfigCell nameOrderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.nameOrder, new String[]{
            getString(R.string.LastFirst),
            getString(R.string.FirstLast)
    }, null));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Storage
    private final AbstractConfigCell headerStorage = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.StorageSettings)));
    private final AbstractConfigCell saveToChatSubfolderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveToChatSubfolder()));
    private final AbstractConfigCell customSavePathRow = cellGroup.appendCell(new ConfigCellTextDetail(
            NekoConfig.customSavePath,
            getString(R.string.customSavePath),
            getString(R.string.customSavePathHint),
            this::sanitizeCustomSavePath,
            this::shouldShowCustomSavePathInputError,
            this::formatCustomSavePathDetail));

    private final AbstractConfigCell dividerStorage = cellGroup.appendCell(new ConfigCellDivider());

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell useIPv6Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useIPv6));
    private final AbstractConfigCell disableProxyWhenVpnEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableProxyWhenVpnEnabled()));
    private final AbstractConfigCell defaultHlsVideoQualityRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getDefaultHlsVideoQuality(), new String[]{
            getString(R.string.QualityAuto),
            getString(R.string.QualityOriginal),
            getString(R.string.Quality1440),
            getString(R.string.Quality1080),
            getString(R.string.Quality720),
            getString(R.string.Quality144),
    }, null));
    private final AbstractConfigCell dnsTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.dnsType, new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.NagramX),
            getString(R.string.DnsTypeSystem),
            getString(R.string.CustomDoH),
    }, null));
    private final AbstractConfigCell customDoHRow = cellGroup.appendCell(new ConfigCellTextInput2(null, NekoConfig.customDoH, "https://1.0.0.1/dns-query, https://...", null));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    // Map
    private final AbstractConfigCell headerMap = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Map)));
    private final AbstractConfigCell useOSMDroidMapRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useOSMDroidMap));
    private final AbstractConfigCell mapDriftingFixForGoogleMapsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mapDriftingFixForGoogleMaps));
    private final AbstractConfigCell mapPreviewRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.mapPreviewProvider, new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.MapPreviewProviderYandexNax),
            getString(R.string.MapPreviewProviderNobody)
    }, null));
    private final AbstractConfigCell dividerMap = cellGroup.appendCell(new ConfigCellDivider());

    // Folder
    private final AbstractConfigCell headerFolder = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Folder)));
    private final AbstractConfigCell hideAllTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideAllTab, getString(R.string.HideAllTabAbout)));
    private final AbstractConfigCell doNotUnarchiveBySwipeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDoNotUnarchiveBySwipe()));
    private final AbstractConfigCell openArchiveOnPullRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.openArchiveOnPull));
    private final AbstractConfigCell hideArchiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideArchive()));
    private final AbstractConfigCell ignoreUnreadCountRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getIgnoreUnreadCount(), new String[]{
            getString(R.string.Disable),
            getString(R.string.FilterMuted),
            getString(R.string.FilterAllChatsShort)
    }, null));
    private final AbstractConfigCell tabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }, null));
    private final AbstractConfigCell dividerFolder = cellGroup.appendCell(new ConfigCellDivider());

    // Dialogs
    private final AbstractConfigCell headerDialogs = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.DialogsSettings)));
    private final AbstractConfigCell sortByUnreadRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSortByUnread()));
    private final AbstractConfigCell hideDialogsSearchFieldRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideDialogsSearchField()));
    private final AbstractConfigCell disableDialogsFloatingButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableDialogsFloatingButton()));
    private final AbstractConfigCell disableBotOpenButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableBotOpenButton()));
    private final AbstractConfigCell mediaPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mediaPreview));
    private final AbstractConfigCell dividerDialogs = cellGroup.appendCell(new ConfigCellDivider());

    // Appearance
    private final AbstractConfigCell headerAppearance = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Appearance)));
    private final AbstractConfigCell typefaceRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.typeface));
    private final AbstractConfigCell hideDividers = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideDividers()));
    private final AbstractConfigCell alwaysShowDownloadIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAlwaysShowDownloadIcon()));
    private final AbstractConfigCell showStickersInTopLevelRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowStickersRowToplevel()));
    private final AbstractConfigCell hidePremiumSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHidePremiumSection()));
    private final AbstractConfigCell hideHelpSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideHelpSection()));
    private final AbstractConfigCell iconReplacements = cellGroup.appendCell(new ConfigCellSelectBox("IconReplacements", NaConfig.INSTANCE.getIconReplacements(), new String[]{
            getString(R.string.Default),
            getString(R.string.IconReplacementSolar),
    }, null));
    private final AbstractConfigCell switchStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3)
    }, null));
    private final AbstractConfigCell sliderStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SliderStyle", NaConfig.INSTANCE.getSliderStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3)
    }, null));
    private final AbstractConfigCell actionBarDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.actionBarDecoration, new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.Fireworks),
            getString(R.string.DecorationNone),
    }, null));
    private final AbstractConfigCell chatDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getChatDecoration(), new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.DecorationNone),
    }, null));
    private final AbstractConfigCell notificationIconRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getNotificationIcon(), new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.NagramX),
            getString(R.string.Nagram),
            getString(R.string.NekoX)
    }, null));
    private final AbstractConfigCell tabletModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabletMode, new String[]{
            getString(R.string.TabletModeDefault),
            getString(R.string.TabletModeOn),
            getString(R.string.TabletModeOff)
    }, null));
    private final AbstractConfigCell centerActionBarTitleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getCenterActionBarTitleType(), new String[]{
            getString(R.string.CenterActionBarTitleOff),
            getString(R.string.CenterActionBarTitleOn),
            getString(R.string.SettingsOnly),
            getString(R.string.ChatsOnly)
    }, null));
    private final AbstractConfigCell dividerAppearance = cellGroup.appendCell(new ConfigCellDivider());

    // Blur
    private final AbstractConfigCell headerBlur = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.LiteOptionsBlur2)));
    private final AbstractConfigCell strokeOnViews = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getStrokeOnViews()));
    private final AbstractConfigCell disableAvatarBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAvatarBlur()));
    private final AbstractConfigCell dividerBlur = cellGroup.appendCell(new ConfigCellDivider());

    // Main Tabs
    private final AbstractConfigCell headerMainTabs = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MainTabsSettingsHeader)));
    private final AbstractConfigCell hideTitlesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMainTabsHideTitles()));
    private final AbstractConfigCell hideContactsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMainTabsHideContacts()));
    private final AbstractConfigCell hideBottomNavigationBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideBottomNavigationBar()));
    private final AbstractConfigCell dividerMainTabs = cellGroup.appendCell(new ConfigCellDivider());

    // Privacy
    private final AbstractConfigCell headerPrivacy = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.PrivacyTitle)));
    private final AbstractConfigCell hidePhoneRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hidePhone));
    private final AbstractConfigCell disableSystemAccountRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableSystemAccount));
    private final AbstractConfigCell disableCrashlyticsCollectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableCrashlyticsCollection()));
    private final AbstractConfigCell dividerPrivacy = cellGroup.appendCell(new ConfigCellDivider());

    // Notifications
    private final AbstractConfigCell headerNotifications = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Notifications)));
    private final AbstractConfigCell pushServiceTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPushServiceType(), new String[]{
            getString(R.string.PushServiceTypeInApp),
            getString(R.string.PushServiceTypeFCM),
            getString(R.string.PushServiceTypeUnified),
            getString(R.string.PushServiceTypeMicroG),
    }, null));
    private final AbstractConfigCell fcmPushStatusRow = cellGroup.appendCell(new ConfigCellCustom("FcmPushStatus", CellGroup.ITEM_TYPE_TEXT_DETAIL, true));
    private final AbstractConfigCell pushServiceTypeUnifiedGatewayRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway(), UnifiedPushService.UP_GATEWAY_DEFAULT, null, (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().defaultValue : input));
    private final AbstractConfigCell pushServiceTypeInAppDialogRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPushServiceTypeInAppDialog()));
    private final AbstractConfigCell disableNotificationBubblesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNotificationBubbles));
    private final AbstractConfigCell dividerNotifications = cellGroup.appendCell(new ConfigCellDivider());

    // AutoDownload
    private final AbstractConfigCell headerAutoDownload = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AutoDownload)));
    private final AbstractConfigCell win32Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingWin32Executable));
    private final AbstractConfigCell archiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingArchive));
    private final AbstractConfigCell dividerAutoDownload = cellGroup.appendCell(new ConfigCellDivider());

    public NekoGeneralSettingsActivity() {
        if (!NaConfig.INSTANCE.getCenterActionBarTitle().Bool()) {
            NaConfig.INSTANCE.getCenterActionBarTitleType().setConfigInt(0);
        }
        if (!shouldShowPersian()) {
            cellGroup.rows.remove(usePersianCalendarRow);
            cellGroup.rows.remove(displayPersianCalendarByLatinRow);
        }
        wasCentered = isCentered();
        wasCenteredAtBeginning = wasCentered;

        checkCustomDoHRows();
        checkMapDriftingFixRows();
        checkCustomTitleRows();
        checkPushServiceTypeRows();
        checkOpenArchiveOnPullRows();
        checkMainTabsRows();
        addRowsToMap(cellGroup);
    }

    @SuppressLint({"NewApi", "NotifyDataSetChanged", "UseCompatLoadingForDrawables"})
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);

        setupDefaultListeners();

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.actionBarDecoration.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getNotificationIcon().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.tabletMode.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.disableSystemAccount.getKey())) {
                if ((boolean) newValue) {
                    getContactsController().deleteUnknownAppAccounts();
                } else {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        ContactsController.getInstance(a).checkAppAccount();
                    }
                }
            } else if (key.equals(NekoConfig.useOSMDroidMap.getKey())) {
                checkMapDriftingFixRows();
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceType().getKey())) {
                PushListenerController.reconcilePushRegistration();
                if ((int) newValue == 0) {
                    AndroidUtil.setPushService(false);
                } else {
                    NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(false);
                }
                checkPushServiceTypeRows();
                refreshFcmPushStatusRow();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeInAppDialog().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableCrashlyticsCollection().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitleUserName().getKey())) {
                checkCustomTitleRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSortByUnread().getKey())) {
                getMessagesController().sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            } else if (key.equals(NaConfig.INSTANCE.getIgnoreUnreadCount().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.hideAllTab.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCenterActionBarTitleType().getKey())) {
                int value = (int) newValue;
                NaConfig.INSTANCE.getCenterActionBarTitle().setConfigBool(value != 0);
                animateActionBarUpdate(this);
            } else if (key.equals(NaConfig.INSTANCE.getHideArchive().getKey())) {
                checkOpenArchiveOnPullRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableBotOpenButton().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideDividers().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getIconReplacements().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSwitchStyle().getKey()) || key.equals(NaConfig.INSTANCE.getSliderStyle().getKey())) {
                if (listView.getLayoutManager() != null) {
                    recyclerViewState = listView.getLayoutManager().onSaveInstanceState();
                    parentLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    listView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
                }
            } else if (key.equals(NekoConfig.usePersianCalendar.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.dnsType.getKey())) {
                checkCustomDoHRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.typeface.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableDialogsFloatingButton().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHidePremiumSection().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideHelpSection().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getAlwaysShowDownloadIcon().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getShowStickersRowToplevel().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSaveToChatSubfolder().getKey())) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(customSavePathRow));
            } else if (key.equals(NaConfig.INSTANCE.getMainTabsHideTitles().getKey())) {
                parentLayout.rebuildFragments(0);
            } else if (key.equals(NaConfig.INSTANCE.getMainTabsHideContacts().getKey())) {
                parentLayout.rebuildFragments(0);
            } else if (key.equals(NaConfig.INSTANCE.getHideBottomNavigationBar().getKey())) {
                checkMainTabsRows();
                parentLayout.rebuildFragments(0);
            } else if (key.equals(NaConfig.INSTANCE.getHideDialogsSearchField().getKey())) {
                parentLayout.rebuildFragments(0);
            }
        };

        return superView;
    }

    private void showUnifiedPushStatistics() {
        if (getParentActivity() == null) {
            return;
        }

        String txt;
        long num = UnifiedPushService.getNumOfReceivedNotifications();
        if (num == 0) {
            txt = getString(R.string.UnifiedPushNeverReceivedNotifications);
        } else {
            txt = LocaleController.formatString(
                    R.string.UnifiedPushLastReceivedNotification,
                    (SystemClock.elapsedRealtime() - UnifiedPushService.getLastReceivedNotification()) / 1000,
                    num
            );
        }
        txt += "\n\n" + LocaleController.formatString(R.string.UnifiedPushCurrentEndpoint, SharedConfig.pushString);

        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.PushServiceTypeUnified))
                .setMessage(txt)
                .setPositiveButton(getString(R.string.OK), null)
                .create());
    }

    @Override
    public int getBaseGuid() {
        return 12000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return getString(R.string.General);
    }

    private String buildFcmPushStatusValue() {
        StringBuilder sb = new StringBuilder();
        int playServicesCode = GooglePushListenerServiceProvider.checkPlayServicesStatusCode();
        if (playServicesCode == ConnectionResult.SUCCESS) {
            sb.append(getString(R.string.FcmPlayServicesAvailable));
        } else {
            sb.append(LocaleController.formatString(R.string.FcmPlayServicesUnavailable, playServicesCode));
        }

        int pushType = NaConfig.INSTANCE.getPushServiceType().Int();
        String pushTypeLabel;
        switch (pushType) {
            case 0:
                pushTypeLabel = getString(R.string.PushServiceTypeInApp);
                break;
            case 1:
                pushTypeLabel = getString(R.string.PushServiceTypeFCM);
                break;
            case 2:
                pushTypeLabel = getString(R.string.PushServiceTypeUnified);
                break;
            case 3:
                pushTypeLabel = getString(R.string.PushServiceTypeMicroG);
                break;
            default:
                pushTypeLabel = String.valueOf(pushType);
                break;
        }
        sb.append('\n').append(LocaleController.formatString(R.string.FcmPushServiceTypeValue, pushTypeLabel));

        if (!TextUtils.isEmpty(SharedConfig.pushStringStatus)) {
            sb.append('\n').append(LocaleController.formatString(R.string.FcmPushStringStatus, SharedConfig.pushStringStatus));
        }

        if (!TextUtils.isEmpty(SharedConfig.pushString)) {
            String token = SharedConfig.pushString;
            int len = token.length();
            String prefix = len >= 8 ? token.substring(0, 8) : token;
            sb.append('\n').append(LocaleController.formatString(R.string.FcmPushTokenOk, len, prefix));
        } else {
            sb.append('\n').append(getString(R.string.FcmPushTokenMissing));
        }

        if (!TextUtils.isEmpty(SharedConfig.pushStringLastError)) {
            sb.append('\n').append(LocaleController.formatString(R.string.FcmPushLastError, SharedConfig.pushStringLastError));
        }

        sb.append('\n').append(getString(R.string.FcmPushStatusHint));
        return sb.toString();
    }

    private void refreshFcmPushStatusRow() {
        if (listAdapter == null) {
            return;
        }
        int index = cellGroup.rows.indexOf(fcmPushStatusRow);
        if (index >= 0) {
            listAdapter.notifyItemChanged(index);
        }
    }

    private void showFcmPushStatusDialog() {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.FcmPushStatus))
                .setMessage(buildFcmPushStatusValue())
                .setNegativeButton(getString(R.string.OK), null)
                .setPositiveButton(getString(R.string.FcmPushRequestToken), (dialog, which) -> rerequestFcmToken())
                .create());
    }

    private void rerequestFcmToken() {
        SharedConfig.pushStringLastError = "";
        PushListenerController.IPushListenerServiceProvider provider = ApplicationLoader.getPushProvider();
        if (provider instanceof GooglePushListenerServiceProvider googleProvider) {
            googleProvider.reset();
            googleProvider.onRequestPushToken();
        } else {
            GooglePushListenerServiceProvider googleProvider = new GooglePushListenerServiceProvider();
            googleProvider.onRequestPushToken();
        }
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.FcmPushRequesting)).show();
        }
        refreshFcmPushStatusRow();
        AndroidUtilities.runOnUIThread(this::refreshFcmPushStatusRow, 1500);
        AndroidUtilities.runOnUIThread(this::refreshFcmPushStatusRow, 4000);
    }

    @Override
    protected void onCustomCellClick(View view, int position, float x, float y) {
        AbstractConfigCell a = cellGroup.rows.get(position);
        if (a == fcmPushStatusRow) {
            showFcmPushStatusDialog();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        AbstractConfigCell a = cellGroup.rows.get(position);
        if (a == pushServiceTypeUnifiedGatewayRow) {
            ItemOptions options = makeLongClickOptions(view);
            options.add(R.drawable.msg_stats, getString(R.string.Statistics), this::showUnifiedPushStatistics);
            addDefaultLongClickOptions(options, "general", position);
            showLongClickOptions(view, options);
            return true;
        }
        if (a == fcmPushStatusRow) {
            ItemOptions options = makeLongClickOptions(view);
            options.add(R.drawable.msg_retry, getString(R.string.FcmPushRequestToken), this::rerequestFcmToken);
            addDefaultLongClickOptions(options, "general", position);
            showLongClickOptions(view, options);
            return true;
        }
        return false;
    }

    // impl ListAdapter
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@androidx.annotation.NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == cellGroup.rows.indexOf(fcmPushStatusRow) && holder.itemView instanceof TextDetailSettingsCell cell) {
                cell.setMultilineDetail(true);
                cell.setTextAndValue(getString(R.string.FcmPushStatus), buildFcmPushStatusValue(), cellGroup.needSetDivider(fcmPushStatusRow));
            }
        }
    }

    private void checkCustomDoHRows() {
        boolean useDoH = NekoConfig.dnsType.Int() == NekoConfig.DNS_TYPE_CUSTOM_DOH;
        if (listAdapter == null) {
            if (!useDoH) {
                cellGroup.rows.remove(customDoHRow);
            }
            return;
        }
        if (useDoH) {
            final int index = cellGroup.rows.indexOf(dnsTypeRow);
            if (!cellGroup.rows.contains(customDoHRow)) {
                cellGroup.rows.add(index + 1, customDoHRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int customDoHRowIndex = cellGroup.rows.indexOf(customDoHRow);
            if (customDoHRowIndex != -1) {
                cellGroup.rows.remove(customDoHRow);
                listAdapter.notifyItemRemoved(customDoHRowIndex);
            }
        }
    }

    private void checkMapDriftingFixRows() {
        boolean useOSMDroid = NekoConfig.useOSMDroidMap.Bool();
        if (listAdapter == null) {
            if (useOSMDroid) {
                cellGroup.rows.remove(mapDriftingFixForGoogleMapsRow);
            }
            return;
        }
        if (!useOSMDroid) {
            final int index = cellGroup.rows.indexOf(useOSMDroidMapRow);
            if (!cellGroup.rows.contains(mapDriftingFixForGoogleMapsRow)) {
                cellGroup.rows.add(index + 1, mapDriftingFixForGoogleMapsRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(mapDriftingFixForGoogleMapsRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(mapDriftingFixForGoogleMapsRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkCustomTitleRows() {
        boolean useUserName = NaConfig.INSTANCE.getCustomTitleUserName().Bool();
        if (listAdapter == null) {
            if (useUserName) {
                cellGroup.rows.remove(customTitleRow);
            }
            return;
        }
        if (!useUserName) {
            final int index = cellGroup.rows.indexOf(headerGeneral);
            if (!cellGroup.rows.contains(customTitleRow)) {
                cellGroup.rows.add(index + 1, customTitleRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(customTitleRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(customTitleRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkPushServiceTypeRows() {
        boolean useInApp = NaConfig.INSTANCE.getPushServiceType().Int() == 0;
        boolean useUnified = NaConfig.INSTANCE.getPushServiceType().Int() == 2;
        if (listAdapter == null) {
            if (!useInApp) {
                cellGroup.rows.remove(pushServiceTypeInAppDialogRow);
            }
            if (!useUnified) {
                cellGroup.rows.remove(pushServiceTypeUnifiedGatewayRow);
            }
            return;
        }
        if (useInApp) {
            final int index = cellGroup.rows.indexOf(pushServiceTypeRow);
            if (!cellGroup.rows.contains(pushServiceTypeInAppDialogRow)) {
                cellGroup.rows.add(index + 1, pushServiceTypeInAppDialogRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(pushServiceTypeInAppDialogRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(pushServiceTypeInAppDialogRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        if (useUnified) {
            final int index = cellGroup.rows.indexOf(pushServiceTypeRow);
            if (!cellGroup.rows.contains(pushServiceTypeUnifiedGatewayRow)) {
                cellGroup.rows.add(index + 1, pushServiceTypeUnifiedGatewayRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(pushServiceTypeUnifiedGatewayRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(pushServiceTypeUnifiedGatewayRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkOpenArchiveOnPullRows() {
        boolean hideArchive = NaConfig.INSTANCE.getHideArchive().Bool();
        if (listAdapter == null) {
            if (hideArchive) {
                cellGroup.rows.remove(openArchiveOnPullRow);
            }
            return;
        }
        if (!hideArchive) {
            final int index = cellGroup.rows.indexOf(hideArchiveRow);
            if (!cellGroup.rows.contains(openArchiveOnPullRow)) {
                cellGroup.rows.add(index, openArchiveOnPullRow);
                listAdapter.notifyItemInserted(index);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(openArchiveOnPullRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(openArchiveOnPullRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkMainTabsRows() {
        boolean hideBottomNavigationBar = NaConfig.INSTANCE.getHideBottomNavigationBar().Bool();
        if (listAdapter == null) {
            if (hideBottomNavigationBar) {
                cellGroup.rows.remove(hideTitlesRow);
                cellGroup.rows.remove(hideContactsRow);
            }
            return;
        }
        boolean changed = false;
        if (!hideBottomNavigationBar) {
            if (!cellGroup.rows.contains(hideContactsRow)) {
                int index = cellGroup.rows.indexOf(hideBottomNavigationBarRow);
                cellGroup.rows.add(index, hideContactsRow);
                listAdapter.notifyItemInserted(index);
                changed = true;
            }
            if (!cellGroup.rows.contains(hideTitlesRow)) {
                int index = cellGroup.rows.indexOf(hideContactsRow);
                cellGroup.rows.add(index, hideTitlesRow);
                listAdapter.notifyItemInserted(index);
                changed = true;
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(hideContactsRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(hideContactsRow);
                listAdapter.notifyItemRemoved(rowIndex);
                changed = true;
            }
            rowIndex = cellGroup.rows.indexOf(hideTitlesRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(hideTitlesRow);
                listAdapter.notifyItemRemoved(rowIndex);
                changed = true;
            }
        }
        if (changed) {
            addRowsToMap(cellGroup);
        }
    }

    private boolean shouldShowPersian() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        return locale != null && locale.getLanguage().equals("fa");
    }

    private boolean isCentered() {
        return NaConfig.INSTANCE.getCenterActionBarTitle().Bool() && NaConfig.INSTANCE.getCenterActionBarTitleType().Int() != 3;
    }

    private void animateActionBarUpdate(BaseNekoXSettingsActivity fragment) {
        boolean centered = isCentered();
        ActionBar actionBar = fragment.getActionBar();
        if (wasCentered == centered) {
            return;
        }
        if (actionBar != null) {
            SimpleTextView titleTextView = actionBar.getTitleTextView();
            if (centeredMeasure == -1) {
                centeredMeasure = actionBar.getMeasuredWidth() / 2f - titleTextView.getTextWidth() / 2f - dp((AndroidUtilities.isTablet() ? 80 : 72));
            }
            titleTextView.animate().translationX(centeredMeasure * (centered ? 1 : 0) - (wasCenteredAtBeginning ? Math.abs(centeredMeasure) : 0)).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    wasCentered = centered;
                    reloadUI(0);
                    LaunchActivity.makeRipple(centered ? (actionBar.getMeasuredWidth() / 2f) : 0, 0, centered ? 1.3f : 0.1f);
                }
            }).start();
        } else {
            reloadUI(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
    }

    private void reloadUI(int flags) {
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (layoutManager != null) {
            recyclerViewState = layoutManager.onSaveInstanceState();
            parentLayout.rebuildFragments(flags);
            layoutManager.onRestoreInstanceState(recyclerViewState);
        }
    }

    private String formatCustomSavePathDetail(String rawValue) {
        String folderName = rawValue == null ? "" : rawValue.trim();
        if (NaConfig.INSTANCE.getSaveToChatSubfolder().Bool()) {
            folderName = TextUtils.isEmpty(folderName) ? "<chat_name>" : folderName + File.separator + "<chat_name>";
        }
        return buildCustomSaveAbsolutePath(Environment.DIRECTORY_DOWNLOADS, folderName);
    }

    private String buildCustomSaveAbsolutePath(String directory, String folderName) {
        File root = Environment.getExternalStoragePublicDirectory(directory);
        if (TextUtils.isEmpty(folderName)) {
            return root.getAbsolutePath();
        }
        return new File(root, folderName).getAbsolutePath();
    }

    private boolean shouldShowCustomSavePathInputError(String input, String output) {
        if (TextUtils.isEmpty(input)) {
            return false;
        }
        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return !normalized.equals(output);
    }

    private String sanitizeCustomSavePath(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.matches("^(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}$")) {
            return normalized;
        }
        return (String) NekoConfig.customSavePath.defaultValue;
    }
}
