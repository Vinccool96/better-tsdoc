package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.JSDocTokenTypes
import com.intellij.lang.javascript.JSTargetElementEvaluator
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.documentation.JSDocumentationBuilder
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptImportStatement
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair
import com.intellij.lang.javascript.psi.impl.JSReferenceExpressionImpl
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.jsdoc.JSDocTagValue
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocReference
import com.intellij.lang.javascript.psi.resolve.JSClassResolver
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.stubs.TypeScriptMergedTypeImplicitElement
import com.intellij.lang.javascript.psi.stubs.TypeScriptProxyImplicitElement
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.lang.javascript.psi.types.JSNamedType
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.lang.typescript.TypeScriptGoToDeclarationHandler
import com.intellij.lang.typescript.documentation.TypeScriptDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.css.impl.util.CssDocumentationProvider
import java.util.function.Consumer

class BetterTSDocDocumentationProvider : TypeScriptDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val res = super.getQuickNavigateInfo(element, originalElement)
        return res
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): MutableList<String>? {
        val res = super.getUrlFor(element, originalElement)
        return res
    }

    override fun generateDoc(psiElement: PsiElement?, originalElement: PsiElement?): String? {
        val res = super.generateDoc(psiElement, originalElement)
        if (psiElement == null || (psiElement is TypeScriptMergedTypeImplicitElement && originalElement != null && originalElement.context is JSExpression)) {
            return null
        }
        if (psiElement is JSImplicitElement && psiElement !is TypeScriptProxyImplicitElement) {
            return this.getDocumentationForImplicitElement(psiElement)
        }

        var _element = this.getEffectiveElement(psiElement, originalElement) ?: return null
        if (_element.parent != null && _element.parent is PsiComment) {
            _element = _element.parent
        }

        if (_element is PsiComment) {
            return doGetCommentTextFromComment(_element, originalElement)
        }

        val results = tryMultiResolveElement(_element)
        if (results.size > 1) {
            return this.processMultiResolvedElements(_element, originalElement, results)
        }

        var element = findElementForWhichPreviousCommentWillBeSearched(_element, originalElement)
        var docComment: PsiComment?
        if (element != null) {
            docComment = JSDocumentationUtils.findDocComment(element,
                    if (_element is JSAttributeNameValuePair) originalElement else null, null)
            if (docComment == null) {
                val meaningfulElement = getPossibleMeaningfulElement(element)
                docComment = JSDocumentationUtils.findDocComment(meaningfulElement)
                if (docComment != null) {
                    element = meaningfulElement
                }

                if (_element is JSPsiNamedElementBase) {
                    val name = _element.name
                    val typeofComment = JSDocumentationUtils.findScopeComment(_element)
                    if (name != null && typeofComment is JSDocComment) {
                        val result = Ref.create<String?>()
                        JSClassResolver.processImplicitElements(name, { e ->
                            result.set(this.getDocumentationForImplicitElement(e))
                            false
                        }, typeofComment)
                        if (!result.isNull) {
                            return result.get()
                        }
                    }
                }
            }

            element = findTargetElement(_element, element)
            if (docComment != null) {
                docComment = findFirstDocComment(docComment)
                val elementType = JSTypeUtils.getTypeOfElement(element)
                val typeofResolvedElement = getTypeofResolvedElement(docComment, elementType)
                if (typeofResolvedElement != null) {
                    val typeofComment = JSDocumentationUtils.findDocComment(typeofResolvedElement)
                    if (typeofComment != null) {
                        docComment = typeofComment
                        element = typeofResolvedElement
                    }
                }

                val isTypeOnlyComment = elementType != null && elementType.isEquivalentTo(
                        JSDocumentationUtils.tryCreateTypeFromComment(docComment, true, true, false), null)
                val builder = this.createDocBuilder(element, originalElement)
                if (!isTypeOnlyComment) {
                    JSDocumentationUtils.processDocumentationTextFromComment(docComment, docComment.node, builder)
                }

                builder.fillEvaluatedType()
                val parameterDoc = if (element is JSParameter) element else null
                return if (parameterDoc != null) builder.getParameterDoc(parameterDoc, null, this,
                        originalElement) else builder.getDoc()
            }
        }

        val possibleCssName = findPossibleCssName(_element)
        if (possibleCssName != null) {
            val cssDoc = CssDocumentationProvider.generateDoc(possibleCssName, _element, null)
            if (cssDoc != null) {
                return cssDoc
            }
        }

        if (element == null) {
            return null
        }

        val builder = this.createDocBuilder(element, originalElement)
        builder.fillEvaluatedType()
        return if (builder.showDoc()) builder.getDoc() else null
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val res = super.generateHoverDoc(element, originalElement)
        return res
    }

    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        val res = super.generateRenderedDoc(comment)
        return res
    }

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        super.collectDocComments(file, sink)
        val a = 1 + 2
    }

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
        val res = super.findDocComment(file, range)
        return res
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager?, `object`: Any?,
            element: PsiElement?): PsiElement? {
        val res = super.getDocumentationElementForLookupItem(psiManager, `object`, element)
        return res
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String,
            context: PsiElement?): PsiElement? {
        val res = super.getDocumentationElementForLink(psiManager, link, context)
        return res
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?,
            targetOffset: Int): PsiElement? {
        val res = super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
        return res
    }

    override fun findExistingDocComment(contextElement: PsiComment?): PsiComment? {
        val res = super.findExistingDocComment(contextElement)
        return res
    }

    override fun parseContext(startPoint: PsiElement): Pair<PsiElement, PsiComment>? {
        val res = super.parseContext(startPoint)
        return res
    }

    override fun generateDocumentationContentStub(contextComment: PsiComment?): String? {
        val res = super.generateDocumentationContentStub(contextComment)
        return res
    }

    override fun fetchExternalDocumentation(project: Project?, element: PsiElement?, docUrls: MutableList<String>?,
            onHover: Boolean): String? {
        val res = super.fetchExternalDocumentation(project, element, docUrls, onHover)
        return res
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        val res = super.canPromptToConfigureDocumentation(element)
        return res
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {
        super.promptToConfigureDocumentation(element)
        val a = 1 + 2
    }

    override fun processMultiResolvedElements(_element: PsiElement, originalElement: PsiElement?,
            results: Array<out ResolveResult>): String? {
        val res = super.processMultiResolvedElements(_element, originalElement, results)
        return res
    }

    fun createDocBuilder(element: PsiElement, contextElement: PsiElement?): BetterTSDocDocumentationBuilder {
        return BetterTSDocDocumentationBuilder(element, contextElement, this)
    }

    private fun getPossibleMeaningfulElement(element: PsiElement): PsiElement {
        if (element is JSFunction && element.isConstructor) {
            val containingClass = JSUtils.getMemberContainingClass(element)
            if (containingClass != null) {
                return containingClass
            }
        }

        return if (DumbService.isDumb(element.project)) element else JSStubBasedPsiTreeUtil.calculateMeaningfulElement(
                element)
    }

    private fun getDocumentationForImplicitElement(element: JSImplicitElement): String? {
        val builder = this.createDocBuilder(element, element)
        val comment = JSDocumentationUtils.findCommentForImplicitElement(element)
        if (comment != null) {
            JSDocumentationUtils.processDocumentationTextFromComment(comment, comment.node, builder)
        }

        builder.fillEvaluatedType()
        return builder.getDoc()
    }

    private fun findTargetElement(psiElement: PsiElement, element: PsiElement): PsiElement {
        var realElement = element
        if (realElement is JSExpression) {
            val parentElement = realElement.parent
            if (parentElement is JSInitializerOwner && parentElement.initializer === realElement) {
                realElement = parentElement
            }
        }

        if (psiElement is JSDefinitionExpression) {
            val parentElement = psiElement.parent
            if (parentElement is JSAssignmentExpression) {
                val rOperand = parentElement.rOperand
                realElement = if (rOperand is JSFunctionExpression) {
                    rOperand
                } else {
                    psiElement
                }
            }
        } else if (psiElement is JSFunctionExpression) {
            realElement = psiElement
        } else if (psiElement is JSProperty) {
            val expression = psiElement.value
            if (expression is JSFunction) {
                realElement = expression
            }
        } else if (psiElement is JSVariable) {
            if (psiElement is JSParameter) {
                val function = psiElement.declaringFunction
                if (function != null) {
                    return function
                }
            }

            realElement = psiElement
        } else if (psiElement is JSAttributeNameValuePair) {
            return psiElement
        } else if (psiElement is JSFunction) {
            val isGet = psiElement.isGetProperty
            val isSet = psiElement.isSetProperty
            if (element is JSFunction && (isGet || isSet)) {
                val isGetEl = element.isGetProperty
                val isSetEl = element.isSetProperty
                if (isGet == isSetEl && isSet == isGetEl) {
                    return psiElement
                }
            }
        }

        return realElement
    }

    private fun findFirstDocComment(docComment: PsiComment): PsiComment {
        var firstDocComment = docComment
        if (firstDocComment.node.elementType == JSTokenTypes.END_OF_LINE_COMMENT) {
            while (true) {
                var prev: PsiElement? = firstDocComment.prevSibling
                if (prev is PsiWhiteSpace) {
                    prev = prev.prevSibling
                }

                if (prev == null || prev.node.elementType != JSTokenTypes.END_OF_LINE_COMMENT) {
                    break
                }

                firstDocComment = prev as PsiComment
            }
        }

        return firstDocComment
    }

    private fun getTypeofResolvedElement(docComment: PsiComment, type: JSType?): PsiElement? {
        if (type is JSNamedType && type.jsContext == JSContext.STATIC) {
            if (docComment is JSDocComment && docComment.tags.size == 1 && docComment.node.findChildByType(
                        JSDocTokenTypes.DOC_COMMENT_DATA) == null
            ) {
                val value = docComment.tags[0].value ?: return null
                val reference = value.reference
                return if (reference is JSDocReference?) reference?.resolve() else null
            }
        }

        return null
    }

    private fun findPossibleCssName(element: PsiElement): String? {
        if (element is JSDefinitionExpression) {
            val expression = element.expression
            if (expression is JSReferenceExpression) {
                val text = expression.referenceName
                if (text == null || (text.isNotEmpty() && text[0].isUpperCase())) {
                    return null
                }

                val buf = StringBuilder(text.length)
                for (ch in text) {
                    if (ch.isUpperCase()) {
                        buf.append("-${ch.lowercaseChar()}")
                    } else {
                        buf.append(ch)
                    }
                }

                return buf.toString()
            }
        }

        return null
    }

    companion object {

        fun tryMultiResolveElement(element: PsiElement?): Array<ResolveResult> {
            when (element) {
                is TypeScriptImportStatement -> {
                    val elements = element.findReferencedElements()
                    return elements.map { el -> JSResolveResult(el) }.toTypedArray()
                }

                is JSReferenceExpressionImpl -> {
                    val elements =
                        TypeScriptGoToDeclarationHandler.getResultsFromService(element.project, element, null)
                    if (!elements.isNullOrEmpty()) {
                        val filtered = elements.mapNotNull(
                                TypeScriptDocumentationProvider::adjustResultFromServiceForDocumentation)

                        if (filtered.isNotEmpty()) {
                            return JSResolveResult.toResolveResults(filtered)
                        }
                    }

                    return JSTargetElementEvaluator.resolveReferenceExpressionWithAllResolveResults(element)
                }

                is PsiPolyVariantReference -> {
                    return element.multiResolve(false)
                }

                is JSDocTagValue -> {
                    val references = element.getReferences()
                    if (references.size == 1) {
                        val reference = references[0]
                        if (reference is JSDocReference) {
                            return reference.multiResolve(false)
                        }
                    }
                }
            }

            return ResolveResult.EMPTY_ARRAY
        }

    }

}