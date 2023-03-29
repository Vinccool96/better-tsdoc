package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptImportStatement
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.lang.javascript.psi.stubs.TypeScriptMergedTypeImplicitElement
import com.intellij.lang.typescript.documentation.TypeScriptDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import java.util.function.Consumer

class BetterTSDocsDocumentationProvider : TypeScriptDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val res = super.getQuickNavigateInfo(element, originalElement)
        return res
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): MutableList<String>? {
        val res = super.getUrlFor(element, originalElement)
        return res
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val res = super.generateDoc(element, originalElement)
        if (element == null || (element is TypeScriptMergedTypeImplicitElement && originalElement != null && originalElement.context is JSExpression)) {
            return null
        }
        var e = this.getEffectiveElement(element, originalElement) ?: return null
        if (e.parent != null && e.parent is PsiComment) {
            e = e.parent
        }

        if (e is PsiComment) {
            return doGetCommentTextFromComment(e, originalElement)
        }

        val a = 1 + 2
        return res
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

    companion object {

        fun tryMultiResolveElement(_element: PsiElement?): Array<ResolveResult> {
            if (_element is TypeScriptImportStatement) {
                val elements = _element.findReferencedElements()
                return elements.map { el -> JSResolveResult(el) }.toTypedArray()
            }
        }

    }

}