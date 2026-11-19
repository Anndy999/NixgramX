package tw.nekomimi.nekogram.parts

import android.app.Activity
import android.content.Context
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.source.LLMTranslator
import tw.nekomimi.nekogram.utils.AlertUtil
import java.util.Locale
import java.util.function.BooleanSupplier
import java.util.function.Consumer

object InputTranslation {
    @JvmStatic
    fun start(
        context: Context?,
        editor: TextView?,
        target: Locale,
        provider: Int,
        selectionStart: Int,
        selectionEnd: Int,
        translationContext: String?,
        isCurrent: BooleanSupplier,
        apply: Consumer<String>
    ) {
        if (context == null || editor == null) return
        val original = editor.text.toString()
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, original.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, original.length)
        val query = if (start == end) original else original.substring(start, end)
        val effectiveProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()

        fun canApply(): Boolean =
            (context !is Activity || !context.isFinishing && !context.isDestroyed) &&
                editor.isAttachedToWindow && isCurrent.asBoolean && editor.text.toString() == original

        fun request() {
            if (!canApply()) return
            val progress = AlertUtil.showProgress(context)
            progress.show()
            val job = CoroutineScope(Dispatchers.Main).launch {
                try {
                    val translation = withContext(Dispatchers.IO) {
                        LLMTranslator.withTranslationContext(translationContext) {
                            Translator.translate(target, query, effectiveProvider)
                        }
                    }
                    val applyResult = progress.isShowing && canApply()
                    progress.dismiss()
                    if (applyResult) apply.accept(translation)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val showError = progress.isShowing && canApply()
                    progress.dismiss()
                    if (showError) {
                        AlertUtil.showTransFailedDialog(context, e is UnsupportedOperationException, e.message ?: e.javaClass.simpleName) {
                            request()
                        }
                    }
                }
            }
            progress.setOnCancelListener { job.cancel() }
            progress.setOnDismissListener { job.cancel() }
        }
        request()
    }
}
