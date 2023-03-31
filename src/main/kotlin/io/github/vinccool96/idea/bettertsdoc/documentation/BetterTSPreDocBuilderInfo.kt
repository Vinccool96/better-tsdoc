package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.openapi.util.text.StringUtil

class BetterTSPreDocBuilderInfo(val id: String) : BetterTSDocBuilderSimpleInfo() {

    fun generateDoc(): String {
        return convertMarkdownToHtml("$FENCE$rawDescription$FENCE").replace(FENCE_REPLACEMENT, FENCE)
    }

    override fun appendNewLines(count: Int) {
        appendDescription(StringUtil.repeat(NEW_LINE_PLACEHOLDER, count))
    }

    override fun startsWithUnknownTag(text: CharSequence): Boolean {
        return false
    }

    companion object {

        const val CLOSE_PRE = "</pre>"

        const val OPEN_PRE = "<pre>"

        const val FENCE = "```"

        const val FENCE_REPLACEMENT = "!!!CODE_FENCE!!!"

        fun getPreBody(text: String, open: Int, openLength: Int, close: Int): String {
            if (open == -1 && close == -1) {
                return text
            }

            return text.substring(if (open == -1) 0 else open + openLength, if (close > 0) close else text.length)
        }

        fun getTextBeforePre(text: String, open: Int): String {
            return if (open < 0) {
                text
            } else if (open == 0) {
                ""
            } else {
                text.substring(0, open)
            }
        }

        fun getTextAfterPre(text: String, close: Int, closeLength: Int): String? {
            return if (close == -1) {
                null
            } else {
                val start = close + closeLength
                val end = text.length
                if (start >= end) null else text.substring(start, end)
            }
        }

    }

}