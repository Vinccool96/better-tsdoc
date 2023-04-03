package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.actions.JSShowTypeInfoAction
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor.MetaDocType
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.ecmascript6.TypeScriptSignatureChooser
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.javascript.index.JSSymbolUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.JSComputedPropertyNameOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.resolve.JSInheritanceUtil
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.JSAnyType
import com.intellij.lang.javascript.psi.types.JSGenericParameterType
import com.intellij.lang.javascript.psi.types.JSTypeParser
import com.intellij.lang.javascript.psi.types.JSTypeSourceFactory
import com.intellij.lang.javascript.psi.types.evaluable.JSEvaluableOnlyType
import com.intellij.lang.javascript.psi.util.JSDestructuringContext
import com.intellij.lang.javascript.psi.util.JSDestructuringUtil
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSDocBuilderSimpleInfo.Companion.NEW_LINE_MARKDOWN_PLACEHOLDER
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.CLOSE_PRE
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.FENCE
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.FENCE_REPLACEMENT
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSPreDocBuilderInfo.Companion.OPEN_PRE
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.HashSet
import kotlin.collections.Iterator
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.collections.toList

class BetterTSDocumentationBuilder(private val myElement: PsiElement, private val myContextElement: PsiElement?,
        private val myProvider: BetterTSDocumentationProvider) : JSDocumentationProcessor {

    private val myTargetInfo = BetterTSDocMethodInfoBuilder()

    private val initializer =
            if (this.myElement is JSVariable) JSPsiImplUtils.getAssignedExpression(this.myElement) else null

    private var currentParameterInfo: BetterTSDocParameterInfoBuilder? = null

    private var myInfoForNewLines: BetterTSDocBuilderSimpleInfo = this.myTargetInfo

    private val function =
            findFunction(if (this.initializer is JSFunctionExpression) this.initializer else this.myElement,
                    this.myContextElement)

    private var myNewLinesPendingCount = 0

    var myPreInfo: BetterTSPreDocBuilderInfo? = null

    private var seenPre: String? = null

    private var seenInheritDoc = false

    private val NULL_PARAMETER_INFO = BetterTSDocParameterInfoBuilder()

    override fun needsPlainCommentData(): Boolean {
        return true
    }

    override fun onCommentLine(line: String): Boolean {
        val trimmedLine = line.trim { it <= ' ' }
        if (trimmedLine.isEmpty() && myInfoForNewLines !is BetterTSExampleDocBuilderInfo) {
            return if (!myInfoForNewLines.hasDescription) {
                true
            } else {
                if (currentParameterInfo != null) {
                    currentParameterInfo = null
                    this.updateInfoForNewLines(myTargetInfo)
                }

                if (myNewLinesPendingCount < 2) {
                    ++myNewLinesPendingCount
                }

                true
            }
        }

        this.appendPreOrInfoLine(line)
        myNewLinesPendingCount = if (currentParameterInfo != null) 0 else 1
        return true
    }

    private fun appendPreOrInfoLine(line: String) {
        var preStart = line.indexOf(OPEN_PRE)
        var prePrefix = OPEN_PRE.length
        var preSuffix = CLOSE_PRE.length
        if (seenPre == null && preStart < 0) {
            preStart = line.indexOf(FENCE)
            preSuffix = 3
            prePrefix = 3
        }

        var preClose: Int
        if (FENCE != seenPre && prePrefix != 3) {
            preClose = line.lastIndexOf(CLOSE_PRE)
        } else {
            preClose = line.indexOf(FENCE, if (preStart > 0) preStart + 3 else 0)
            preSuffix = 3
            prePrefix = 3
        }

        if (preStart in 1 until preClose) {
            preClose = -1
        }

        var needNewLines = true
        var preBody: String
        if (preStart >= 0) {
            seenPre = line.substring(preStart)
            if (preStart > 0) {
                preBody = BetterTSPreDocBuilderInfo.getTextBeforePre(line, preStart)
                myInfoForNewLines.appendNewLines(myNewLinesPendingCount)
                this.replaceTagsAndAppendInfoLine(preBody)
                needNewLines = false
            }
            myPreInfo = myTargetInfo.startPre()
        }

        if (seenPre != null) {
            preBody = BetterTSPreDocBuilderInfo.getPreBody(line, preStart, prePrefix, preClose)
            preBody = if (OPEN_PRE == seenPre) preBody.replace(FENCE, FENCE_REPLACEMENT) else preBody
            if (needNewLines) {
                assert(myPreInfo != null)
                myPreInfo!!.appendNewLines(myNewLinesPendingCount)
            }
            this.replaceNewLinesAndAppendPreLine(preBody)
            if (preClose >= 0) {
                val afterText = BetterTSPreDocBuilderInfo.getTextAfterPre(line, preClose, preSuffix)
                if (!StringUtil.isEmpty(afterText)) {
                    this.replaceTagsAndAppendInfoLine(afterText!!)
                }
                seenPre = null
            }
        } else {
            myInfoForNewLines.appendNewLines(myNewLinesPendingCount)
            this.replaceTagsAndAppendInfoLine(line)
        }
    }

    fun replaceNewLinesAndAppendPreLine(line: String) {
        assert(myPreInfo != null)

        myPreInfo!!.appendDescription(line)
    }

    fun replaceTagsAndAppendInfoLine(line: String) {
        var realLine = line
        if (realLine.indexOf(60.toChar()) != -1) {
            realLine = replaceBrTagsWithNewLines(realLine)
        }

        myInfoForNewLines.appendDescription(realLine)
    }

    private fun updateInfoForNewLines(info: BetterTSDocBuilderSimpleInfo) {
        myInfoForNewLines = info
        myNewLinesPendingCount = 0
    }

    override fun onPatternMatch(metaDocType: MetaDocType, matchName: String?, matchValue: String?,
            remainingLineContent: String?, line: String, patternMatched: String): Boolean {
        if (!this.isNestedDocType(metaDocType, patternMatched)) {
            if (myTargetInfo !== myInfoForNewLines) {
                updateInfoForNewLines(myTargetInfo)
            }
            currentParameterInfo = null
        }

        val content: String
        val color: Boolean
        return if (metaDocType == MetaDocType.PREVIOUS_IS_DEFAULT && remainingLineContent != null) {
            color = remainingLineContent.startsWith("0x") && remainingLineContent.length == 8
            content = remainingLineContent.substring(if (color) 2 else 0)
            val parameterInfo = this.getFieldInfo(matchName!!)
            if (parameterInfo != null) {
                parameterInfo.initialValue = content
            } else if (currentParameterInfo != null) {
                currentParameterInfo!!.defaultValue = content
            }
            true
        } else if (metaDocType == MetaDocType.DEFAULT && remainingLineContent != null) {
            color = remainingLineContent.startsWith("0x") && remainingLineContent.length == 8
            content = remainingLineContent.substring(if (color) 2 else 0)
            myTargetInfo.setDefaultValue(content, color, remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.SEE) {
            myTargetInfo.addSeeAlsoText(remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.EVENT) {
            myTargetInfo.addEventOrImplementsTag(EVENTS, matchName, remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.IMPLEMENTS) {
            myTargetInfo.addEventOrImplementsTag(IMPLEMENTS, matchName, remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.EXTENDS) {
            myTargetInfo.addExtendsTag(EXTENDS, matchName, remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.EXAMPLE) {
            val exampleInfo = myTargetInfo.startExample(remainingLineContent, myElement)
            updateInfoForNewLines(exampleInfo)
            true
        } else if (SIMPLE_TAGS.containsKey(metaDocType)) {
            myTargetInfo.addSimpleTag(SIMPLE_TAGS[metaDocType]!!, remainingLineContent)
            true
        } else if (metaDocType == MetaDocType.PREVIOUS_IS_OPTIONAL) {
            var parameterInfo: BetterTSDocParameterInfoBuilder? = null
            if (myTargetInfo.myProperties != null && matchName != null) {
                parameterInfo = myTargetInfo.myProperties!![JSQualifiedNameImpl.fromQualifiedName(matchName)]
            }
            if (parameterInfo == null) {
                parameterInfo = this.getFieldInfo(matchName!!)
            }
            if (remainingLineContent != null) {
                onCommentLine(remainingLineContent)
            }
            if (parameterInfo != null) {
                parameterInfo.optional = true
                true
            } else {
                true
            }
        } else if (metaDocType == MetaDocType.DEPRECATED) {
            val deprecatedInfo = myTargetInfo.startDeprecated(remainingLineContent)
            updateInfoForNewLines(deprecatedInfo)
            true
        } else if (metaDocType == MetaDocType.DESCRIPTION) {
            myTargetInfo.appendBlockDescription(remainingLineContent)
            true
        } else {
            val namedItem = if (function == null && myElement is JSPsiElementBase) myElement else null
            val typeSource = JSTypeSourceFactory.createTypeSource(myElement, true)
            if (metaDocType != MetaDocType.PRIVATE && metaDocType != MetaDocType.PUBLIC && metaDocType != MetaDocType.PROTECTED && metaDocType != MetaDocType.STATIC) {
                when (metaDocType) {
                    MetaDocType.TYPE -> {
                        myTargetInfo.jsType = JSTypeParser.createTypeFromJSDoc(myElement.project, matchValue,
                                typeSource)
                        true
                    }

                    MetaDocType.FINAL -> {
                        myTargetInfo.finalAccess = "final"
                        true
                    }

                    MetaDocType.REQUIRES -> {
                        true
                    }

                    MetaDocType.NAMESPACE -> {
                        myTargetInfo.namespace = matchName
                        true
                    }

                    MetaDocType.INHERIT_DOC -> {
                        seenInheritDoc = true
                        true
                    }

                    else -> {
                        var parameterInfo: BetterTSDocParameterInfoBuilder
                        if (metaDocType == MetaDocType.PROPERTY) {
                            val matchesElement =
                                    namedItem != null && namedItem !is JSImplicitElement && matchName == namedItem.name
                            if (matchName != null && !matchesElement) {
                                val qualifiedName = JSQualifiedNameImpl.fromQualifiedName(matchName)
                                if (myTargetInfo.myProperties == null) {
                                    myTargetInfo.myProperties = LinkedHashMap()
                                }
                                parameterInfo = BetterTSDocParameterInfoBuilder()
                                if (matchValue != null) {
                                    parameterInfo.jsType =
                                            JSTypeParser.createTypeFromJSDoc(myElement.project, matchValue, typeSource)
                                }
                                if (qualifiedName.parent != null) {
                                    parameterInfo.namespace = qualifiedName.parent!!.qualifiedName
                                }
                                parameterInfo.appendDescription(remainingLineContent)
                                myTargetInfo.myProperties!![qualifiedName] = parameterInfo
                                updateInfoForNewLines(parameterInfo)
                            }
                        } else if (metaDocType == MetaDocType.TYPEDEF) {
                            myTargetInfo.jsType =
                                    JSTypeParser.createTypeFromJSDoc(myElement.project, matchValue, typeSource)
                        }
                        val methodGenerationInfo = myTargetInfo
                        val returnInfo: BetterTSDocBuilderSimpleInfo
                        if (metaDocType != MetaDocType.THROWS && metaDocType != MetaDocType.FIRES) {
                            if (metaDocType != MetaDocType.CONSTRUCTOR && metaDocType != MetaDocType.METHOD) {
                                if (metaDocType == MetaDocType.PARAMETER_PROPERTY) {
                                    var fieldName: String?
                                    if (matchName != null && matchName.indexOf(46.toChar()) > 0) {
                                        fieldName = matchName.substring(0, matchName.indexOf("."))
                                        currentParameterInfo = methodGenerationInfo.getInfoForParameterName(fieldName)
                                    }
                                    if (currentParameterInfo != null) {
                                        if (currentParameterInfo!!.optionsMap == null) {
                                            currentParameterInfo!!.optionsMap = LinkedHashMap()
                                        }
                                        fieldName = getFieldName(matchName)
                                        if (fieldName != null) {
                                            parameterInfo = BetterTSDocParameterInfoBuilder()
                                            val parameterType =
                                                    JSTypeParser.createParameterType(myElement.project, matchValue,
                                                            typeSource)
                                            if (parameterType != null) {
                                                parameterInfo.updateFromDecorator(parameterType)
                                            }
                                            parameterInfo.appendDescription(remainingLineContent)
                                            currentParameterInfo!!.optionsMap!![fieldName] = parameterInfo
                                            updateInfoForNewLines(parameterInfo)
                                        }
                                    }
                                } else {
                                    var addReturnTypeInfoFromComments: Boolean
                                    if (metaDocType != MetaDocType.PARAMETER) {
                                        if (metaDocType == MetaDocType.RETURN) {
                                            returnInfo = methodGenerationInfo.returnInfo
                                            addReturnTypeInfoFromComments = true
                                            if (function is JSFunction) {
                                                val holder = DialectDetector.dialectOfElement(function)
                                                var typeFromCommentsFunction: JSType? = null
                                                if (holder != null && (holder.isTypeScript || holder.isECMA4)
                                                        && JSPsiImplUtils.getTypeFromDeclaration(function).also {
                                                            typeFromCommentsFunction = it
                                                        } != null
                                                        && typeFromCommentsFunction!!.typeText != matchValue) {
                                                    addReturnTypeInfoFromComments = false
                                                }
                                            }
                                            if (matchValue != null) {
                                                if (addReturnTypeInfoFromComments) {
                                                    if (function == null || !function.isSetProperty) {
                                                        methodGenerationInfo.returnInfo.jsType =
                                                                JSTypeParser.createTypeFromJSDoc(
                                                                        myElement.project, matchValue, typeSource)
                                                    }
                                                } else {
                                                    returnInfo.appendDescription("$matchValue ")
                                                }
                                            }
                                            if (remainingLineContent != null) {
                                                returnInfo.appendDescription(remainingLineContent)
                                            }
                                            myInfoForNewLines = returnInfo
                                        }
                                    } else {
                                        val elementMatchesParameter = function == null && matchName != null
                                                && myElement is JSImplicitElement && matchName == myElement.name
                                        addReturnTypeInfoFromComments =
                                                patternMatched == JSDocumentationUtils.ourDojoParametersPattern
                                                        .pattern() && (function == null || matchName == null
                                                        || !ContainerUtil.exists(function.parameterVariables) {
                                                    matchName == it.name
                                                })
                                        if (elementMatchesParameter || addReturnTypeInfoFromComments) {
                                            onCommentLine(line)
                                            return true
                                        }
                                        val info = methodGenerationInfo.getInfoForParameterName(matchName)
                                        info.docName = matchName
                                        info.appendDescription(remainingLineContent)
                                        currentParameterInfo = info
                                        updateInfoForNewLines(info)
                                    }
                                }
                            } else {
                                methodGenerationInfo.methodType = StringUtil.toLowerCase(metaDocType.name)
                            }
                        } else {
                            returnInfo = BetterTSDocBuilderSimpleInfo()
                            val typeString = if (metaDocType == MetaDocType.THROWS) matchValue else matchName
                            if (typeString != null) {
                                if (metaDocType == MetaDocType.FIRES) {
                                    returnInfo.hasFiredEvents = true
                                }
                                returnInfo.jsType =
                                        JSTypeParser.createTypeFromJSDoc(myElement.project, typeString, typeSource)
                            }
                            if (remainingLineContent != null) {
                                returnInfo.appendDescription(remainingLineContent)
                            }
                            val infos = if (metaDocType == MetaDocType.THROWS) {
                                methodGenerationInfo.throwsInfos
                            } else {
                                methodGenerationInfo.firesInfos
                            }
                            infos.add(returnInfo)
                            myInfoForNewLines = returnInfo
                        }
                        true
                    }
                }
            } else {
                val s = StringUtil.toLowerCase(metaDocType.name)
                if (myTargetInfo.modifiers == null) {
                    myTargetInfo.modifiers = s
                } else {
                    myTargetInfo.modifiers = myTargetInfo.modifiers + ", " + s
                }
                if (matchName != null) {
                    myTargetInfo.jsType = JSTypeParser.createTypeFromJSDoc(myElement.project, matchName, typeSource)
                }
                true
            }
        }
    }

    fun isNestedDocType(metaDocType: MetaDocType, patternMatched: String): Boolean {
        return metaDocType == MetaDocType.PREVIOUS_IS_OPTIONAL || metaDocType == MetaDocType.PREVIOUS_IS_DEFAULT ||
                (metaDocType == MetaDocType.PARAMETER &&
                        patternMatched != JSDocumentationUtils.ourDojoParametersPattern.pattern())
    }

    override fun postProcess() {
        if (seenInheritDoc) {
            this.processOverriddenMembers()
        }
    }

    fun processOverriddenMembers() {
        if (function != null) {
            val methodInfo = myTargetInfo
            val processorRef: AtomicReference<Processor<JSFunction>?> = AtomicReference()
            val visited: MutableSet<JSFunction?> = HashSet()
            val processor =
                    Processor { function: JSFunction ->
                        if (!visited.add(function)) {
                            return@Processor true
                        } else {
                            if (function !== myElement) {
                                val e =
                                        JSDocumentationUtils.findDocComment(function.navigationElement)
                                if (e != null) {
                                    val builder =
                                            BetterTSDocumentationBuilder(function, function, myProvider)
                                    JSDocumentationUtils.processDocumentationTextFromComment(e, e.node, builder)
                                    val superMethodInfo = builder.myTargetInfo
                                    methodInfo.mergeDescriptionWith(superMethodInfo)
                                    methodInfo.mergeSignatureWith(this.function, function, superMethodInfo)
                                    return@Processor false
                                }
                            }
                            var var8: Iterator<JSPsiElementBase> =
                                    JSInheritanceUtil.findImplementedMethods(function).iterator()
                            var f: JSFunction?
                            do {
                                if (!var8.hasNext()) {
                                    var8 = JSInheritanceUtil.findNearestOverriddenMembers(function, true).iterator()
                                    var fx: JSFunctionItem?
                                    do {
                                        if (!var8.hasNext()) {
                                            return@Processor true
                                        }
                                        val m = var8.next()
                                        fx = JSPsiImplUtils.calculatePossibleFunction(m, null, true)
                                    } while (fx !is JSFunction || processorRef.get()!!.process(fx))
                                    return@Processor false
                                }
                                f = var8.next() as JSFunction?
                            } while (processorRef.get()!!.process(f))
                            return@Processor false
                        }
                    }
            processorRef.set(processor)
            processor.process(myElement as JSFunction)
        } else if (myElement is JSPsiElementBase) {
            val members: Collection<JSPsiElementBase> = if (myElement is JSClass) {
                myElement.superClasses.toList()
            } else {
                JSInheritanceUtil.findNearestOverriddenMembers(myElement, false)
            }
            val var7 = members.iterator()
            while (var7.hasNext()) {
                val member = var7.next()
                val e = JSDocumentationUtils.findDocComment(member)
                if (e != null) {
                    val builder = BetterTSDocumentationBuilder(member, member, myProvider)
                    JSDocumentationUtils.processDocumentationTextFromComment(e, e.node, builder)
                    myTargetInfo.mergeDescriptionWith(builder.myTargetInfo)
                }
            }
        }
    }

    private fun getFieldName(name: String?): String? {
        return if (name == null) {
            null
        } else {
            val dotIndex = name.indexOf(46.toChar())
            if (dotIndex == -1) null else name.substring(dotIndex + 1)
        }
    }

    private fun getFieldInfo(name: String): BetterTSDocParameterInfoBuilder? {
        val fieldName = getFieldName(name)
        return if (fieldName == null) {
            null
        } else {
            val map = currentParameterInfo?.optionsMap
            if (map == null) {
                NULL_PARAMETER_INFO
            } else {
                map[fieldName] ?: NULL_PARAMETER_INFO
            }
        }
    }

    @Nls
    fun getDoc(): String? {
        return this.getDoc(false)
    }

    @Nls
    fun getDoc(isForProperty: Boolean): String? {
        val newResult = StringBuilder()
        this.createPrinter(isForProperty).appendDoc(newResult, myProvider)
        StringUtil.trimEnd(newResult, "<table class='sections'></table>")
        return if (newResult.isNotEmpty()) newResult.toString() else null
    }

    private fun createPrinter(isForProperty: Boolean): BetterTSDocSimpleInfoPrinter<out BetterTSDocBuilderSimpleInfo> {
        var target: BetterTSDocBuilderSimpleInfo = myTargetInfo
        if (isForProperty && myElement is JSElementBase) {
            val propertyTarget = this.findMostSuitablePropertyOrParameterProperty()
            if (propertyTarget != null) {
                target = propertyTarget
            }
        }

        return if (function != null && target is BetterTSDocMethodInfoBuilder) {
            BetterTSDocMethodInfoPrinter(target, function, myElement, myContextElement)
        } else if (target is BetterTSDocSymbolInfoBuilder) {
            BetterTSDocSymbolInfoPrinter(target, myElement, myContextElement, true)
        } else {
            BetterTSDocSimpleInfoPrinter(target, myElement, myContextElement, true)
        }
    }

    private fun findMostSuitablePropertyOrParameterProperty(): BetterTSDocBuilderSimpleInfo? {
        val properties = myTargetInfo.myProperties
        var propertyName = JSQualifiedNameImpl.fromQualifiedNamedElement(
                (myElement as JSElementBase))
        while (propertyName != null) {
            if (properties != null) {
                val propertyInfo = properties[propertyName]
                if (propertyInfo != null) {
                    return propertyInfo
                }
            }
            val var7 = myTargetInfo.parameterInfoMap.entries.iterator()
            while (var7.hasNext()) {
                val (_, value) = var7.next()
                val optionsMap = value.optionsMap
                if (optionsMap != null) {
                    val parameterProperty = optionsMap[propertyName.qualifiedName]
                    if (parameterProperty != null) {
                        return parameterProperty
                    }
                }
            }
            propertyName = propertyName.withoutInnermostComponent(null)
        }
        return null
    }

    @get:Nls
    val renderedDoc: String?
        get() {
            var description = createPrinter(false).getRenderedDoc(myProvider)
            return if (description == null) {
                null
            } else {
                description = StringUtil.trimEnd(description, "<table class='sections'></table>")
                description.ifEmpty { null }
            }
        }

    fun getParameterDoc(parameter: JSParameter, docComment: PsiElement?, provider: BetterTSDocumentationProvider,
            place: PsiElement?): @Nls String? {
        return if (function == null) {
            null
        } else {
            val name = parameter.name!!
            val methodInfo = myTargetInfo
            var parameterInfo = methodInfo.parameterInfoMap[name]
            if (parameterInfo == null && docComment is JSDocComment && function is JSFunction && JSDestructuringUtil.isDestructuring(
                            parameter.parent)) {
                Objects.requireNonNull(JSDestructuringParameter::class.java)
                val destructuringContext = JSDestructuringContext.findDestructuringParents(parameter.parent) { obj ->
                    JSDestructuringParameter::class.java.isInstance(obj)
                }
                if (destructuringContext.outerElement != null) {
                    val fieldName = destructuringContext.buildFieldName()
                    var containerInfo = methodInfo.parameterInfoMap[fieldName]
                    if (containerInfo == null) {
                        val destructuringParameter = destructuringContext.outerElement as JSDestructuringParameter
                        val parameterIndex = JSContextTypeEvaluator.getParameterIndex(destructuringParameter)
                        val topLevelParameters = ArrayList(methodInfo.parameterInfoMap.values)
                        if (parameterIndex >= 0 && parameterIndex < topLevelParameters.size) {
                            containerInfo = topLevelParameters[parameterIndex]
                        }
                    }
                    if (containerInfo != null && fieldName != null) {
                        val fieldInfo = containerInfo.optionsMap?.get(fieldName)
                        if (fieldInfo != null) {
                            return this.buildParameterInfo(name, parameter, fieldInfo, provider, place)
                        }
                    }
                }
            }
            if (parameterInfo == null) {
                parameterInfo = BetterTSDocParameterInfoBuilder()
            }
            this.buildParameterInfo(name, parameter, parameterInfo, provider, place)
        }
    }

    @Nls
    private fun buildParameterInfo(name: String?, parameter: JSParameter,
            parameterInfo: BetterTSDocParameterInfoBuilder,
            provider: BetterTSDocumentationProvider, place: PsiElement?): String {
        val newResult = StringBuilder()
        val printer = BetterTSDocParameterInfoPrinter(parameter, parameterInfo)
        printer.appendDoc(name, myTargetInfo, newResult, provider, place)
        return newResult.toString()
    }

    fun fillEvaluatedType() {
        if (myElement !is JSClass && myTargetInfo.jsType == null) {
            myTargetInfo.jsType = calculateEvaluatedType()
        }
    }

    fun showDoc(): Boolean {
        return if (!DialectDetector.isActionScript(myElement)) {
            true
        } else {
            myElement is JSClass || myTargetInfo.jsType != null
        }
    }

    private fun calculateEvaluatedType(): JSType? {
        val contextElement = myContextElement
        if (contextElement != null) {
            val parent = contextElement.parent
            if (parent is JSReferenceExpression) {
                val typeForDoc = JSShowTypeInfoAction.getTypeForDocumentation(parent)
                if (typeForDoc != null && !JSTypeUtils.isAnyType(typeForDoc)) {
                    return typeForDoc
                }
            }
        }
        val declaredType = JSTypeUtils.getTypeOfElement(myElement)
        return if (declaredType != null && !JSTypeUtils.hasTypes(declaredType, JSEvaluableOnlyType::class.java)
                && !JSTypeUtils.hasTypes(declaredType, JSGenericParameterType::class.java)) {
            declaredType
        } else {
            val expressionForTypeEvaluation = JSPsiImplUtils.getAssignedExpression(myElement)
            if (expressionForTypeEvaluation != null && expressionForTypeEvaluation !is JSFunctionExpression) {
                var type = JSResolveUtil.getExpressionJSType(expressionForTypeEvaluation)
                if (type != null && type !is JSAnyType) {
                    if (type.isSourceStrict) {
                        type = JSTypeUtils.copyWithStrictRecursive(type, false)
                    }
                    return type
                }
            }
            if (expressionForTypeEvaluation == null) JSShowTypeInfoAction.getTypeForDocumentation(myElement) else null
        }
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

        private fun replaceBrTagsWithNewLines(line: String): String {
            return ourBrTagPattern.matcher(line).replaceAll(NEW_LINE_MARKDOWN_PLACEHOLDER)
        }

        fun getNameForDocumentation(element: JSPsiNamedElementBase): String {
            var name = element.name
            return if (name != null) {
                val quote = JSCodeStyleSettings.getQuoteChar(element)
                name = JSSymbolUtil.quoteIfSpecialPropertyName(name, JSUtils.isPrivateSharpItem(element), quote)
                name = ensureBracketsForQuotedName(name, quote)
                name
            } else if (element is JSComputedPropertyNameOwner) {
                val computedName = (element as JSComputedPropertyNameOwner).computedPropertyName
                val expressionAsReferenceName = computedName?.expressionAsReferenceName
                if (expressionAsReferenceName != null) "[$expressionAsReferenceName]" else "[<computed>]"
            } else {
                "<unknown>"
            }
        }

        fun ensureBracketsForQuotedName(name: String, quote: Char): String {
            return if (name[0] == quote) {
                "[$name]"
            } else {
                name
            }
        }

    }

}