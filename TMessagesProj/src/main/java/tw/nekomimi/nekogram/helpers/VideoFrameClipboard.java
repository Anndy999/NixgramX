package tw.nekomimi.nekogram.helpers;

import android.graphics.Bitmap;
import android.os.Build;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.DispatchQueue2;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;

import static org.telegram.messenger.LocaleController.getString;

public final class VideoFrameClipboard {
    private static final DispatchQueue2 worker = new DispatchQueue2("videoFrameClipboard");
    // Both admission and completion run on the UI thread, including PixelCopy callbacks.
    private static boolean busy;

    private final View container;
    private final Theme.ResourcesProvider resources;

    private VideoFrameClipboard(View container, Theme.ResourcesProvider resources) {
        this.container = container;
        this.resources = resources;
    }

    private static VideoFrameClipboard begin(View container, Theme.ResourcesProvider resources) {
        if (busy) {
            if (container instanceof FrameLayout layout) {
                BulletinFactory.of(layout, resources).createSimpleBulletin(R.raw.copy, getString(R.string.CopyFrameInProgress)).show();
            }
            return null;
        }
        busy = true;
        return new VideoFrameClipboard(container, resources);
    }

    public static void fromFile(File file, long positionMs, View container, Theme.ResourcesProvider resources, Runnable fallback) {
        VideoFrameClipboard request = begin(container, resources);
        if (request == null) return;
        worker.postRunnable(() -> {
            Bitmap frame = null;
            try {
                if (file != null && file.isFile()) frame = MessageHelper.createVideoFrameBitmap(file, positionMs);
            } catch (RuntimeException | OutOfMemoryError e) {
                FileLog.e(e);
            }
            if (frame != null) {
                request.encode(frame);
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    busy = false;
                    if (fallback != null) fallback.run();
                });
            }
        });
    }

    public static void fromDisplay(SurfaceView surface, TextureView texture, BooleanSupplier sameFrame,
                                   View container, Theme.ResourcesProvider resources, Runnable onFailure) {
        VideoFrameClipboard request = begin(container, resources);
        if (request == null) return;
        Bitmap bitmap = null;
        try {
            if (sameFrame.getAsBoolean()) {
                if (surface != null && Build.VERSION.SDK_INT >= 24 && surface.getWidth() > 0 && surface.getHeight() > 0
                        && surface.getHolder().getSurface().isValid()) {
                    bitmap = Bitmaps.createBitmap(surface.getWidth(), surface.getHeight(), Bitmap.Config.ARGB_8888);
                    final Bitmap target = bitmap;
                    PixelCopy.request(surface, target, result -> {
                        if (result == PixelCopy.SUCCESS && sameFrame.getAsBoolean()) {
                            worker.postRunnable(() -> request.encode(target));
                        } else {
                            target.recycle();
                            busy = false;
                            onFailure.run();
                        }
                    }, ApplicationLoader.applicationHandler);
                    return;
                }
                if (texture != null && texture.isAvailable() && texture.getWidth() > 0 && texture.getHeight() > 0) {
                    final Bitmap frame = texture.getBitmap();
                    if (frame != null) {
                        worker.postRunnable(() -> request.encode(frame));
                        return;
                    }
                }
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            FileLog.e(e);
        }
        if (bitmap != null) bitmap.recycle();
        busy = false;
        onFailure.run();
    }

    private void encode(Bitmap frame) {
        File output = null;
        boolean saved = false;
        try {
            output = File.createTempFile("frame_", ".png", FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE));
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!frame.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new java.io.IOException("PNG encoding failed");
            }
            saved = true;
        } catch (Exception | OutOfMemoryError e) {
            FileLog.e(e);
        } finally {
            frame.recycle();
        }
        final File png = saved ? output : null;
        if (!saved && output != null) output.delete();
        AndroidUtilities.runOnUIThread(() -> {
            busy = false;
            if (png != null) {
                MessageHelper.addFileToClipboard(png, container instanceof FrameLayout layout
                        ? () -> BulletinFactory.of(layout, resources).createCopyBulletin(getString(R.string.PhotoCopied)).show() : null);
            } else if (container instanceof FrameLayout layout) {
                BulletinFactory.of(layout, resources).createErrorBulletin(getString(R.string.UnknownError)).show();
            }
        });
    }
}
