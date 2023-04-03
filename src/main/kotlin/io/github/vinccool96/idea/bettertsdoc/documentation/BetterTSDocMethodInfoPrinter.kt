package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.JavaScriptBundle
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
            builder.returnInfo.jsType = JSFunctionImpl.getReturnTypeInContext(myFunctionItem, null)
        }
    }

    override fun appendDoc(result: StringBuilder, provider: BetterTSDocumentationProvider) {
        super.appendDoc(result, provider)
        if (DialectDetector.isTypeScript(myFunctionItem)) {
            val signatures = provider.getOverloads(myFunctionItem)
            if (signatures.size > 1) {
                val results = ArrayList<PsiElement>()
                results.add(myFunctionItem)
                val var5 = signatures.iterator()
                while (var5.hasNext()) {
                    val signature = var5.next()
                    if (signature !== myFunctionItem) {
                        results.add(signature)
                        if (results.size > OVERLOADS_LIMIT) {
                            break
                        }
                    }
                }
                Objects.requireNonNull(provider)
                val collector = provider.LinkedDocCollector(contextElement!!, Pair(myFunctionItem, ""), results)
                result.append("<div class='content'>")
                result.append("<p>")
                result.append(collector.getLinks())
                if (results.size != signatures.size) {
                    val count = signatures.size - results.size
                    val overloadsText = JavaScriptBundle.message(
                            if (count == 1) "js.documentation.more.overload" else "js.documentation.more.overloads",
                            *arrayOf<Any>(count))
                    result.append(", <br> ").append(overloadsText)
                }
                result.append("</div>")
            }
        }
    }

    companion object {

        const val OVERLOADS_LIMIT = 5

    }

}