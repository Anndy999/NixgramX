package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Level;
import org.telegram.messenger.diagnostics.NgxDiagnostics;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class NgxDiagnosticsSettingsActivity extends BaseNekoSettingsActivity implements NgxDiagnostics.Listener {

    private int headerRow;
    private int levelRow;
    private int captureRow;
    private int cancelRow;
    private int exportRow;
    private int clearRow;
    private int selfTestRow;
    private int statusRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        headerRow = addRow();
        levelRow = addRow();
        captureRow = addRow();
        cancelRow = addRow();
        exportRow = addRow();
        clearRow = addRow();
        selfTestRow = addRow();
        statusRow = addRow();
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.NgxDiagnostics);
    }

    @Override
    public boolean onFragmentCreate() {
        NgxDiagnostics.init(ApplicationLoader.applicationContext);
        NgxDiagnostics.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NgxDiagnostics.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onDiagnosticsChanged() {
        refreshAdapterSafely();
    }

    private void refreshAdapterSafely() {
        if (listAdapter == null || listView == null) return;
        if (listView.isComputingLayout()) {
            listView.post(this::refreshAdapterSafely);
            return;
        }
        listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == levelRow) {
            if (NgxDiagnostics.isCapturing()) return;
            CharSequence[] items = new CharSequence[]{
                    getString(R.string.NgxDiagnosticsLevelOff),
                    getString(R.string.NgxDiagnosticsLevelDiagnostic),
                    getString(R.string.NgxDiagnosticsLevelTrace)
            };
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.NgxDiagnosticsLevel))
                    .setItems(items, (dialog, which) -> {
                        Level next = which == 1 ? Level.DIAGNOSTIC : which == 2 ? Level.TRACE : Level.OFF;
                        NgxDiagnostics.setLevel(next);
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    })
                    .show();
        } else if (position == captureRow) {
            if (NgxDiagnostics.isCapturing()) return;
            NgxDiagnostics.capture();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else if (position == cancelRow) {
            NgxDiagnostics.cancelCapture();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else if (position == exportRow) {
            export();
        } else if (position == clearRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.NgxDiagnosticsClear))
                    .setMessage(getString(R.string.NgxDiagnosticsClearInfo))
                    .setPositiveButton(getString(R.string.Clear), (d, w) -> {
                        NgxDiagnostics.clear();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (position == selfTestRow) {
            int n = NgxDiagnostics.runSelfTest();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.NgxDiagnosticsSelfTestDone) + " (" + n + ")").show();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        }
    }

    private void export() {
        final Context context = getParentActivity();
        if (context == null) return;
        Utilities.globalQueue.postRunnable(() -> {
            File zip = NgxDiagnostics.exportZip();
            AndroidUtilities.runOnUIThread(() -> {
                if (zip == null || !zip.isFile()) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                    return;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/zip");
                    Uri uri;
                    if (Build.VERSION.SDK_INT >= 24) {
                        uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", zip);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        uri = Uri.fromFile(zip);
                    }
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), 500);
                } catch (Exception ignored) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                }
            });
        });
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
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    ((HeaderCell) holder.itemView).setText(getString(R.string.NgxDiagnostics));
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    boolean capturing = NgxDiagnostics.isCapturing();
                    cell.setEnabled(!capturing, null);
                    String value = getString(R.string.NgxDiagnosticsLevelOff);
                    if (NgxDiagnostics.level() == Level.DIAGNOSTIC) value = getString(R.string.NgxDiagnosticsLevelDiagnostic);
                    else if (NgxDiagnostics.level() == Level.TRACE) value = getString(R.string.NgxDiagnosticsLevelTrace);
                    cell.setTextAndValue(getString(R.string.NgxDiagnosticsLevel), value, true);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == captureRow) {
                        if (NgxDiagnostics.isCapturing()) {
                            cell.setText(getString(R.string.NgxDiagnosticsCapturing) + " " + NgxDiagnostics.remaining(), true);
                        } else {
                            cell.setText(getString(R.string.NgxDiagnosticsCapture), true);
                        }
                    } else if (position == cancelRow) {
                        cell.setText(getString(R.string.NgxDiagnosticsCancelCapture), true);
                    } else if (position == exportRow) {
                        cell.setText(getString(R.string.NgxDiagnosticsExport), true);
                    } else if (position == clearRow) {
                        cell.setText(getString(R.string.NgxDiagnosticsClear), true);
                    } else if (position == selfTestRow) {
                        cell.setText(getString(R.string.NgxDiagnosticsSelfTest), false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == statusRow) {
                        cell.setText(getString(R.string.NgxDiagnosticsStatus)
                                + " " + NgxDiagnostics.storedEstimate()
                                + " / " + getString(R.string.NgxDiagnosticsDropped)
                                + " " + NgxDiagnostics.dropped()
                                + (NgxDiagnostics.isCapturing() ? " / " + getString(R.string.NgxDiagnosticsCapturingShort) : ""));
                    } else {
                        cell.setText(getString(R.string.NgxDiagnosticsInfo));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return TYPE_HEADER;
            if (position == levelRow) return TYPE_SETTINGS;
            if (position == captureRow || position == cancelRow || position == exportRow
                    || position == clearRow || position == selfTestRow) return TYPE_TEXT;
            return TYPE_INFO_PRIVACY;
        }
    }
}
