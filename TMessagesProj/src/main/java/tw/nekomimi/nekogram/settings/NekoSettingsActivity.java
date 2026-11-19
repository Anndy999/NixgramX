package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.utils.AlertUtil;

public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_SYNC = 2;

    private int generalRow;
    private int translatorRow;
    private int chatRow;
    private int passcodeRow;
    private int experimentRow;
    private int categoriesEndRow;

    private int importSettingsRow;
    private int exportSettingsRow;
    private int resetSettingsRow;
    private int diagnosticsRow;
    private int appRestartRow;
    private int nSettingsEndRow;


    private int aboutRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        generalRow = addRow();
        translatorRow = addRow();
        chatRow = addRow();
        if (!PasscodeHelper.isSettingsHidden()) {
            passcodeRow = addRow();
        } else {
            passcodeRow = -1;
        }
        experimentRow = addRow();
        categoriesEndRow = addRow();

        exportSettingsRow = addRow();
        importSettingsRow = addRow();
        resetSettingsRow = addRow();
        diagnosticsRow = addRow();
        appRestartRow = addRow();
        nSettingsEndRow = addRow();

        aboutRow = addRow();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, resourcesProvider);
        menu.addItem(MENU_SYNC, R.drawable.cloud_sync, resourcesProvider);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SEARCH) {
                    showSettingsSearchDialog();
                } else if (id == MENU_SYNC) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        return view;
    }

    /**
     * @noinspection SizeReplaceableByIsEmpty
     */
    private void showSettingsSearchDialog() {
        try {
            Activity parent = getParentActivity();
            if (parent == null) return;

            ArrayList<SettingsSearchResult> results = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));

            final ArrayList<SettingsSearchResult> filtered = new ArrayList<>(results);
            Locale locale = java.util.Objects.requireNonNullElse(LocaleController.getInstance().getCurrentLocale(), Locale.getDefault());
            String[] indexedTitles = new String[results.size()];
            for (int i = 0; i < results.size(); i++) {
                indexedTitles[i] = results.get(i).searchTitle.toLowerCase(locale);
            }
            final String[] currentQuery = {""};
            final String[][] queryParts = {new String[0]};
            final int searchHeight = dp(36);
            final int clearSize = dp(36);
            final int pad = dp(12);

            LinearLayout containerLayout = new LinearLayout(parent);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            containerLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            FrameLayout searchFrame = new FrameLayout(parent);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, searchHeight + dp(12));
            layoutParams.leftMargin = dp(10);
            layoutParams.rightMargin = dp(10);
            layoutParams.topMargin = dp(6);
            layoutParams.bottomMargin = dp(2);
            searchFrame.setLayoutParams(layoutParams);
            searchFrame.setClipToPadding(true);
            searchFrame.setClipChildren(true);

            ImageView searchIcon = new ImageView(parent);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.ic_ab_search_solar);
            ThemeBind.colorFilter(searchIcon, Theme.key_windowBackgroundWhiteGrayIcon, resourceProvider, PorterDuff.Mode.MULTIPLY);
            searchFrame.addView(searchIcon, LayoutHelper.createFrameRelatively(48, 48, Gravity.START | Gravity.CENTER_VERTICAL));

            EditTextBoldCursor searchField = new EditTextBoldCursor(parent);
            searchField.setHint(getString(R.string.Search));
            ThemeBind.textColor(searchField, Theme.key_dialogTextBlack, resourceProvider);
            ThemeBind.hintTextColor(searchField, Theme.key_windowBackgroundWhiteHintText, resourceProvider);
            searchField.setSingleLine(true);
            searchField.setBackground(null);
            searchField.setInputType(InputType.TYPE_CLASS_TEXT);
            ThemeBind.lineColors(searchField, Theme.key_windowBackgroundWhiteInputField, Theme.key_windowBackgroundWhiteInputFieldActivated, Theme.key_text_RedRegular, resourceProvider);
            searchField.setPadding(dp(LocaleController.isRTL ? 48 : 61), pad / 2,
                    dp(LocaleController.isRTL ? 61 : 48), pad / 2);
            searchField.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
            searchFrame.addView(searchField);

            ImageView clearButton = new ImageView(parent);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setImageResource(R.drawable.ic_close_white);
            ThemeBind.selectorBackground(clearButton, Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP),
                    null, ThemeBind.of(Theme.key_listSelector), resourceProvider);
            clearButton.setContentDescription(getString(R.string.ClearButton));
            ThemeBind.colorFilter(clearButton, Theme.key_windowBackgroundWhiteGrayIcon, resourceProvider, PorterDuff.Mode.MULTIPLY);
            clearButton.setLayoutParams(new FrameLayout.LayoutParams(clearSize, clearSize, LayoutHelper.getAbsoluteGravityEnd() | Gravity.CENTER_VERTICAL));
            searchFrame.addView(clearButton);
            containerLayout.addView(searchFrame);

            AlertDialog.Builder builder = new AlertDialog.Builder(parent, resourceProvider);
            builder.setView(containerLayout);
            builder.setNegativeButton(getString(R.string.Close), null);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                try {
                    searchField.requestFocus();
                    AndroidUtilities.showKeyboard(searchField);
                } catch (Exception ignore) {
                }
            });

            RecyclerListView searchListView = new RecyclerListView(parent);
            searchListView.setOverScrollMode(OVER_SCROLL_NEVER);
            searchListView.setLayoutManager(new LinearLayoutManager(parent, LinearLayoutManager.VERTICAL, false));

            var adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent1, int viewType) {
                    View view = new SettingsSearchCell(parent);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
                    SettingsSearchResult r = filtered.get(position);
                    String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
                    CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
                    if (queryParts[0].length > 0 && titleToSet.length() > 0) {
                        SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                        String lower = titleToSet.toString().toLowerCase(locale);
                        for (String p : queryParts[0]) {
                            int idx = 0;
                            while (true) {
                                int found = lower.indexOf(p, idx);
                                if (found == -1) break;
                                int end = found + p.length();
                                if (end <= ss.length() && titleToSet.toString().regionMatches(true, found, p, 0, p.length())) {
                                    ss.setSpan(new CharacterStyle() {
                                        @Override
                                        public void updateDrawState(TextPaint paint) {
                                            paint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
                                        }
                                    }, found, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                idx = found + p.length();
                            }
                        }
                        titleToSet = ss;
                    }
                    cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, position < filtered.size() - 1);
                }

                @Override
                public int getItemCount() {
                    return filtered.size();
                }
            };

            searchListView.setAdapter(adapter);
            searchListView.setOnItemClickListener((v, position) -> {
                if (position < 0 || position >= filtered.size()) return;
                SettingsSearchResult r = filtered.get(position);
                try {
                    if (r.openRunnable != null) r.openRunnable.run();
                } catch (Exception ignore) {
                }
                dialog.dismiss();
            });

            FrameLayout resultsFrame = new FrameLayout(parent);
            resultsFrame.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            TextView noResults = new TextView(parent);
            noResults.setText(R.string.NoResult);
            noResults.setGravity(Gravity.CENTER);
            noResults.setMinHeight(dp(56));
            ThemeBind.textColor(noResults, Theme.key_dialogTextGray3, resourceProvider);
            resultsFrame.addView(noResults, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            searchListView.setEmptyView(noResults);
            containerLayout.addView(resultsFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void afterTextChanged(Editable s) {
                    String q = s.toString().toLowerCase(locale).trim();
                    if (q.equals(currentQuery[0])) return;
                    currentQuery[0] = q;
                    queryParts[0] = q.isEmpty() ? new String[0] : q.split("\\s+");
                    filtered.clear();
                    if (q.isEmpty()) {
                        filtered.addAll(results);
                    } else {
                        for (int i = 0; i < indexedTitles.length; i++) {
                            String title = indexedTitles[i];
                            boolean ok = true;
                            for (String p : queryParts[0]) {
                                if (!title.contains(p)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) filtered.add(results.get(i));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    searchIcon.setVisibility(q.length() > 20 ? View.GONE : View.VISIBLE);
                    clearButton.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });

            clearButton.setOnClickListener(v -> {
                searchField.setText("");
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            });
            clearButton.setVisibility(View.GONE);

            showDialog(dialog);
        } catch (Exception ignore) {
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.NekoSettings);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == diagnosticsRow) {
            org.telegram.messenger.diagnostics.Diagnostics.show(getParentActivity());
        } else if (position == chatRow) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (position == generalRow) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (position == passcodeRow) {
            presentFragment(new NekoPasscodeSettingsActivity());
        } else if (position == experimentRow) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == aboutRow) {
            presentFragment(new NekoAboutActivity());
        } else if (position == importSettingsRow) {
            if (Build.VERSION.SDK_INT >= 33) {
                openFilePicker();
            } else {
                DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                if (activity != null) {
                    presentFragment(activity);
                }
            }
        } else if (position == resetSettingsRow) {
            AlertUtil.showConfirm(getParentActivity(),
                    getString(R.string.ResetSettingsAlert),
                    R.drawable.msg_reset,
                    getString(R.string.Reset),
                    true,
                    () -> {
                        ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                        NekoConfig.getPreferences().edit().clear().commit();
                        AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                    });
        } else if (position == exportSettingsRow) {
            SettingsBackupHelper.backupSettings(getParentActivity(), resourceProvider);
        } else if (position == appRestartRow) {
            AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            switch (viewType) {
                case TYPE_SHADOW: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_TEXT: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == diagnosticsRow) {
                        textCell.setTextAndIcon(getString(R.string.NixDiagnostics), R.drawable.msg_info, true);
                    } else if (position == chatRow) {
                        textCell.setTextAndIcon(getString(R.string.Chat), R.drawable.msg_discussion, true);
                    } else if (position == generalRow) {
                        textCell.setTextAndIcon(getString(R.string.General), R.drawable.msg_theme, true);
                    } else if (position == translatorRow) {
                        textCell.setTextAndIcon(getString(R.string.TranslatorSettings), R.drawable.ic_translate, true);
                    } else if (position == passcodeRow) {
                        textCell.setTextAndIcon(getString(R.string.PasscodeNeko), R.drawable.msg_permissions, true);
                    } else if (position == experimentRow) {
                        textCell.setTextAndIcon(getString(R.string.Experimental), R.drawable.msg_fave, true);
                    } else if (position == importSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ImportSettings), R.drawable.import_solar, true);
                    } else if (position == exportSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.BackupSettings), R.drawable.export_solar, true);
                    } else if (position == resetSettingsRow) {
                        textCell.setTextAndIcon(getString(R.string.ResetSettings), R.drawable.msg_reset_solar, true);
                    } else if (position == appRestartRow) {
                        textCell.setTextAndIcon(getString(R.string.RestartApp), R.drawable.msg_retry_solar, true);
                    } else if (position == aboutRow) {
                        textCell.setTextAndIcon(getString(R.string.About), R.drawable.msg_info, true);
                    }
                    break;
                }

            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == categoriesEndRow || position == nSettingsEndRow) {
                return TYPE_SHADOW;
            } else if (position == chatRow || position == generalRow || position == passcodeRow || position == experimentRow || position == translatorRow ||
                    position == importSettingsRow || position == exportSettingsRow || position == resetSettingsRow || position == appRestartRow ||
                    position == aboutRow || position == diagnosticsRow) {
                return TYPE_TEXT;
            }
            return TYPE_SHADOW;
        }
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                SettingsBackupHelper.importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try {
                    final InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        OutputStream outputStream = new FileOutputStream(file);
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        SettingsBackupHelper.importSettings(getParentActivity(), file);
                    }
                } catch (Exception ignore) {
                }
            }
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }
}
