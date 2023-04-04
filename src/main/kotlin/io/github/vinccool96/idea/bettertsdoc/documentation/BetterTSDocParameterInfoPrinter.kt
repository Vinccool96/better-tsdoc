package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.psi.JSParameter
import com.intellij.lang.javascript.psi.JSParameterItem
import com.intellij.lang.javascript.psi.JSTypeUtils
import com.intellij.lang.javascript.psi.types.JSParameterTypeDecoratorImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.ObjectUtils

class BetterTSDocParameterInfoPrinter(private val context: PsiElement?, private val myParameterItem: JSParameterItem?,
        builder: BetterTSDocParameterInfoBuilder?) {

    val myBuilder: BetterTSDocParameterInfoBuilder = (builder ?: BetterTSDocParameterInfoBuilder().apply {
        optional = myParameterItem != null && myParameterItem.isOptional
        rest = myParameterItem != null && myParameterItem.typeDecorator.isRest
    }).apply {
        if (jsType == null) {
            jsType = if (myParameterItem == null) null else ObjectUtils.coalesce(myParameterItem.simpleType,
                    myParameterItem.inferredType)
        }
    }

    constructor(parameter: JSParameterItem?, builder: BetterTSDocParameterInfoBuilder?) : this(null, parameter, builder)

    constructor(parameter: JSParameterItem?) : this(null, parameter, null)

    val parameterItem: JSParameterItem
        get() {
            return myParameterItem ?: JSParameterTypeDecoratorImpl(null, myBuilder.jsType, myBuilder.optional,
                    myBuilder.rest, true)
        }

    fun appendDoc(actualName: String?, symbolInfo: BetterTSDocSymbolInfoBuilder, result: StringBuilder,
            provider: BetterTSDocumentationProvider, place: PsiElement?) {
        result.append("<div class='definition'><pre>")
        val options = StringBuilder()
        BetterTSDocSimpleInfoPrinter.addJSDocVisibilityAndAccess(myBuilder, options)
        if (options.isNotEmpty()) {
            result.append(options).append("<br>")
        }

        if (!StringUtil.isEmpty(symbolInfo.namespace)) {
            result.append(symbolInfo.namespace).append('.')
        }

        val builder = provider.quickNavigateBuilder
        if (myParameterItem is JSParameter) {
            val toUse = place ?: myParameterItem
            val navInfoResult = builder.getQuickNavigateInfoForNavigationElement(myParameterItem, toUse, true)
            result.append(navInfoResult)
        } else {
            result.append(actualName)
            if (myBuilder.hasType) {
                result.append(": ")
                result.append(myBuilder.getTypeString(context))
            }
        }

        result.append("</pre></div>")
        val contentStart = result.length
        result.append("<div class='content'>")
        result.append(this.descriptionMergedWithConfigOptions)
        if (result.length == contentStart + "<div class='content'>".length) {
            result.setLength(contentStart)
        } else {
            result.append("</div>")
        }
    }

    private val descriptionMergedWithConfigOptions: String
        get() {
            return if (myBuilder.optionsMap == null) {
                myBuilder.finalDescription
            } else {
                val optionsBuilder = StringBuilder()
                optionsBuilder.append("<table class='sections'>")
                optionsBuilder.append("<tr><td valign='top' class='section'><p>")
                optionsBuilder.append("Config options").append(":")
                optionsBuilder.append("</td><td valign='top'>")
                val var2 = myBuilder.optionsMap!!.entries.iterator()
                while (var2.hasNext()) {
                    val (key, value) = var2.next()
                    optionsBuilder.append("<p>")
                    BetterTSDocParameterInfoPrinter(null, value).appendOptionDescription(key, optionsBuilder)
                }
                optionsBuilder.append("</table>")
                val description: String = myBuilder.finalDescription
                val i = description.indexOf(BetterTSDocSeeAlsoPrinter.SEE_ALSO_DOC_TOKEN)
                if (i != -1) {
                    StringBuilder(description).insert(i, optionsBuilder).toString()
                } else {
                    description + optionsBuilder
                }
            }
        }

    private fun appendParameterInfoInSignature(actualName: String, result: StringBuilder) {
        val qName = if (!StringUtil.isEmpty(myBuilder.namespace)) myBuilder.namespace + "." + actualName else actualName
        result.append(qName)
        if (myBuilder.optional) {
            result.append("?")
        }

        if (myBuilder.hasType) {
            result.append(": ")
            if (myBuilder.jsType != null) {
                myBuilder.jsType = myBuilder.jsType!!.substitute()
            }
            result.append(myBuilder.getTypeString(context))
            if (myBuilder.rest && !JSTypeUtils.isArrayLikeType(myBuilder.jsType)) {
                result.append("[]")
            }
        }

        if (myBuilder.initialValue != null) {
            result.append(" = ").append(myBuilder.initialValue)
        }
    }

    fun hasTypeElement(): Boolean {
        return myParameterItem is JSParameter && myParameterItem.typeElement != null
    }

    fun appendOptionDescription(name: String, builder: StringBuilder) {
        appendParameterInfoInSignature(name, builder)
        builder.append(" &ndash; ")
        builder.append(myBuilder.finalDescription)
    }

}