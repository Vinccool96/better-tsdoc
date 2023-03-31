package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.documentation.JSHtmlHighlightingUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.OPEN_PRE
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.CLOSE_PRE

class BetterTSExampleDocBuilderInfo(val id: String, private val myText: String?, private val myContext: PsiElement?) :
        BetterTSDocBuilderSimpleInfo() {

    fun generateDoc(): String {
        return "<div class='section'>Example: " + convertMarkdownToHtml(
                StringUtil.trim(StringUtil.notNullize(myText))) + "</div>" + exampleContent
    }

    private val exampleContent: String
        get() {
            val content = tryGetRichExampleContent()
            return if (content != null) {
                "$OPEN_PRE$content$CLOSE_PRE"
            } else {
                simpleExampleContent
            }
        }

    private fun tryGetRichExampleContent(): CharSequence? {
        if (myContext == null) {
            return null
        }

        val text = trimRawDescription
        return JSHtmlHighlightingUtil.tryGetHtmlHighlighting(myContext, text, null, 0, text.length)
    }

    private val simpleExampleContent: String
        get() {
            return "$OPEN_PRE${StringUtil.escapeXmlEntities(trimRawDescription)}$CLOSE_PRE"
        }

    private val trimRawDescription: String
        get() {
            return StringUtil.trimEnd(StringUtil.trimEnd(this.rawDescription, " "), NEW_LINE_PLACEHOLDER)
        }

}
