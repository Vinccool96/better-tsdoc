package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.psi.PsiElement

open class BetterTSDocSymbolInfoPrinter<T : BetterTSDocSymbolInfoBuilder>(builder: T, element: PsiElement,
        contextElement: PsiElement?, canBeNamed: Boolean) : BetterTSDocSimpleInfoPrinter<T>(builder,
        element, contextElement, canBeNamed) {

    private val mySeeAlsoPrinter = BetterTSDocSeeAlsoPrinter(builder.mySeeAlsoTexts, myElement)

    override fun appendInnerSections(result: StringBuilder, provider: BetterTSDocumentationProvider,
            hasDefinition: Boolean) {
        appendBodyDoc(result, hasDefinition)
        addSections(myBuilder.mySimpleTags, result)
        addSections(myBuilder.myUnknownTags, result)
        mySeeAlsoPrinter.appendDoc(result, provider)
        if (hasDefinition) {
            appendLocation(result)
        }
    }

    override fun appendPropertiesAndDefaultValue(result: StringBuilder) {
        if (myBuilder.defaultValue != null) {
            result.append(myBuilder.defaultValue)
        }

        if (myBuilder.myProperties != null) {
            startNamedSection("Properties:", result)
            result.append("<td valign='top'>")
            val var2 = myBuilder.myProperties!!.entries.iterator()
            while (var2.hasNext()) {
                val (key, value) = var2.next()
                result.append("<p>")
                BetterTSDocParameterInfoPrinter(contextElement, null, value).appendOptionDescription(key.name, result)
            }
            result.append("</td>")
        }
    }

}