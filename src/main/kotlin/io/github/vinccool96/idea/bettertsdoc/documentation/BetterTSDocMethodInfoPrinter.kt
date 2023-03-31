package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.documentation.JSDocumentationProvider
import com.intellij.lang.javascript.documentation.JSDocumentationProvider.LinkedDocCollector
import com.intellij.lang.javascript.psi.JSFunctionItem
import com.intellij.lang.javascript.psi.impl.JSFunctionImpl
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import java.util.*

class BetterTSDocMethodInfoPrinter(builder: BetterTSDocMethodInfoBuilder, private val myFunctionItem: JSFunctionItem,
        element: PsiElement, contextElement: PsiElement?) :
        BetterTSDocSymbolInfoPrinter<BetterTSDocMethodInfoBuilder>(builder, element, contextElement, false) {

    init {
        if (builder.returnInfo.jsType == null) {
            builder.returnInfo.jsType = JSFunctionImpl.getReturnTypeInContext(functionItem, null)
        }
    }

    override fun appendDoc(builder: StringBuilder, provider: BetterTSDocumentationProvider) {
        super.appendDoc(builder, provider)
        if (DialectDetector.isTypeScript(myFunctionItem)) {
            val signatures = provider.getOverloads(myFunctionItem)
            if (signatures.size > 1) {
                val results = ArrayList<PsiElement?>()
                results.add(myFunctionItem)
                val var5 = signatures.iterator()
                while (var5.hasNext()) {
                    val signature = var5.next() as JSFunctionItem
                    if (signature !== myFunctionItem) {
                        results.add(signature)
                        if (results.size > 5) {
                            break
                        }
                    }
                }
                Objects.requireNonNull(provider)
                val collector = LinkedDocCollector(provider, contextElement, Pair(myFunctionItem, ""), results)
                builder.append("<div class='content'>")
                builder.append("<p>")
                builder.append(collector.getLinks())
                if (results.size != signatures.size) {
                    val count = signatures.size - results.size
                    val overloadsText = JavaScriptBundle.message(
                            if (count == 1) "js.documentation.more.overload" else "js.documentation.more.overloads",
                            *arrayOf<Any>(count))
                    builder.append(", <br> ").append(overloadsText)
                }
                builder.append("</div>")
            }
        }
    }

    companion object {

        const val OVERLOADS_LIMIT = 5

    }

}