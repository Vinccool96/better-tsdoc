package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.openapi.util.text.StringUtil

class BetterTSDeprecatedDocBuilderInfo(val id: String, private val myText: String?) : BetterTSDocBuilderSimpleInfo() {

    fun generateDoc(): String {
        return "<br/><span class='grayed'>Deprecated: " + convertMarkdownToHtml(StringUtil.trim(StringUtil.notNullize(myText))) + deprecatedContent + "</span><br/>"
    }

    private val deprecatedContent: String
        get() {
            return StringUtil.escapeXmlEntities(trimRawDescription)
        }

    private val trimRawDescription: String
        get() {
            return StringUtil.trimEnd(StringUtil.trimEnd(rawDescription, " "), NEW_LINE_PLACEHOLDER)
        }

}