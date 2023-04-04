@file:Suppress("DEPRECATION")

package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.QuickDocUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.documentation.CodeDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportExportSpecifierAlias
import com.intellij.lang.ecmascript6.psi.ES6ImportedExportedDefaultBinding
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.actions.JSShowTypeInfoAction
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.ecmascript6.TypeScriptSignatureChooser
import com.intellij.lang.javascript.library.JSLibraryManager
import com.intellij.lang.javascript.presentable.JSFormatUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.JSType.TypeTextFormat
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction
import com.intellij.lang.javascript.psi.ecma6.TypeScriptImportStatement
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeMember
import com.intellij.lang.javascript.psi.ecmal4.*
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.impl.JSReferenceExpressionImpl
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.jsdoc.JSDocTagValue
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocReference
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocReferenceSet
import com.intellij.lang.javascript.psi.resolve.JSClassResolver
import com.intellij.lang.javascript.psi.resolve.JSReferenceExpressionResolver
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.stubs.TypeScriptMergedTypeImplicitElement
import com.intellij.lang.javascript.psi.stubs.TypeScriptProxyImplicitElement
import com.intellij.lang.javascript.psi.types.JSAnyType
import com.intellij.lang.javascript.psi.types.JSCompositeTypeImpl
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.lang.javascript.psi.types.JSNamedType
import com.intellij.lang.javascript.psi.types.primitives.JSVoidType
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.lang.javascript.types.TypeFromUsageDetector
import com.intellij.lang.typescript.TypeScriptGoToDeclarationHandler
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.lang.typescript.psi.TypeScriptPsiUtil
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.psi.*
import com.intellij.psi.css.impl.util.CssDocumentationProvider
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.webcore.libraries.ScriptingLibraryManager
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

