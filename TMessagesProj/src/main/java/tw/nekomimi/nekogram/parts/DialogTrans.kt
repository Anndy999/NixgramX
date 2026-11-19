package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.helpers.MessageHelper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.utils.AlertUtil
import xyz.nextalone.nagram.NaConfig

@JvmOverloads
fun startTrans(
    ctx: Context,
    text: String,
    toLang: String = NekoConfig.translateToLang.String(),
    provider: Int = 0
) {

    val dialog = AlertUtil.showProgress(ctx)
    val finalToLang = toLang.code2Locale
    val finalProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
    val appendOriginal = MessageHelper.shouldKeepOriginalForManualTranslation(NaConfig.translatorMode.Int())

    dialog.show()
    val job = CoroutineScope(Dispatchers.Main).launch {
        val result = try {
            withContext(Dispatchers.IO) { Translator.translate(finalToLang, text, finalProvider) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (dialog.isShowing) {
                dialog.dismiss()
                AlertUtil.showTransFailedDialog(ctx, e is UnsupportedOperationException, e.message ?: e.javaClass.simpleName) {
                    startTrans(ctx, text, toLang, finalProvider)
                }
            }
            return@launch
        }
        if (dialog.isShowing) {
            dialog.dismiss()
            val finalText = MessageHelper.buildTranslatedDisplayText(text, result, appendOriginal)
            AlertUtil.showCopyAlert(ctx, finalText)
        }
    }
    dialog.setOnCancelListener { job.cancel() }
    dialog.setOnDismissListener { job.cancel() }
}
