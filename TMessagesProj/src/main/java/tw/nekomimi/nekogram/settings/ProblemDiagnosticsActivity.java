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
import org.telegram.messenger.diagnostics.NgxDiagnostics;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class ProblemDiagnosticsActivity extends BaseNekoSettingsActivity implements NgxDiagnostics.Listener {

    private int headerRow;
    private int infoRow;
    private int actionRow;
    private int exportRow;
    private int clearRow;
    private int advancedRow;
    private int footerRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        boolean capturing = NgxDiagnostics.isCapturing();
        headerRow = addRow();
        infoRow = addRow();
        actionRow = addRow();
        if (capturing) {
            exportRow = -1;
            clearRow = -1;
        } else {
            exportRow = addRow();
            clearRow = addRow();
        }
        advancedRow = addRow();
        footerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.ProblemDiagnostics);
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
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == actionRow) {
            if (NgxDiagnostics.isCapturing()) {
                NgxDiagnostics.cancelCapture();
            } else {
                NgxDiagnostics.capture();
            }
            onDiagnosticsChanged();
        } else if (position == exportRow) {
            export();
        } else if (position == clearRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.ProblemDiagnosticsClear))
                    .setMessage(getString(R.string.ProblemDiagnosticsClearConfirm))
                    .setPositiveButton(getString(R.string.Clear), (d, w) -> {
                        NgxDiagnostics.clear();
                        onDiagnosticsChanged();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (position == advancedRow) {
            presentFragment(new NgxDiagnosticsSettingsActivity());
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
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(getString(R.string.ProblemDiagnostics));
                    break;
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == actionRow) {
                        if (NgxDiagnostics.isCapturing()) {
                            cell.setText(getString(R.string.ProblemDiagnosticsStop), true);
                        } else {
                            cell.setText(getString(R.string.ProblemDiagnosticsStart), true);
                        }
                    } else if (position == exportRow) {
                        cell.setText(getString(R.string.ProblemDiagnosticsExport), true);
                    } else if (position == clearRow) {
                        cell.setText(getString(R.string.ProblemDiagnosticsClear), true);
                    } else if (position == advancedRow) {
                        cell.setText(getString(R.string.ProblemDiagnosticsAdvanced), false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        if (NgxDiagnostics.isCapturing()) {
                            cell.setText(getString(R.string.ProblemDiagnosticsRecordingHint));
                        } else if (NgxDiagnostics.hasDiagnostics()) {
                            cell.setText(getString(R.string.ProblemDiagnosticsDone));
                        } else {
                            cell.setText(getString(R.string.ProblemDiagnosticsInfo));
                        }
                    } else {
                        cell.setText("");
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return TYPE_HEADER;
            if (position == actionRow || position == exportRow || position == clearRow || position == advancedRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
