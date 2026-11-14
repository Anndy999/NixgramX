package org.telegram.messenger.diagnostics;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.SuggestionSpan;
import android.text.style.TextAppearanceSpan;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.TextAttribute;
import android.widget.EditText;

import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Category;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Field;
import org.telegram.messenger.diagnostics.NgxDiagnosticCore.Value;

/**
 * Temporary manual-capture instrumentation: no text contents or derived text hashes.
 * Base paint and sampled span colors are evidence, not final per-run drawing colors.
 * Unrelated diagnostics can still consume the global 50-event capture allowance.
 */
public final class InputTextDiagnostics {
    private enum Operation { COMMIT, COMPOSE, FINISH }
    private enum Failure { NULL_POINTER, INDEX_BOUNDS, ILLEGAL_ARGUMENT, RUNTIME, OTHER }
    private enum Bucket { COMMIT, CONTROL, DRAW, FINISH }
    private static final class Token {
        final long generation;
        final int id, argumentLength;
        final Operation operation;
        final Bucket bucket;
        Token(long generation, int id, int argumentLength, Operation operation, Bucket bucket) {
            this.generation = generation;
            this.id = id;
            this.argumentLength = argumentLength;
            this.operation = operation;
            this.bucket = bucket;
        }
    }
    private static long generation = -1;
    private static int commits, compositions, finishes, finishSnapshots, emptyCommits, sequence;
    private static final int[] events = new int[4];
    // Separate reservations; the first nonempty FINISH gets its own compact frame.
    private static final int[] LIMITS = {24, 10, 4, 4};

    private InputTextDiagnostics() {}

    private static boolean active() {
        return NgxDiagnostics.isCapturing();
    }

    private static void refresh() {
        long current = NgxDiagnostics.captureGeneration();
        if (generation != current) {
            generation = current;
            commits = compositions = finishes = finishSnapshots = emptyCommits = sequence = 0;
            java.util.Arrays.fill(events, 0);
        }
    }

    private static boolean current(Token token) {
        return token != null && active() && token.generation == NgxDiagnostics.captureGeneration();
    }

