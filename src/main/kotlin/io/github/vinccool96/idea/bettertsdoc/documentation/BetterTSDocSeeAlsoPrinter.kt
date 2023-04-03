package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement

class BetterTSDocSeeAlsoPrinter(private val myTexts: List<String>, private val myElement: PsiElement) {

    fun appendDoc(result: StringBuilder, provider: BetterTSDocumentationProvider) {
        if (myTexts.isNotEmpty()) {
            result.append(SEE_ALSO_DOC_TOKEN)
            var isFirst = true
            var text: String?
            val var4 = myTexts.iterator()
            while (var4.hasNext()) {
                text = var4.next()
                if (isFirst) {
                    isFirst = false
                } else {
                    result.append(", ")
                }
                appendSection(text, result, provider)
            }
            result.append("</td>")
        }
    }

    private fun appendSection(remainingLineContent: String, result: StringBuilder,
            provider: BetterTSDocumentationProvider) {
        val realRemainingLineContent = remainingLineContent.trim { it <= ' ' }
        if (realRemainingLineContent.startsWith("\"") && realRemainingLineContent.endsWith("\"")) {
            result.append(StringUtil.unquoteString(realRemainingLineContent))
        } else {
            val i = realRemainingLineContent.indexOf(32.toChar())
            val linkPart: String
            val displayText: String
            if (i == -1) {
                displayText = realRemainingLineContent
                linkPart = realRemainingLineContent
            } else {
                linkPart = realRemainingLineContent.substring(0, i)
                displayText = realRemainingLineContent.substring(i + 1)
            }
            appendLink(result, realRemainingLineContent, provider, linkPart, displayText)
        }
    }

    private fun appendLink(result: StringBuilder, remainingLineContent: String, provider: BetterTSDocumentationProvider,
            linkPart: String, displayText: String) {
        if (BrowserUtil.isAbsoluteURL(linkPart)) {
            result.append("<a href=\"").append(linkPart).append("\">").append(displayText).append("</a>")
        } else if (looksLikeWebURL(linkPart)) {
            result.append("<a href=\"http://").append(linkPart).append("\">").append(displayText).append("</a>")
        } else if (!provider.appendSeeAlsoLink(linkPart, displayText, remainingLineContent, myElement, result)) {
            result.append(remainingLineContent)
        }
    }

    companion object {

        const val SEE_ALSO_DOC_TOKEN = "<tr><td valign='top' class='section'><p>See also:</td><td valign='top'>"

        private fun looksLikeWebURL(linkPart: String): Boolean {
            return linkPart.startsWith("www.") && linkPart.matches("[a-z0-9./]+".toRegex())
        }

    }

}