package tw.nekomimi.nekogram.parts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ArticleViewer
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.AlertUtil

private fun extractTextPlainLeaves(
    richText: TL_iv.RichText, leaves: MutableList<TL_iv.textPlain>
) {
    when (richText) {
        is TL_iv.textPlain -> {
            if (richText.text.isNotBlank()) {
                leaves.add(richText)
            }
        }

        is TL_iv.textConcat -> richText.texts.forEach { extractTextPlainLeaves(it, leaves) }
        is TL_iv.textBold -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textItalic -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textUnderline -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textStrike -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textFixed -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textUrl -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textEmail -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textPhone -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textMarked -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textSubscript -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textSuperscript -> extractTextPlainLeaves(richText.text, leaves)
        is TL_iv.textAnchor -> extractTextPlainLeaves(richText.text, leaves)
    }
}

fun ArticleViewer.doTransLATE() {
    val page = pages.firstOrNull() ?: return
    val context = parentActivity ?: return
    val adapter = page.adapter
    val article = adapter.translationSourcePage ?: return
    val texts = buildSet {
        for (item in adapter.textBlocks) {
            when (item) {
                is TL_iv.RichText -> {
                    val leaves = mutableListOf<TL_iv.textPlain>()
                    extractTextPlainLeaves(item, leaves)
                    leaves.forEach { add(it.text) }
                }
                is String -> if (item.isNotBlank()) add(item)
            }
        }
    }
    if (texts.isEmpty()) return
    fun isCurrent() = isVisible && pages.firstOrNull() === page && adapter.translationSourcePage === article

    val status = AlertUtil.showProgress(context, "0 / ${texts.size}")
    status.show()
    val job = CoroutineScope(Dispatchers.Main).launch {
        val dispatcher = Dispatchers.IO.limitedParallelism(5)
        var completed = 0
        val translated = try {
            coroutineScope {
                texts.map { original ->
                    async {
                        val translation = withContext(dispatcher) {
                            Translator.translateArticle(original)
                        }
                        completed++
                        status.setMessage("$completed / ${texts.size}")
                        original to translation
                    }
                }.awaitAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (status.isShowing && isCurrent()) {
                status.dismiss()
                AlertUtil.showTransFailedDialog(context, e is UnsupportedOperationException, e.message ?: e.javaClass.simpleName) {
                    if (isCurrent()) doTransLATE()
                }
            } else {
                status.dismiss()
            }
            return@launch
        }

        if (status.isShowing && isCurrent()) {
            adapter.translatedTextCache.clear()
            adapter.translatedTextCache.putAll(translated)
            updateTranslateButton(true)
            updatePaintSize()
        }
        status.dismiss()
    }
    status.setOnCancelListener { job.cancel() }
    status.setOnDismissListener { job.cancel() }
}