    private static synchronized void emit(Token token, String fixedName, Value... values) {
        try {
            if (!current(token)) return;
            refresh();
            int bucket = token.bucket.ordinal();
            if (events[bucket] >= LIMITS[bucket]) return;
            events[bucket]++;
            NgxDiagnostics.event(Category.UI, fixedName, values);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized Token start(Operation operation, CharSequence argument) {
        try {
            if (!active()) return null;
            refresh();
            int size = length(argument);
            Bucket bucket = Bucket.CONTROL;
            if (operation == Operation.COMPOSE) {
                if (compositions >= 2) return null;
                compositions++;
            } else if (operation == Operation.FINISH) {
                if (size > 0 && finishSnapshots == 0) {
                    finishSnapshots++;
                    bucket = Bucket.FINISH;
                } else {
                    if (finishes >= 2) return null;
                    finishes++;
                }
            } else if (size <= 0) {
                if (emptyCommits >= 1) return null;
                emptyCommits++;
            } else {
                if (commits >= 3) return null;
                commits++;
                bucket = Bucket.COMMIT;
            }
            return new Token(generation, ++sequence, operation == Operation.FINISH ? -1 : size, operation, bucket);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Token startFinish(EditText view) {
        try {
            return active() ? start(Operation.FINISH, view.getText()) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized Token drawToken() {
        try {
            if (!active()) return null;
            refresh();
            if (events[Bucket.DRAW.ordinal()] >= LIMITS[Bucket.DRAW.ordinal()]) return null;
            return new Token(generation, 0, -1, null, Bucket.DRAW);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void state(EditText view, String event, Token token, boolean result) {
        try {
            if (!current(token)) return;
            Editable text = view.getText();
            emit(token, event, Value.integer(Field.OP_SEQUENCE, token.id), Value.enumValue(Field.TRIGGER, token.operation),
                    Value.integer(Field.ARG_LENGTH, token.argumentLength),
                    Value.integer(Field.TEXT_LENGTH, text == null ? 0 : text.length()),
                    Value.integer(Field.COMPOSING_START, text == null ? -1 : BaseInputConnection.getComposingSpanStart(text)),
                    Value.integer(Field.COMPOSING_END, text == null ? -1 : BaseInputConnection.getComposingSpanEnd(text)),
                    Value.integer(Field.SELECTION_START, view.getSelectionStart()),
                    Value.integer(Field.SELECTION_END, view.getSelectionEnd()), Value.bool(Field.RESULT, result));
        } catch (Throwable ignored) {
        }
    }

    private static int length(CharSequence text) {
        try { return text == null ? -1 : text.length(); } catch (Throwable ignored) { return -2; }
    }

    public static InputConnection wrap(InputConnection target, EditText view) {
        if (target == null) return null;
        return Build.VERSION.SDK_INT >= 33 ? new Api33Connection(target, view) : new BasicConnection(target, view);
    }

    private static class BasicConnection extends InputConnectionWrapper {
        protected final EditText view;

        BasicConnection(InputConnection target, EditText view) {
            super(target, false);
            this.view = view;
        }
            @Override
            public boolean commitText(CharSequence text, int cursor) {
                Token token = start(Operation.COMMIT, text);
                state(view, "INPUT_BEFORE", token, false);
                boolean result = super.commitText(text, cursor);
                state(view, "INPUT_AFTER", token, result);
                frame(view, token);
                return result;
            }

            @Override
            public boolean setComposingText(CharSequence text, int cursor) {
                Token token = start(Operation.COMPOSE, text);
                state(view, "INPUT_BEFORE", token, false);
                boolean result = super.setComposingText(text, cursor);
                state(view, "INPUT_AFTER", token, result);
                return result;
            }

            @Override
            public boolean finishComposingText() {
                Token token = startFinish(view);
                state(view, "INPUT_BEFORE", token, false);
                boolean result = super.finishComposingText();
                state(view, "INPUT_AFTER", token, result);
                frame(view, token);
                return result;
            }

    }

    @android.annotation.TargetApi(33)
    private static final class Api33Connection extends BasicConnection {
        Api33Connection(InputConnection target, EditText view) {
            super(target, view);
        }

            @Override
            public boolean commitText(CharSequence text, int cursor, TextAttribute attributes) {
                Token token = start(Operation.COMMIT, text);
                state(view, "INPUT_BEFORE", token, false);
                boolean result = super.commitText(text, cursor, attributes);
                state(view, "INPUT_AFTER", token, result);
                frame(view, token);
                return result;
            }

            @Override
            public boolean setComposingText(CharSequence text, int cursor, TextAttribute attributes) {
                Token token = start(Operation.COMPOSE, text);
                state(view, "INPUT_BEFORE", token, false);
                boolean result = super.setComposingText(text, cursor, attributes);
                state(view, "INPUT_AFTER", token, result);
                return result;
            }
    }

    private static void frame(EditText view, Token token) {
        try {
            if (!current(token) || (token.bucket != Bucket.COMMIT && token.bucket != Bucket.FINISH)) return;
            view.postOnAnimation(new Runnable() {
                @Override
                public void run() {
                try {
                    if (!current(token)) return;
                    Layout layout = view.getLayout();
                    Rect bounds = new Rect();
                    int baseline = layout != null && layout.getLineCount() > 0 ? layout.getLineBounds(0, bounds) : -1;
                    emit(token, "INPUT_FRAME", Value.integer(Field.OP_SEQUENCE, token.id),
                            Value.integer(Field.PAINT_COLOR, view.getPaint().getColor()),
                            Value.integer(Field.VIEW_ALPHA_MILLI, Math.round(view.getAlpha() * 1000)),
                            Value.integer(Field.TEXT_SIZE_MILLI, Math.round(view.getTextSize() * 1000)),
                            Value.integer(Field.LINE_COUNT, layout == null ? -1 : layout.getLineCount()),
                            Value.integer(Field.HEIGHT, view.getHeight()), Value.integer(Field.SCROLL_Y, view.getScrollY()),
                            Value.integer(Field.DY, baseline), Value.integer(Field.OLD_STATE, bounds.top),
                            Value.integer(Field.NEW_STATE, bounds.bottom));
                    Editable text = view.getText();
                    if (text == null) return;
                    ForegroundColorSpan[] fg = text.getSpans(0, text.length(), ForegroundColorSpan.class);
                    TextAppearanceSpan[] appearances = text.getSpans(0, text.length(), TextAppearanceSpan.class);
                    android.content.res.ColorStateList appearanceColor = appearances.length == 0 ? null : appearances[0].getTextColor();
                    emit(token, "INPUT_SPANS", Value.integer(Field.OP_SEQUENCE, token.id),
                            Value.integer(Field.TEXT_LENGTH, text.length()),
                            Value.integer(Field.COMPOSING_START, BaseInputConnection.getComposingSpanStart(text)),
                            Value.integer(Field.COMPOSING_END, BaseInputConnection.getComposingSpanEnd(text)),
                            Value.integer(Field.SPAN_COUNT, text.getSpans(0, text.length(), Object.class).length),
                            Value.integer(Field.FOREGROUND_COUNT, fg.length),
                            Value.integer(Field.APPEARANCE_COUNT, appearances.length),
                            Value.integer(Field.FIRST_FOREGROUND_COLOR, fg.length == 0 ? 0 : fg[0].getForegroundColor()),
                            Value.integer(Field.FIRST_APPEARANCE_COLOR, appearanceColor == null ? 0 : appearanceColor.getColorForState(view.getDrawableState(), appearanceColor.getDefaultColor())),
                            Value.bool(Field.RESULT, appearanceColor != null),
                            Value.integer(Field.REPLACEMENT_COUNT, text.getSpans(0, text.length(), ReplacementSpan.class).length),
                            Value.integer(Field.SUGGESTION_COUNT, text.getSpans(0, text.length(), SuggestionSpan.class).length));
                    if (token.bucket == Bucket.FINISH) return;
                    for (int i = 0; i < Math.min(2, fg.length); i++) {
                        emit(token, "INPUT_FOREGROUND", Value.integer(Field.OP_SEQUENCE, token.id),
                                Value.integer(Field.PAINT_COLOR, fg[i].getForegroundColor()),
                                Value.integer(Field.INDEX, text.getSpanStart(fg[i])),
                                Value.integer(Field.TARGET_INDEX, text.getSpanEnd(fg[i])));
                    }
                    for (int i = 0; i < Math.min(2, appearances.length); i++) {
                        if (appearances[i].getTextColor() == null) continue;
                        int color = appearances[i].getTextColor().getColorForState(view.getDrawableState(),
                                appearances[i].getTextColor().getDefaultColor());
                        emit(token, "INPUT_APPEARANCE", Value.integer(Field.OP_SEQUENCE, token.id),
                                Value.integer(Field.PAINT_COLOR, color),
                                Value.integer(Field.INDEX, text.getSpanStart(appearances[i])),
                                Value.integer(Field.TARGET_INDEX, text.getSpanEnd(appearances[i])));
                    }
                } catch (Throwable ignored) {
                }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static int saveCount(Canvas canvas) {
        try { return active() ? canvas.getSaveCount() : -1; } catch (Throwable ignored) { return -1; }
    }

    public static void drawFailure(Exception failure, Canvas canvas, int before, boolean drawInMaim) {
        try {
            Token token = drawToken();
            if (token == null) return;
            Failure kind = failure instanceof NullPointerException ? Failure.NULL_POINTER :
                    failure instanceof IndexOutOfBoundsException ? Failure.INDEX_BOUNDS :
                    failure instanceof IllegalArgumentException ? Failure.ILLEGAL_ARGUMENT :
                    failure instanceof RuntimeException ? Failure.RUNTIME : Failure.OTHER;
            emit(token, "INPUT_DRAW_FAILURE", Value.enumValue(Field.ERROR_CATEGORY, kind),
                    Value.integer(Field.OLD_STATE, before), Value.integer(Field.NEW_STATE, canvas.getSaveCount()),
                    Value.bool(Field.RESULT, drawInMaim));
        } catch (Throwable ignored) {
        }
    }
}
