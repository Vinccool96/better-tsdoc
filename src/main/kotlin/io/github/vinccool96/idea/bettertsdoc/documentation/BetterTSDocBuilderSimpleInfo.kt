package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.documentation.JSHtmlHighlightingUtil
import com.intellij.lang.javascript.presentable.JSFormatUtil
import com.intellij.lang.javascript.psi.JSType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import com.intellij.webSymbols.utils.HtmlMarkdownUtils
import kotlin.math.min

open class BetterTSDocBuilderSimpleInfo {

    private val myDescription = StringBuilder()

    private val myExamples: MutableList<BetterTSExampleDocBuilderInfo> = SmartList()

    private val myDeprecated: MutableList<BetterTSDeprecatedDocBuilderInfo> = SmartList()

    private val myPre: MutableList<BetterTSPreDocBuilderInfo> = SmartList()

    val myUnknownTags: MutableMap<String, String> = HashMap()

    var jsType: JSType? = null

    var modifiers: String? = null

    var finalAccess: String? = null

    var namespace: String? = null

    var hasFiredEvents = false

    fun resetDescription() {
        myDescription.setLength(0)
    }

    fun addUnknownTag(key: String, text: String?) {
        this.myUnknownTags[key] = StringUtil.notNullize(text)
    }

    val hasDescription: Boolean
        get() {
            return myDescription.isNotEmpty()
        }

    fun appendBlockDescription(text: CharSequence?) {
        if (hasDescription) {
            myDescription.append("{{Par%#%L}}")
        }
        appendDescription(text)
    }

    open fun appendNewLines(count: Int) {
        myDescription.append("\n".repeat(count))
    }

    val finalDescription: String
        get() {
            val result = myDescription.toString()
            if (StringUtil.isEmptyOrSpaces(result)) {
                return result
            }

            var html = convertMarkdownToHtml(result)

            for (info in myDeprecated) {
                html = html.replace(info.id, info.generateDoc())
            }

            for (info in myExamples) {
                html = html.replace(info.id, info.generateDoc())
            }

            for (info in myPre) {
                html = html.replace(info.id, info.generateDoc())
                val preDoc = info.generateDoc()
                html = html.replace(TAG_FOR_NEW_LINES + info.id, preDoc).replace(info.id, preDoc)
            }

            html = StringUtil.replace(html, PARAGRAPH_PLACEHOLDER, TAG_FOR_NEW_LINES)

            return html
        }

    val rawDescription: String
        get() {
            return this.myDescription.toString()
        }

    fun mergeRawDescriptionAndPlaceHolders(info: BetterTSDocBuilderSimpleInfo) {
        appendDescription(info.rawDescription)
        myDeprecated.addAll(info.myDeprecated)
        myExamples.addAll(info.myExamples)
        myPre.addAll(info.myPre)
    }

    fun appendDescription(rawText: CharSequence?) {
        if (rawText != null) {
            if (this.startsWithUnknownTag(rawText)) {
                var text = rawText.toString()
                val startIndex = text.indexOf("@")
                if (startIndex >= 0) {
                    text = text.substring(startIndex)
                    val endTag = text.indexOf(" ")
                    val tagName: String
                    if (endTag != -1) {
                        tagName = text.substring(0, endTag).trim { it <= ' ' }
                        addUnknownTag("$tagName: ", text.substring(endTag).trim { it <= ' ' })
                    } else {
                        tagName = text.trim { it <= ' ' }
                        addUnknownTag(tagName, "")
                    }
                }
            } else {
                myDescription.append(rawText)
            }
        }
    }

    protected open fun startsWithUnknownTag(text: CharSequence): Boolean {
        for (i in text.indices) {
            if (text[i] == '@') {
                return i + 1 < text.length && !StringUtil.equals("param ",
                        text.subSequence(i + 1, min(i + 7, text.length)))
            }
            if (!Character.isWhitespace(text[i])) {
                break
            }
        }

        return false
    }

    fun startDeprecated(text: String?): BetterTSDeprecatedDocBuilderInfo {
        val nextId = generateId("Deprecated", myDeprecated)
        val info = BetterTSDeprecatedDocBuilderInfo(nextId, text)
        myDeprecated.add(info)
        appendDescription(nextId)

        return info
    }

    fun startExample(text: String?, context: PsiElement?): BetterTSExampleDocBuilderInfo {
        val nextId = generateId("Example", myExamples)
        val info = BetterTSExampleDocBuilderInfo(nextId, text, context)
        myExamples.add(info)
        appendDescription(nextId)

        return info
    }

    fun startPre(): BetterTSPreDocBuilderInfo {
        val nextId = generateId("Pre", myPre)
        val info = BetterTSPreDocBuilderInfo(nextId)
        myPre.add(info)
        appendDescription("$NEW_LINE_PLACEHOLDER$nextId")

        return info
    }

    private fun generateId(prefix: String, map: List<BetterTSDocBuilderSimpleInfo>): String {
        return "{$prefix{${map.size + 1}#${this.hashCode()}}}"
    }

    fun mergeDescriptionWith(toMerge: BetterTSDocBuilderSimpleInfo) {
        if (toMerge.hasDescription) {
            if (this.hasDescription) {
                val addTwoBreaks: Boolean = !this.isEndingWithNewLine
                appendNewLines(if (addTwoBreaks) 2 else 1)
            }
            appendDescription(toMerge.myDescription)
            myPre.addAll(toMerge.myPre)
        }
    }

    private val isEndingWithNewLine: Boolean
        get() {
            return myDescription.lastIndexOf("\n") == myDescription.length - "\n".length
        }

    fun getTypeString(context: PsiElement?): CharSequence {
        jsType = if (jsType != null && context != null && !DumbService.isDumb(
                        context.project)) jsType!!.substitute() else jsType
        return BetterTSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(jsType, context, hasFiredEvents)
    }

    val hasType: Boolean
        get() {
            return JSFormatUtil.isPossiblyPresentableType(jsType, null as PsiElement?, true) && jsType!!.isSourceStrict
        }

    companion object {

        const val NEW_LINE_PLACEHOLDER = "\n"

        const val NEW_LINE_MARKDOWN_PLACEHOLDER = "\n\n"

        private const val TAG_FOR_NEW_LINES = "<p>"

        private const val NEW_LINE_PLACEHOLDER_REPLACEMENT = "\n<p>"

        private const val PARAGRAPH_PLACEHOLDER = "{{Par%#%L}}"

        protected fun convertNewLinePlaceholdersToTags(generatedDoc: String): String {
            return StringUtil.replace(generatedDoc, NEW_LINE_PLACEHOLDER, NEW_LINE_PLACEHOLDER_REPLACEMENT)
        }

        @JvmStatic
        protected fun convertMarkdownToHtml(html: String): String {
            var newHtml = HtmlMarkdownUtils.toHtml(html, false)
                    ?: return HtmlMarkdownUtils.replaceProhibitedTags(convertNewLinePlaceholdersToTags(html))

            if (newHtml.startsWith(TAG_FOR_NEW_LINES)) {
                newHtml = StringUtil.trimEnd(StringUtil.trimStart(newHtml, TAG_FOR_NEW_LINES), TAG_FOR_NEW_LINES)
            }

            newHtml = StringUtil.replace(newHtml, TAG_FOR_NEW_LINES, "")
            newHtml = StringUtil.replace(newHtml, "<br  />", TAG_FOR_NEW_LINES)

            return newHtml
        }

    }

}