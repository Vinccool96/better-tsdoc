package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.documentation.*
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor.MetaDocType
import com.intellij.lang.javascript.ecmascript6.TypeScriptSignatureChooser
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSFunctionItem
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import java.util.*
import java.util.regex.Pattern

class BetterTSDocumentationBuilder(private val myElement: PsiElement, private val myContextElement: PsiElement?,
        private val myProvider: JSDocumentationProvider?) : JSDocumentationProcessor {

    private val myTargetInfo = JSDocMethodInfoBuilder()

    private val initializer =
        if (this.myElement is JSVariable) JSPsiImplUtils.getAssignedExpression(this.myElement) else null

    private var currentParameterInfo: JSDocParameterInfoBuilder? = null

    private var myInfoForNewLines: JSDocBuilderSimpleInfo = this.myTargetInfo

    private val function =
        findFunction(if (this.initializer is JSFunctionExpression) this.initializer else this.myElement,
                this.myContextElement)

    private var myNewLinesPendingCount = 0

    var myPreInfo: JSPreDocBuilderInfo? = null

    private val seenPre: String? = null

    private val seenInheritDoc = false

    private val NULL_PARAMETER_INFO = JSDocParameterInfoBuilder()

    override fun needsPlainCommentData(): Boolean {
        return true
    }

    override fun onCommentLine(line: String): Boolean {
        return true
    }

    override fun onPatternMatch(p0: MetaDocType, p1: String?, p2: String?, p3: String?, p4: String,
            p5: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun postProcess() {
        TODO("Not yet implemented")
    }

    companion object {

        private const val EVENTS = "Events:"

        private const val IMPLEMENTS = "Implements:"

        private const val EXTENDS = "Extends:"

        private val SIMPLE_TAGS: MutableMap<MetaDocType, String> = EnumMap(MetaDocType::class.java)

        private val ourBrTagPattern = Pattern.compile("<br\\s?/?>", 2)

        init {
            SIMPLE_TAGS[MetaDocType.NOTE] = "Note:"
            SIMPLE_TAGS[MetaDocType.AUTHOR] = "Author:"
            SIMPLE_TAGS[MetaDocType.FILE_OVERVIEW] = "File overview:"
            SIMPLE_TAGS[MetaDocType.SINCE] = "Since:"
            SIMPLE_TAGS[MetaDocType.VERSION] = "Version:"
            SIMPLE_TAGS[MetaDocType.SUMMARY] = "Summary:"
            SIMPLE_TAGS[MetaDocType.TODO] = "To do:"
            SIMPLE_TAGS[MetaDocType.NG_MODULE] = "NgModule:"
        }

        private fun findFunction(element: PsiElement, contextElement: PsiElement?): JSFunctionItem? {
            var result = TypeScriptSignatureChooser.resolveAnyFunction(element, contextElement)
            if (result == null) {
                val comment = JSDocumentationUtils.findOwnDocComment(element)
                if (comment is JSDocCommentImpl) {
                    var name = comment.explicitName
                    if (name == null && element is PsiNamedElement) {
                        name = element.name
                    }

                    name = name ?: ""

                    val implicitElement = comment.buildImplicitElement(name)
                    if (implicitElement is JSFunctionItem) {
                        result = implicitElement
                    }
                }
            }

            return result
        }

    }

}