@Suppress("UNUSED_PARAMETER", "UnstableApiUsage", "unused")
open class BetterTSDocumentationProvider(private val myQuickNavigateBuilder: BetterTSQuickNavigateBuilder) :
        CodeDocumentationProvider, ExternalDocumentationProvider {

    constructor() : this(BetterTSQuickNavigateBuilder())

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): @Nls String? {
        return QuickDocUtil.inferLinkFromFullDocumentation(this, element, originalElement,
                myQuickNavigateBuilder.getQuickNavigateInfo(element, originalElement))
    }

    override fun fetchExternalDocumentation(project: Project?, element: PsiElement?, docUrls: List<String?>?,
            onHover: Boolean): @Nls String? {
        return null
    }

    @Deprecated("Deprecated in Java",
            ReplaceWith("CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)",
                    "com.intellij.lang.documentation.CompositeDocumentationProvider"))
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean {
        return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        return false
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {}

    override fun getUrlFor(element: PsiElement, originalElement: PsiElement): List<String>? {
        val possibleCssName = findPossibleCssName(element)
        val cssUrls = if (possibleCssName != null) {
            listOf(*CssDocumentationProvider.getUrlsFor(possibleCssName, element))
        } else {
            emptyList()
        }

        return cssUrls.ifEmpty { collectExternalUrls(element, originalElement) }
    }

    @Nls
    private fun processMergedTypeMember(jsExpression: JSExpression, originalElement: PsiElement?,
            results: Array<ResolveResult>): String {
        val element = results[0].element!!

        assert(element is TypeScriptMergedTypeImplicitElement)

        val doc = StringBuilder("<div class='definition'><pre>")
        doc.append(myQuickNavigateBuilder.getQuickNavigateInfoForNavigationElement(element,
                ObjectUtils.coalesce(originalElement, jsExpression), true))
        doc.append("</pre></div>")
        doc.append("<div class='content'>")
        BetterTSDocSimpleInfoPrinter.addDescription(JavaScriptBundle.message("typescript.types.type.member",
                getMergedKindDescription(results)), doc)
        doc.append(P_TAG)
        doc.append(JavaScriptBundle.message("typescript.types.merged.parts", *arrayOfNulls(0)))
        doc.append("<br>")
        val collector = LinkedDocCollector(originalElement!!, results, true)
        val links = collector.getLinks()
        if (links != null) {
            doc.append(links)
        }

        doc.append("</div>")
        return doc.toString()
    }

    private fun appendParameterDoc(builder: StringBuilder, parameter: JSParameter) {
        val settings = TypeScriptCodeStyleSettings.getTypeScriptSettings(parameter)
        if (!DumbService.isDumb(parameter.project)) {
            val type = JSShowTypeInfoAction.getTypeForDocumentation(parameter)
            if (type != null && type !is JSAnyType && settings.JSDOC_INCLUDE_TYPES) {
                builder.append("* ")
                builder.append("@param ")
                builder.append("{")
                builder.append(getTypeTextForGenerateDoc(type))
                builder.append("} ")
                builder.append(parameter.name)
                return
            }
        }

        builder.append("* @param ").append(parameter.name)
    }

    private fun getFunctionReturnTypeForInfoDoc(function: JSFunction): JSType? {
        return if (!TypeScriptCodeStyleSettings.getTypeScriptSettings(function).JSDOC_INCLUDE_TYPES) {
            null
        } else if (function is TypeScriptFunction && function.returnTypeElement != null) {
            function.getReturnType()
        } else {
            TypeFromUsageDetector.detectTypeFromUsage(function)
        }
    }

    private fun getEffectiveElement(psiElement: PsiElement, originalElement: PsiElement?): PsiElement {
        if (psiElement is JSReferenceExpression && originalElement != null) {
            val elements =
                    TypeScriptGoToDeclarationHandler.getResultsFromService(psiElement.project, originalElement, null)
            if (!elements.isNullOrEmpty()) {
                val element = adjustResultFromServiceForDocumentation(elements[0])
                if (element != null) {
                    return element
                }
            }
        }

        var resolve: PsiElement?
        if (psiElement is JSExpression && psiElement !is JSQualifiedNamedElement) {
            val candidate = BetterTSQuickNavigateBuilder.getOriginalElementOrParentIfLeaf(originalElement)
            resolve = candidate
            if (candidate is JSReferenceExpression) {
                resolve = candidate.resolve()
            }
            if (resolve is ES6ImportedExportedDefaultBinding) {
                return resolve
            }
        }

        if (psiElement is ES6ImportExportSpecifierAlias) {
            val element = psiElement.findSpecifierElement()
            if (element != null) {
                resolve = element.resolve()
                return (resolve ?: element)
            }
        }

        return psiElement
    }

    private fun getTypeTextForGenerateDoc(rawType: JSType): String {
        val realRawType = JSTypeUtils.applyCompositeMapping(rawType) { el: JSType? ->
            JSCompositeTypeImpl.optimizeTypeIfComposite(el)
        }!!
        val builder = JSDocTextStringBuilder()
        realRawType.buildTypeText(TypeTextFormat.CODE, builder)
        return builder.result
    }

    private fun shouldAddTypeAnnotation(context: PsiElement?): Boolean {
        return if (context == null) {
            false
        } else {
            val settings = TypeScriptCodeStyleSettings.getTypeScriptSettings(context)
            settings.JSDOC_INCLUDE_TYPES
        }
    }

    private fun getDeclarationAccessModifier(name: String?,
            attributeListOwner: JSAttributeListOwner?): JSAttributeList.AccessType? {
        return if (attributeListOwner == null) {
            null
        } else {
            val attributeList = attributeListOwner.attributeList
            attributeList?.accessType
        }
    }

    override fun generateDoc(psiElement: PsiElement?, originalElement: PsiElement?): String? {
        if (psiElement == null || (psiElement is TypeScriptMergedTypeImplicitElement && originalElement != null && originalElement.context is JSExpression)) {
            return null
        }
        if (psiElement is JSImplicitElement && psiElement !is TypeScriptProxyImplicitElement) {
            return this.getDocumentationForImplicitElement(psiElement)
        }

        var effectiveElement = this.getEffectiveElement(psiElement, originalElement)
        if (effectiveElement.parent != null && effectiveElement.parent is PsiComment) {
            effectiveElement = effectiveElement.parent
        }

        if (effectiveElement is PsiComment) {
            return doGetCommentTextFromComment(effectiveElement, originalElement)
        }

        val results = tryMultiResolveElement(effectiveElement)
        if (results.size > 1) {
            return this.processMultiResolvedElements(effectiveElement, originalElement, results)
        }

        var element = findElementForWhichPreviousCommentWillBeSearched(effectiveElement, originalElement)
        var docComment: PsiComment?
        if (element != null) {
            docComment = JSDocumentationUtils.findDocComment(element,
                    if (effectiveElement is JSAttributeNameValuePair) originalElement else null, null)
            if (docComment == null) {
                val meaningfulElement = getPossibleMeaningfulElement(element)
                docComment = JSDocumentationUtils.findDocComment(meaningfulElement)
                if (docComment != null) {
                    element = meaningfulElement
                }

                if (effectiveElement is JSPsiNamedElementBase) {
                    val name = effectiveElement.name
                    val typeofComment = JSDocumentationUtils.findScopeComment(effectiveElement)
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

            element = findTargetElement(effectiveElement, element)
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
                val builder = this.createDocumentationBuilder(element, originalElement)
                if (!isTypeOnlyComment) {
                    JSDocumentationUtils.processDocumentationTextFromComment(docComment, docComment.node, builder)
                }

                builder.fillEvaluatedType()
                val parameterDoc = if (element is JSParameter) element else null
                return if (parameterDoc != null) builder.getParameterDoc(parameterDoc, null, this,
                        originalElement) else builder.getDoc()
            }
        }

        val possibleCssName = findPossibleCssName(effectiveElement)
        if (possibleCssName != null) {
            val cssDoc = CssDocumentationProvider.generateDoc(possibleCssName, effectiveElement, null)
            if (cssDoc != null) {
                return cssDoc
            }
        }

        if (element == null) {
            return null
        }

        val builder = this.createDocumentationBuilder(element, originalElement)
        builder.fillEvaluatedType()
        return if (builder.showDoc()) builder.getDoc() else null
    }

    private fun processMultiResolvedElements(element: PsiElement, originalElement: PsiElement?,
            results: Array<ResolveResult>): @Nls String? {
        JSResolveUtil.stableResolveOrder(results)
        return if (element is JSExpression && results.isNotEmpty()
                && ContainerUtil.and(results) { r -> r.element is TypeScriptMergedTypeImplicitElement }) {
            this.processMergedTypeMember(element, originalElement, results)
        } else {
            val linkedDocCollector = LinkedDocCollector(originalElement!!, results)
            val linkedDoc = linkedDocCollector.getDoc()
            val links = linkedDocCollector.getLinks()
            if (linkedDoc != null) {
                if (links == null) linkedDoc else "$linkedDoc<div class='content'><p>$links</div>"
            } else {
                if (links != null) "<div class='content'><p>$links</div>" else null
            }
        }
    }

    private fun createDocumentationBuilder(element: PsiElement,
            contextElement: PsiElement?): BetterTSDocumentationBuilder {
        return BetterTSDocumentationBuilder(element, contextElement, this)
    }

    private fun doGetCommentTextFromComment(element: PsiComment, originalElement: PsiElement?): @Nls String? {
        val builder = this.createDocumentationBuilder(element, originalElement)
        JSDocumentationUtils.processDocumentationTextFromComment(element, element.node, builder)
        return builder.getDoc()
    }

    private fun getDocumentationForImplicitElement(element: JSImplicitElement): String? {
        val builder = this.createDocumentationBuilder(element, element)
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

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager?, any: Any?,
            element: PsiElement?): PsiElement? {
        var e = any
        if (e is LookupElement) {
            e = e.psiElement
        }
        return if (e is PsiElement) e else null
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String,
            context: PsiElement?): PsiElement? {
        val delimiterIndex = link.lastIndexOf("%")
        return if (delimiterIndex != -1) {
            resolveDocumentLink(psiManager, link, delimiterIndex)
        } else {
            if (context != null) {
                val references = JSDocReferenceSet(context, link, 0, false).references
                if (references.isNotEmpty()) {
                    val results = (references[references.size - 1] as JSDocReference).multiResolve(false)
                    val var8 = results.size
                    for (var9 in 0 until var8) {
                        val result = results[var9]
                        val resolve = result.element
                        if (resolve != null) {
                            return resolve
                        }
                    }
                }
            }
            null
        }
    }

    override fun findExistingDocComment(contextElement: PsiComment?): PsiComment? {
        return contextElement
    }

    override fun parseContext(startPoint: PsiElement): Pair<PsiElement, PsiComment>? {
        val context = PsiTreeUtil.getNonStrictParentOfType<PsiElement>(startPoint, JSProperty::class.java,
                JSFunction::class.java, JSExpressionStatement::class.java, JSVarStatement::class.java)
        return if (context != null) Pair.create(context, JSDocumentationUtils.findDocComment(context)) else null
    }

    override fun generateDocumentationContentStub(contextComment: PsiComment?): String? {
        val el = JSDocumentationUtils.findAttachedElementFromComment(contextComment)
        return if (el is JSFunction) {
            this.doGenerateDoc(el)
        } else {
            val expression: PsiElement?
            if (el is JSProperty) {
                expression = el.value
                if (expression is JSFunction) {
                    return this.doGenerateDoc(expression as JSFunction)
                }
            } else if (el is JSDefinitionExpression) {
                expression = el.getParent()
                if (expression is JSAssignmentExpression) {
                    val rOperand = expression.rOperand
                    return if (rOperand is JSFunctionExpression) {
                        this.doGenerateDoc(rOperand as JSFunction)
                    } else this.doGenerateDoc(rOperand)
                }
            } else if (el is JSVariable) {
                expression = el.initializer
                if (expression is JSFunctionExpression) {
                    return this.doGenerateDoc(expression as JSFunction)
                }
                if (el is JSField) {
                    return this.doGenerateDoc(el)
                }
                if (expression != null) {
                    return this.doGenerateDoc(expression)
                }
            }
            null
        }
    }

    private fun doGenerateDoc(expression: JSExpression?): String? {
        return if (!isAvailable(expression)) {
            null
        } else {
            val type = if (shouldAddTypeAnnotation(expression)) JSResolveUtil.getExpressionJSType(expression) else null
            var name: String? = null
            val parent = expression!!.parent
            if (parent is JSNamedElement) {
                name = parent.name
            } else if (parent is JSAssignmentExpression) {
                var operand = parent.lOperand
                if (operand is JSDefinitionExpression) {
                    operand = operand.expression
                }
                if (operand is JSReferenceExpression) {
                    name = operand.referenceName
                }
            }
            this.buildTypeDocString(type, name, ObjectUtils.tryCast(parent, JSAttributeListOwner::class.java))
        }
    }

    private fun doGenerateDoc(function: JSFunction): String {
        val builder = StringBuilder()
        val parameterList = function.parameterList
        if (parameterList != null) {
            val var4 = parameterList.parameterVariables
            val var5 = var4.size
            for (var6 in 0 until var5) {
                val parameter = var4[var6]
                appendParameterDoc(builder, parameter)
                builder.append("\n")
            }
        }

        return if (DumbService.isDumb(function.project)) {
            builder.toString()
        } else {
            this.appendFunctionInfoDoc(function, builder)
            builder.toString()
        }
    }

    private fun doGenerateDoc(field: JSField?): String? {
        return if (!isAvailable(field)) {
            null
        } else {
            val type = if (shouldAddTypeAnnotation(field)) field!!.jsType else null
            this.buildTypeDocString(type, field!!.name, field)
        }
    }

    private fun buildTypeDocString(type: JSType?, name: String?, attributeListOwner: JSAttributeListOwner?): String {
        val builder = StringBuilder()
        if (type != null && type !is JSVoidType && (type !is JSAnyType || type.isSourceStrict())) {
            var typeName: String? = getTypeTextForGenerateDoc(type)
            typeName = JSTypeUtils.transformActionScriptSpecificTypesIntoEcma(typeName)
            builder.append("* @type {").append(typeName).append("}\n")
        }

        this.appendAccessModifiersDoc(builder, name, attributeListOwner)
        return builder.toString()
    }

    private fun appendFunctionInfoDoc(function: JSFunction, builder: StringBuilder) {
        val returnType = getFunctionReturnTypeForInfoDoc(function)
        var name: String
        if (returnType != null && returnType !is JSVoidType) {
            name = JSTypeUtils.transformActionScriptSpecificTypesIntoEcma(getTypeTextForGenerateDoc(returnType))
            builder.append("* @").append(returnTag).append(" {").append(name).append("}\n")
        }

        name = function.name!!
        if (function is JSExpression) {
            val initializedElement = JSPsiImplUtils.getInitializedElement(function)
            if (initializedElement != null) {
                name = initializedElement.name!!
            }
        }

        if (!StringUtil.isEmpty(name) && Character.isUpperCase(name[0])) {
            builder.append("* @constructor\n")
        }

        this.appendAccessModifiersDoc(builder, name, function)
    }

    private fun appendAccessModifiersDoc(builder: StringBuilder, name: String?,
            attributeListOwner: JSAttributeListOwner?) {
        val accessType = getDeclarationAccessModifier(name, attributeListOwner)
        if (accessType != null) {
            when (accessType) {
                JSAttributeList.AccessType.PRIVATE -> builder.append("* @private\n")
                JSAttributeList.AccessType.PROTECTED -> builder.append("* @protected\n")
                else -> {}
            }
        }
    }

    fun appendSeeAlsoLink(linkPart: String, displayText: String, seeAlsoValue: String, element: PsiElement,
            result: StringBuilder): Boolean {
        return if (!JSDocumentationUtils.NAMEPATH_PATTERN.matcher(seeAlsoValue).matches()) {
            false
        } else {
            DocumentationManager.createHyperlink(result, seeAlsoValue, seeAlsoValue, true)
            true
        }
    }

    fun getOverloads(mainElement: JSFunctionItem): Collection<JSFunctionItem> {
        return TypeScriptPsiUtil.getAllOverloadSignatures(mainElement)
    }

    val quickNavigateBuilder: BetterTSQuickNavigateBuilder
        get() {
            return myQuickNavigateBuilder
        }

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file is JSFile && !file.isMinified && file !is PsiCompiledFile) {
            object : JSRecursiveWalkingElementVisitor() {
                override fun visitAsFunction(function: JSFunction): Boolean {
                    this.accept(function)
                    return true
                }

                override fun visitJSVariable(node: JSVariable) {
                    if (node !is JSParameter) {
                        accept(node)
                        super.visitJSVariable(node)
                    }
                }

                override fun visitES6ExportDeclaration(exportDeclaration: ES6ExportDeclaration) {
                    accept(exportDeclaration)
                }

                override fun visitTypeScriptImportStatement(importStatement: TypeScriptImportStatement) {
                    accept(importStatement)
                }

                override fun visitES6ImportDeclaration(importDeclaration: ES6ImportDeclaration) {
                    accept(importDeclaration)
                }

                override fun visitJSClass(node: JSClass) {
                    accept(node)
                    super.visitJSClass(node)
                }

                override fun visitTypeScriptTypeMember(node: TypeScriptTypeMember) {
                    accept(node)
                }

                fun accept(element: PsiElement) {
                    val comment = JSDocumentationUtils.findDocComment(element)
                    if (comment is JSDocComment) {
                        sink.accept(comment)
                    }
                }
            }.visitFile(file)
        }
    }

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        var owner = JSDocumentationUtils.findAssociatedElement(comment)
        if (owner == null) {
            owner = comment.owner
        }

        return if (owner == null) {
            null
        } else {
            if (owner is JSInitializerOwner) {
                val initializer = owner.initializer
                if (initializer is JSFunction) {
                    owner = initializer
                }
            }
            val builder = createDocumentationBuilder(owner, owner)
            JSDocumentationUtils.processDocumentationTextFromComment(comment, comment.node, builder)
            builder.renderedDoc
        }
    }

    inner class LinkedDocCollector {

        private val myOriginalElement: PsiElement?

        private val myResults: List<PsiElement>

        private val myMainInfo: Pair<PsiElement, String>?

        private val myLinksOnly: Boolean

        private val myStrict: Boolean

        constructor(originalElement: PsiElement, mainInfo: Pair<PsiElement, String>, results: List<PsiElement>) {
            myOriginalElement = originalElement
            myMainInfo = mainInfo
            myResults = results
            myLinksOnly = false
            myStrict = true
        }

        constructor(originalElement: PsiElement, results: Array<ResolveResult>, linksOnly: Boolean) {
            myStrict = JSResolveUtil.areResolveResultsStrict(results)
            myOriginalElement = originalElement
            var elements: List<PsiElement> = ContainerUtil.mapNotNull(results) { el: ResolveResult ->
                var element = el.element
                if (element is TypeScriptMergedTypeImplicitElement) {
                    element = element.explicitElement
                }
                element
            }
            myMainInfo = if (linksOnly) null else this.findMainInfo(elements)
            myLinksOnly = linksOnly || myMainInfo == null
            if (myMainInfo != null) {
                elements = this.removeMainOverloads(myMainInfo.first, elements)
            }

            myResults = elements
        }

        constructor(originalElement: PsiElement, results: Array<ResolveResult>) : this(originalElement, results, false)

        private fun removeMainOverloads(mainElement: PsiElement, elements: List<PsiElement>): List<PsiElement> {
            var withRemoved: MutableList<PsiElement> = ArrayList(elements)
            if (mainElement is TypeScriptFunction) {
                val overloads = this@BetterTSDocumentationProvider.getOverloads(mainElement)
                withRemoved = ArrayList(elements)
                withRemoved.removeAll(overloads)
            }

            return withRemoved
        }

        private fun findMainInfo(myResults: List<PsiElement>): Pair<PsiElement, String>? {
            var first: Pair<PsiElement, String>? = null
            val var3 = myResults.iterator()

            var result: PsiElement?
            var doc: String?
            do {
                if (!var3.hasNext()) {
                    return first
                }
                result = var3.next()
                doc = RecursionManager.doPreventingRecursion(result,
                        false) { this@BetterTSDocumentationProvider.generateDoc(result, myOriginalElement) }
                if (first == null && doc != null) {
                    first = Pair(result, doc)
                }
            } while (doc == null || !doc.contains("<div class='content'>"))

            return Pair(result, doc)
        }

        @Nls
        fun getDoc(): String? {
            return if (!myLinksOnly && myMainInfo != null) myMainInfo.second as String else null
        }

        @Nls
        fun getLinks(): String? {
            val gatheredDocs = MultiMap.createLinked<String?, PsiElement>()
            val mainElement = if (myMainInfo == null) null else myMainInfo.first as PsiElement
            var i = 0
            while (i < myResults.size && i <= JSReferenceExpressionResolver.MAX_RESULTS_COUNT_TO_KEEP) {
                val element = myResults[i]
                if (mainElement !== element) {
                    gatheredDocs.putValue(element.containingFile.name, element)
                }
                ++i
            }
            return if (!gatheredDocs.isEmpty) {
                val linkedDocFile = mainElement?.containingFile?.name
                val builder = StringBuilder()
                var length = 0
                val elements = gatheredDocs.remove(linkedDocFile)
                if (elements != null) {
                    this.addElementLinks(builder, elements, false)
                }
                var entry: Map.Entry<String, Collection<PsiElement>>
                val var7 = gatheredDocs.entrySet().iterator()
                while (var7.hasNext()) {
                    entry = var7.next()
                    if (builder.length > length) {
                        builder.append(", <br />")
                        length = builder.length
                    }
                    this.addElementLinks(builder, entry.value, true)
                }
                builder.toString()
            } else {
                null
            }
        }

        private fun addElementLinks(builder: StringBuilder, elements: Collection<PsiElement>, addFilename: Boolean) {
            val seenOverloads: MutableSet<NavigationItem?> = HashSet()
            var i = -1
            val indices = getIndices(elements, addFilename)
            val var7: Iterator<*> = elements.iterator()

            while (true) {
                var element: PsiElement?
                var nav: NavigationItem?
                var name: String?
                var presentation: ItemPresentation?
                do {
                    do {
                        do {
                            if (!var7.hasNext()) {
                                return
                            }
                            element = var7.next() as PsiElement?
                            ++i
                            nav = getNavigationItemForLinks(element!!)
                        } while (nav == null)
                        name = nav.name
                    } while (name == null)
                    presentation = nav!!.presentation
                } while (presentation == null)
                var presentableName = name
                if (nav is JSQualifiedNamedElement && (myLinksOnly || !myStrict)) {
                    val module = ES6PsiUtil.findExternalModule(element!!)
                    if (module !is TypeScriptModule) {
                        presentableName = nav.qualifiedName
                    }
                }
                if (nav is JSFunctionItem && !addFilename) {
                    presentableName += JSFormatUtil.buildFunctionSignaturePresentation(nav)
                }
                if (seenOverloads.add(nav)) {
                    if (i != 0) {
                        builder.append(", <br>")
                    }
                    val elementId = indices.getInt(element)
                    JSDocumentationUtils.appendHyperLinkToElement(nav as PsiElement?, name, builder, presentableName,
                            true, addFilename, elementId)
                }
            }
        }

    }

    class OptionalJsDocumentationProvider private constructor() : BetterTSDocumentationProvider() {

        init {
            val var1 = FileTypeManager.getInstance().registeredFileTypes
            val var2 = var1.size
            var shouldThrow = true
            for (var3 in 0 until var2) {
                val registeredFileType = var1[var3]
                if (registeredFileType is JavaScriptFileType) {
                    shouldThrow = false
                }
            }
            if (shouldThrow) {
                throw ExtensionNotApplicableException.create()
            }
        }

    }


    companion object {

        const val DEFAULT_CLASS_NAME = "default"

        @NlsSafe
        const val OBJECT_NAME = "Object"

        private const val RETURN_TAG_PROPERTY = "javascript.return.tag"

        const val SEE_PLAIN_TEXT_CHARS = "\t \"-\\/<>*"

        @NlsSafe
        private const val P_TAG = "<p>"

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
                                BetterTSDocumentationProvider::adjustResultFromServiceForDocumentation)

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

        private fun getPossibleMeaningfulElement(element: PsiElement): PsiElement {
            if (element is JSFunction && element.isConstructor) {
                val containingClass = JSUtils.getMemberContainingClass(element)
                if (containingClass != null) {
                    return containingClass
                }
            }

            return if (DumbService.isDumb(element.project)) {
                element
            } else {
                JSStubBasedPsiTreeUtil.calculateMeaningfulElement(element)
            }
        }

        private fun collectExternalUrls(element: PsiElement, originalElement: PsiElement): List<String>? {
            val dialect = DialectDetector.dialectOfElement(element)
            return if (dialect != null && !dialect.isECMA4 && element !is JSReferenceExpression) {
                val urls: MutableList<String> = SmartList()
                val project = element.project
                if (JSDocumentationUtils.isFromCoreLibFile(element)) {
                    val documentation = JSDocumentationUtils.getJsMdnDocumentation(element, originalElement)
                    if (documentation != null) {
                        urls.add(documentation.url)
                    }
                } else {
                    val libManager = project.getService(JSLibraryManager::class.java) as ScriptingLibraryManager
                    val file = element.containingFile.virtualFile ?: return null
                    val var7 = libManager.getDocUrlsFor(file).iterator()
                    while (var7.hasNext()) {
                        val baseUrl = var7.next()
                        addBaseUrl(urls, element, file, baseUrl)
                    }
                }
                if (urls.isEmpty()) null else urls
            } else {
                null
            }
        }

        private fun addBaseUrl(urls: MutableList<String>, docElement: PsiElement, libFile: VirtualFile,
                baseUrl: String) {
            if (docElement is JSPsiElementBase) {
                var elementFQN = docElement.qualifiedName
                if (elementFQN != null) {
                    var relativeUrl: String
                    if (libFile.path.contains("nodejs")) {
                        elementFQN = patchNodejsFQN(elementFQN, libFile)
                        relativeUrl = getParameterSignature(docElement)
                        elementFQN += relativeUrl
                    }
                    relativeUrl = JSDocumentationUtils.getLibDocRelativeUrl(baseUrl, elementFQN)
                    urls.add(baseUrl + (if (!baseUrl.endsWith("/") && !StringUtil.isEmpty(
                                    relativeUrl)) "/" else "") + relativeUrl)
                }
            }
        }

        private fun patchNodejsFQN(fqn: String, libFile: VirtualFile): String {
            var fileName = libFile.name
            fileName = StringUtil.trimEnd(fileName, ".js")
            return if (fqn.startsWith("exports.")) {
                fqn.replace("^exports".toRegex(), fileName)
            } else {
                if (fqn.startsWith(fileName)) fqn else "$fileName.$fqn"
            }
        }

        private fun getParameterSignature(element: PsiElement): String {
            val parameterBuf = StringBuilder()
            if (element is JSFunction) {
                val var3 = element.parameterVariables
                val var4 = var3.size
                for (var5 in 0 until var4) {
                    val parameter = var3[var5]
                    parameterBuf.append('%').append(parameter.name)
                }
            }
            return parameterBuf.toString()
        }

        private fun getMergedKindDescription(results: Array<ResolveResult>): String {
            var commonDesc: String? = null
            val var3 = results.size

            var var4 = 0
            while (var4 < var3) {
                val r = results[var4]
                val element = r.element as TypeScriptMergedTypeImplicitElement?
                if (element != null) {
                    if (commonDesc == null) {
                        commonDesc = element.sourceKind.description
                    } else {
                        return JavaScriptBundle.message("typescript.types.merged")
                    }
                }

                ++var4
            }

            return commonDesc ?: JavaScriptBundle.message("typescript.types.merged", *arrayOfNulls<Any>(0))
        }

        fun resolveDocumentLink(psiManager: PsiManager, link: String, namespaceDelimiterIndex: Int): PsiElement? {
            var realLink = link
            val project = psiManager.project
            val filenameDelimiterIndex = realLink.lastIndexOf("%", namespaceDelimiterIndex - 1)
            val symbolOffsetStart = realLink.lastIndexOf(":")
            return if (filenameDelimiterIndex == -1) {
                null
            } else {
                val elementId: Int
                if (symbolOffsetStart > filenameDelimiterIndex) {
                    elementId = StringUtil.parseInt(realLink.substring(symbolOffsetStart + 1), -1)
                    realLink = realLink.substring(0, symbolOffsetStart)
                } else {
                    elementId = 0
                }
                val fileName = realLink.substring(0, filenameDelimiterIndex).replace('\\', '/')
                val name = realLink.substring(filenameDelimiterIndex + 1, namespaceDelimiterIndex)
                val qualifier = realLink.substring(namespaceDelimiterIndex + 1)
                val isGlobal = "null" == qualifier
                var relativeFile = if (ApplicationManager.getApplication().isUnitTestMode) TempFileSystem.getInstance()
                        .findFileByPath(fileName) else VfsUtilCore.findRelativeFile(fileName, null as VirtualFile?)
                if (relativeFile == null) {
                    relativeFile = JSResolveUtil.findPredefinedOrLibraryFile(project, fileName)
                }
                val result: Ref<PsiElement> = Ref()
                if (relativeFile != null) {
                    val fileScope = GlobalSearchScope.fileScope(project, relativeFile)
                    val qName = if (isGlobal) name else "$qualifier.$name"
                    val index = AtomicInteger(0)
                    JSClassResolver.getInstance().processElementsByQNameIncludingImplicit(qName, fileScope
                    ) { base: JSPsiElementBase? ->
                        result.set(base)
                        index.incrementAndGet()
                        elementId > 0 && index.get() != elementId
                    }
                }
                result.get()
            }
        }

        fun findElementForWhichPreviousCommentWillBeSearched(psiElement: PsiElement?,
                context: PsiElement?): PsiElement? {
            var parent: PsiElement
            var grandParent: PsiElement
            return if (psiElement !is JSFunction) {
                if (context != null) {
                    val relatedFunction = TypeScriptSignatureChooser.resolveAnyFunction(psiElement, context)
                    if (relatedFunction != null) {
                        return relatedFunction
                    }
                }
                if (psiElement !is JSProperty && psiElement !is JSStatement && psiElement !is JSClass) {
                    if (psiElement != null) {
                        parent = psiElement.parent
                        if (parent is JSAssignmentExpression) {
                            SharedImplUtil.getParent(parent.getNode())
                        } else if (parent is JSAttribute) {
                            grandParent = parent.getParent()
                            if (grandParent.firstChild === parent) {
                                val element = grandParent.parent
                                if (element is JSFile) grandParent else element
                            } else {
                                parent
                            }
                        } else {
                            psiElement
                        }
                    } else {
                        null
                    }
                } else {
                    psiElement
                }
            } else {
                parent = psiElement
                grandParent = psiElement.getParent()
                if (grandParent is JSNewExpression) {
                    grandParent = grandParent.getParent()
                }
                if (grandParent is JSProperty) {
                    parent = grandParent
                } else if (grandParent is JSAssignmentExpression) {
                    parent = grandParent.getParent()
                }
                if (psiElement.isSetProperty || psiElement.isGetProperty) {
                    var doc: PsiElement? = JSDocumentationUtils.findDocComment(psiElement)
                    if (doc != null) {
                        return psiElement
                    }
                    var el = psiElement.getPrevSibling()
                    while (el != null) {
                        if (el !is PsiWhiteSpace && el !is PsiComment) {
                            if (el is JSFunction) {
                                val prevFunction = el
                                val name = prevFunction.name
                                if (name != null && (name == psiElement.name)
                                        && ((prevFunction.isGetProperty && psiElement.isSetProperty)
                                                || (prevFunction.isSetProperty && psiElement.isGetProperty))) {
                                    doc = JSDocumentationUtils.findDocComment(prevFunction)
                                    if (doc != null) {
                                        return prevFunction
                                    }
                                }
                            }
                            break
                        }
                        el = el.prevSibling
                    }
                }
                parent
            }
        }

        private fun isAvailable(context: PsiElement?): Boolean {
            return if (context == null) {
                false
            } else if (DialectDetector.isActionScript(context)) {
                false
            } else {
                val dumbService = DumbService.getInstance(context.project)
                !dumbService.isDumb
            }
        }

        var returnTag: String?
            get() {
                return PropertiesComponent.getInstance().getValue(RETURN_TAG_PROPERTY, "returns")
            }
            set(returnTag) {
                PropertiesComponent.getInstance().setValue(RETURN_TAG_PROPERTY, returnTag)
            }

        private fun getNavigationItemForLinks(element: PsiElement): NavigationItem? {
            val candidate = element.navigationElement
            return if (candidate is NavigationItem) {
//            if (candidate.presentation == null && element is NavigationItem) {
//            }
                candidate
            } else {
                null
            }
        }

        private fun getIndices(elements: Collection<PsiElement>, addFilename: Boolean): Object2IntMap<PsiElement> {
            val qNames = MultiMap<String, PsiElement>()
            val map: Object2IntMap<PsiElement> = Object2IntOpenHashMap()
            val var4 = elements.iterator()

            while (true) {
                var qName: String
                var file: PsiFile?
                do {
                    var element: PsiElement
                    var withQName: Collection<PsiElement>
                    do {
                        do {
                            var name: String?
                            do {
                                if (!var4.hasNext()) {
                                    return map
                                }
                                element = var4.next()
                                val nav = getNavigationItemForLinks(element)
                                name = nav?.name
                            } while (name == null)
                            val namespace =
                                    if (element is JSElementBase) (element as JSElementBase).namespace else null
                            qName = (if (namespace == null) "" else namespace.qualifiedName + ".") + name
                            qNames.putValue(qName, element)
                            withQName = qNames[qName]
                        } while (addFilename && withQName.size != 2)
                    } while (!addFilename && withQName.size != 1)
                    file = element.containingFile
                } while (file == null)
                val implicit = JSClassResolver.getInstance().findElementsByQNameIncludingImplicit(qName, file)
                var index = 1
                val var14 = implicit.iterator()
                while (var14.hasNext()) {
                    val base = var14.next()
                    map.put(base, index++)
                }
            }
        }

        fun adjustResultFromServiceForDocumentation(element: PsiElement?): PsiElement? {
            var realElement = element
            if (realElement is LeafElement) {
                realElement = realElement.parent
            }
            if (realElement is JSReferenceExpression) {
                realElement = realElement.getParent()
            }
            return realElement as? JSQualifiedNamedElement
        }

    }

}