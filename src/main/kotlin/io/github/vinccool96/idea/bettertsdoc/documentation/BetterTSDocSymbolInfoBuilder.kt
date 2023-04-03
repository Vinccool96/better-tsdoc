package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.psi.JSQualifiedName
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList

open class BetterTSDocSymbolInfoBuilder : BetterTSDocBuilderSimpleInfo() {

    val mySimpleTags: MutableMap<String, String> = HashMap()

    val mySeeAlsoTexts: MutableList<String> = SmartList()

    var myProperties: MutableMap<JSQualifiedName, BetterTSDocParameterInfoBuilder>? = null

    var defaultValue: String? = null

    fun addSimpleTag(key: String, text: String?) {
        mySimpleTags[key] = StringUtil.notNullize(text)
    }

    fun addEventOrImplementsTag(key: String, name: String?, text: String?) {
        val value = StringUtil.notNullize(mySimpleTags[key]) + getValue(name, text)
        mySimpleTags[key] = value
    }

    fun addExtendsTag(key: String, name: String?, text: String?) {
        val value = getValue(name, text)
        mySimpleTags[key] = value
    }

    fun addSeeAlsoText(text: String?) {
        if (!StringUtil.isEmpty(text)) {
            mySeeAlsoTexts.add(text!!)
        }
    }

    fun setDefaultValue(content: String, color: Boolean, originalText: String?) {
        defaultValue = BetterTSDocSimpleInfoPrinter.buildCurrentOrDefaultValue(content, color, true, originalText, true)
    }

    companion object {

        private fun getValue(name: String?, text: String?): String {
            val link = if (!StringUtil.isEmpty(name) && !StringUtil.isEmpty(text)) " &ndash; " else ""
            return "<p>${StringUtil.notNullize(name)}$link${StringUtil.notNullize(text)}"
        }

    }

}