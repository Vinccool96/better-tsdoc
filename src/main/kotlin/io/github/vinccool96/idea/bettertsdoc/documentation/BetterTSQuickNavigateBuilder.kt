package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.javascript.JSParameterInfoHandler
import com.intellij.lang.ASTNode
import com.intellij.lang.ecmascript6.psi.*
import com.intellij.lang.ecmascript6.resolve.ES6ImportHandler
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.actions.JSShowTypeInfoAction
import com.intellij.lang.javascript.index.JSSymbolUtil
import com.intellij.lang.javascript.presentable.JSFormatUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.JSFunction.FunctionKind
import com.intellij.lang.javascript.psi.JSType.TypeTextFormat
import com.intellij.lang.javascript.psi.ecma6.*
import com.intellij.lang.javascript.psi.ecmal4.*
import com.intellij.lang.javascript.psi.impl.*
import com.intellij.lang.javascript.psi.resolve.JSImportHandlingUtil
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil
import com.intellij.lang.javascript.psi.resolve.JSTagContextBuilder
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.JSArrayType.GenericArrayBuilder
import com.intellij.lang.javascript.psi.types.JSUnionOrIntersectionType.OptimizedKind
import com.intellij.lang.javascript.psi.types.evaluable.JSEvaluableOnlyType
import com.intellij.lang.javascript.psi.types.guard.JSTypeGuardChecker
import com.intellij.lang.javascript.psi.types.guard.TypeScriptTypeRelations
import com.intellij.lang.javascript.psi.types.primitives.JSUndefinedType
import com.intellij.lang.javascript.psi.util.JSUtils
import  io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSHtmlHighlightingUtil.TextPlaceholder
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.lang.javascript.settings.JSSymbolPresentationProvider
import com.intellij.lang.typescript.documentation.TypeScriptQuickNavigateBuilder
import com.intellij.lang.typescript.psi.TypeScriptDeclarationMappings
import com.intellij.lang.typescript.psi.TypeScriptPsiUtil
import com.intellij.lang.typescript.resolve.TypeScriptGenericTypesEvaluator
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlToken
import com.intellij.ui.ColorUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls

@Suppress("UnstableApiUsage")
class BetterTSQuickNavigateBuilder {

    fun getQuickNavigateInfoForNavigationElement(element: PsiElement, originalElement: PsiElement,
            jsDoc: Boolean): @NlsSafe String? {
        return getQuickNavigateInfoForNavigationElement(element, originalElement, jsDoc,
                FunctionCallType.TYPESCRIPT)
    }

    private fun getQuickNavigateInfoForNavigationElement(element: PsiElement, originalElement: PsiElement,
            jsDoc: Boolean, functionCallType: FunctionCallType): @NlsSafe String? {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                if (!isWhitelisted(originalElement) && !jsDoc) {
                    val serviceResult = getServiceResult(element, originalElement)
                    if (!StringUtil.isEmpty(serviceResult)) {
                        var s = BetterTSServiceQuickInfoParser.parseServiceText(element, serviceResult!!)
                        if (Registry.`is`("typescript.show.own.type")) {
                            s = "$s<br/>[ide]<br/>" + getQuickNavigateInfoForNavigationElement(element,
                                    originalElement, false, FunctionCallType.JAVASCRIPT)
                        }
                        return s
                    }
                }

                return getQuickNavigateInfoForNavigationElement(element, originalElement, jsDoc,
                        FunctionCallType.JAVASCRIPT)
            }

            FunctionCallType.JAVASCRIPT -> {
                return if (element is TypeScriptModule) {
                    val prefix = if (element.isInternal) "namespace " else "module "
                    createQuickNavigateForJSElement(element, originalElement, prefix, ObjectKind.SIMPLE_DECLARATION,
                            jsDoc)
                } else if (element is ES6ImportExportDeclarationPart) {
                    val kind = getKindForImport(element)
                    createQuickNavigateForJSElement(element, originalElement, if (jsDoc) kind.toJSDocPrefix() else "",
                            kind, jsDoc)
                } else {
                    getQuickNavigateInfoForNavigationElement(element, originalElement, jsDoc, FunctionCallType.JS)
                }
            }

            FunctionCallType.JS -> {
                val realOriginalElement = getOriginalElementOrParentIfLeaf(originalElement)!!
                return if (element is JSFunction) {
                    createForFunction(element, realOriginalElement)
                } else if (element is JSClass) {
                    createForJSClass(element, realOriginalElement, jsDoc)
                } else if (element is JSFieldVariable) {
                    createForVariableOrField(element, realOriginalElement, jsDoc)
                } else if (element is JSProperty) {
                    createForProperty(element, realOriginalElement, jsDoc)
                } else if (element is JSImplicitElement) {
                    createQuickNavigateForJSElement(element, realOriginalElement, "", ObjectKind.SIMPLE_DECLARATION,
                            jsDoc)
                } else if (element is XmlToken) {
                    val xmlAttributeDoc = checkAndGetXmlAttributeQuickNavigate(element)
                    if (xmlAttributeDoc != null) {
                        xmlAttributeDoc
                    } else {
                        val builder = JSTagContextBuilder(element, "XmlTag")
                        StringUtil.unquoteString(element.getText()) + ":" + builder.typeName
                    }
                } else {
                    null
                }
            }
        }
    }

    fun getFunctionNameWithHtml(functionItem: JSFunctionItem, substitutor: JSTypeSubstitutor): String {
        return getFunctionNameWithHtml(functionItem, substitutor, FunctionCallType.TYPESCRIPT)
    }

    private fun getFunctionNameWithHtml(functionItem: JSFunctionItem, substitutor: JSTypeSubstitutor,
            functionCallType: FunctionCallType): String {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                val name: String = getFunctionNameWithHtml(functionItem, substitutor, FunctionCallType.JS)
                val generics = getGenerics(functionItem, substitutor)
                return name + StringUtil.escapeXmlEntities(StringUtil.notNullize(generics))
            }

            FunctionCallType.JAVASCRIPT, FunctionCallType.JS -> {
                val parent = JSResolveUtil.findParent(functionItem)
                var namespace = getParentInfo(parent, functionItem, substitutor)
                var functionName = JSPsiImplUtils.findFunctionName(functionItem)
                if (parent is JSAssignmentExpression) {
                    val unqualifiedFunctionName = functionName
                    val definition = parent.lOperand
                    if (definition is JSDefinitionExpression) {
                        val expression = definition.expression
                        if (expression != null) {
                            functionName = null
                            if (expression is JSReferenceExpression) {
                                val qName = JSSymbolUtil.getAccurateReferenceName(
                                        (expression as JSReferenceExpression?)!!)
                                if (qName != null) {
                                    functionName = qName.qualifiedName
                                }
                            }
                            if (functionName == null) {
                                functionName = expression.text
                            }
                            if (namespace != null && functionName == "$namespace.$unqualifiedFunctionName") {
                                namespace = null
                            }
                        }
                    }
                }

                if (functionName == null) {
                    functionName = JSFormatUtil.getAnonymousElementPresentation()
                }

                return if (!StringUtil.isEmpty(namespace)) {
                    namespace + getQNameSeparator(functionItem) + functionName
                } else {
                    functionName!!
                }
            }
        }
    }

    fun getFunctionDefinition(functionItem: JSFunctionItem, escapedName: String, substitutor: JSTypeSubstitutor,
            parameters: Collection<BetterTSDocParameterInfoPrinter>, returnInfo: BetterTSDocBuilderSimpleInfo,
            originalElement: PsiElement, jsDoc: Boolean): CharSequence {
        return getFunctionDefinition(functionItem, escapedName, substitutor, parameters, returnInfo, originalElement,
                jsDoc, FunctionCallType.TYPESCRIPT)
    }

    private fun getFunctionDefinition(functionItem: JSFunctionItem, escapedName: String, substitutor: JSTypeSubstitutor,
            parameters: Collection<BetterTSDocParameterInfoPrinter>, returnInfo: BetterTSDocBuilderSimpleInfo,
            originalElement: PsiElement, jsDoc: Boolean, functionCallType: FunctionCallType): CharSequence {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                if (jsDoc) {
                    val info = getParsedServiceInfo(functionItem, originalElement)
                    val highlighting = getServiceFunctionDefinition(functionItem, escapedName, substitutor, parameters,
                            returnInfo, originalElement, info)
                    if (highlighting != null) {
                        return highlighting
                    }
                }

                return getFunctionDefinition(functionItem, escapedName, substitutor, parameters, returnInfo,
                        originalElement, jsDoc, FunctionCallType.JS)
            }

            FunctionCallType.JAVASCRIPT, FunctionCallType.JS -> {
                val currentBuilder = StringBuilder()
                currentBuilder.append("(")
                val realParameters = if (jsDoc) parameters else expandParameters(functionItem, parameters, substitutor)
                val parent = JSResolveUtil.findParent(functionItem)
                val shouldAppendFunctionKeyword = shouldAppendFunctionKeyword(functionItem, parent)
                val modifiers = StringBuilder()
                appendFunctionAttributes(functionItem, modifiers, shouldAppendFunctionKeyword)
                if (functionItem.isGetProperty) {
                    modifiers.append("get ")
                }

                if (functionItem.isSetProperty) {
                    modifiers.append("set ")
                }

                val paramsStart = currentBuilder.length + escapedName.length + modifiers.length
                val lengthToWrapAll = 10
                val alignment = if (paramsStart >= lengthToWrapAll) 4 else paramsStart
                val placeholders: MutableList<TextPlaceholder> = ArrayList()
                var lastLineStart = escapedName.length
                val var17 = realParameters.iterator()

                while (var17.hasNext()) {
                    val parameterInfo = var17.next()
                    val firstArg = currentBuilder.length == 1
                    if (!firstArg) {
                        lastLineStart = currentBuilder.length
                        if (!jsDoc) {
                            currentBuilder.append(", ")
                        } else {
                            currentBuilder.append(",\n")
                            StringUtil.repeatSymbol(currentBuilder, ' ', alignment)
                        }
                    } else if (jsDoc && paramsStart >= lengthToWrapAll) {
                        lastLineStart = currentBuilder.length
                        currentBuilder.append("\n")
                        StringUtil.repeatSymbol(currentBuilder, ' ', alignment)
                    }
                    val key = parameterInfo.parameterItem
                    currentBuilder.append(JSParameterInfoHandler.getSignatureForParameter(key, substitutor) { t ->
                        var type = t
                        if (type != null && !type!!.isSourceStrict) {
                            type = null
                        }
                        type = getTypeWithAppliedSubstitutor(type, substitutor)
                        val holder =
                                BetterTSHtmlHighlightingUtil.getTypeWithLinksPlaceholder(type,
                                        parameterInfo.myBuilder.hasFiredEvents,
                                        "$\$Type$\$Parameter" + hashCode())
                        placeholders.add(holder)
                        holder.holderText.toString()
                    })
                }

                currentBuilder.append(")")
                val returnTypeBuilder = StringBuilder()
                if (returnInfo.hasType) {
                    currentBuilder.append(": ")
                    if (jsDoc && currentBuilder.length - lastLineStart > 80) {
                        currentBuilder.append("\n")
                        StringUtil.repeatSymbol(currentBuilder, ' ', 2)
                    }
                    val narrowedGetterType =
                            if (functionItem.isGetProperty) getNarrowedType(originalElement, substitutor) else null
                    val returnType = getReturnTypeForQuickNavigate(functionItem, functionItem.isSetProperty,
                            returnInfo.jsType, substitutor)
                    var type = appendOptionality(functionItem, returnType, narrowedGetterType, returnTypeBuilder,
                            originalElement)
                    if (!functionItem.isGetProperty) {
                        type = returnType
                    }
                    val placeholder = BetterTSHtmlHighlightingUtil.getTypeWithLinksPlaceholder(type,
                            returnInfo.hasFiredEvents, "$\$Type$\$Unique")
                    ContainerUtil.addIfNotNull(placeholders, placeholder)
                    currentBuilder.append(placeholder.holderText)
                }

                val s = "" + BetterTSHtmlHighlightingUtil.getFunctionHtmlHighlighting(functionItem, escapedName,
                        shouldAppendFunctionKeyword, modifiers, currentBuilder,
                        placeholders) + returnTypeBuilder.toString()
                return if (jsDoc) {
                    s
                } else {
                    buildResult(getFunctionKind(functionItem, JSPsiImplUtils.isGetterOrSetter(functionItem),
                            shouldAppendFunctionKeyword), s, functionItem, originalElement)
                }
            }
        }
    }

    private fun getServiceFunctionDefinition(functionItem: JSFunctionItem, escapedName: String,
            substitutor: JSTypeSubstitutor, parameters: Collection<BetterTSDocParameterInfoPrinter>,
            returnInfo: BetterTSDocBuilderSimpleInfo, originalElement: PsiElement,
            info: BetterTSServiceQuickInfoParser.ParsedInfo?): CharSequence? {
        return if (info == null) {
            null
        } else {
            val rest = StringUtil.trimStart(info.myRestPartWithPlaceHolders, "?")
            val rawNewName = info.myQName
            if (!rest.startsWith("(")) {
                if (rest.startsWith(":") && functionItem.isGetProperty) {
                    val type =
                            BetterTSHtmlHighlightingUtil.parseType(StringUtil.trimStart(rest, ":").trim { it <= ' ' },
                                    functionItem)
                    if (type != null) {
                        val newReturnInfo = BetterTSDocBuilderSimpleInfo()
                        newReturnInfo.jsType = type
                        return buildForFunctionFromService(functionItem, escapedName, substitutor, parameters,
                                returnInfo, originalElement, info, rawNewName, ContainerUtil.emptyList(), newReturnInfo)
                    }
                }
                null
            } else {
                val parsedFunction = parseFunctionText(rest, functionItem)
                if (parsedFunction != null) {
                    val newParameters = mapParametersToInfos(parsedFunction)
                    val newReturnInfo = mapReturnTypeToInfo(parsedFunction)
                    buildForFunctionFromService(functionItem, escapedName, substitutor, parameters, returnInfo,
                            originalElement, info, rawNewName, newParameters, newReturnInfo)
                } else {
                    null
                }
            }
        }
    }

    private fun buildForFunctionFromService(functionItem: JSFunctionItem, escapedName: String,
            substitutor: JSTypeSubstitutor, parameters: Collection<BetterTSDocParameterInfoPrinter>,
            returnInfo: BetterTSDocBuilderSimpleInfo, originalElement: PsiElement,
            info: BetterTSServiceQuickInfoParser.ParsedInfo, rawNewName: String,
            newParameters: List<BetterTSDocParameterInfoPrinter>,
            newReturnInfo: BetterTSDocBuilderSimpleInfo): CharSequence {
        val highlighting = restoreHolders(info, getFunctionDefinition(functionItem,
                StringUtil.escapeXmlEntities(rawNewName), JSTypeSubstitutor.EMPTY, newParameters, newReturnInfo,
                originalElement, true, FunctionCallType.JAVASCRIPT))
        return appendOwnSignature(functionItem, escapedName, substitutor, parameters, returnInfo, originalElement,
                highlighting) ?: highlighting
    }

    private fun appendOwnSignature(functionItem: JSFunctionItem, escapedName: String, substitutor: JSTypeSubstitutor,
            parameters: Collection<BetterTSDocParameterInfoPrinter>, returnInfo: BetterTSDocBuilderSimpleInfo,
            originalElement: PsiElement, highlighting: CharSequence): CharSequence? {
        return if (Registry.`is`("typescript.show.own.type")) {
            val ownSignature = getFunctionDefinition(functionItem, escapedName, substitutor, parameters, returnInfo,
                    originalElement, true, FunctionCallType.JAVASCRIPT)
            "$highlighting\n[ide]\n$ownSignature"
        } else {
            null
        }
    }

    fun appendOptionality(variableOrField: JSElement, declaredType: JSType?, narrowedType: JSType?,
            builder: StringBuilder, originalElement: PsiElement): JSType? {
        return appendOptionality(variableOrField, declaredType, narrowedType, builder, originalElement,
                FunctionCallType.TYPESCRIPT)
    }

    private fun appendOptionality(variableOrField: JSElement, declaredType: JSType?, narrowedType: JSType?,
            builder: StringBuilder, originalElement: PsiElement, functionCallType: FunctionCallType): JSType? {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                val config = TypeScriptConfigUtil.getConfigForPsiFile(originalElement.containingFile)
                return if (config == null || !config.strictNullChecks() || narrowedType == null ||
                        (narrowedType is JSUnionType && narrowedType !is JSUndefinedType
                                && TypeScriptTypeRelations.isUnionWithUndefinedType(narrowedType))) {
                    appendOptionality(variableOrField, declaredType, narrowedType, builder, originalElement,
                            FunctionCallType.JS)
                } else {
                    narrowedType
                }
            }

            FunctionCallType.JAVASCRIPT, FunctionCallType.JS -> {
                var toUse = narrowedType ?: declaredType
                if ((variableOrField is JSOptionalOwner && variableOrField.isOptional)
                        || isGuessedOptional(variableOrField)) {
                    builder.append("?")
                    if (narrowedType != null && (declaredType !is JSUnionType || !TypeScriptTypeRelations.isUnionWithUndefinedType(
                                    declaredType))) {
                        toUse = JSCompositeTypeImpl.optimizeTypeIfComposite(narrowedType,
                                OptimizedKind.OPTIMIZED_REMOVED_NULL_UNDEFINED)
                    }
                }

                return toUse
            }
        }
    }

    fun appendTypeWithSeparatorForOwner(typeOwner: JSElement, toUse: JSType?, substitutor: JSTypeSubstitutor,
            builder: StringBuilder, originalElement: PsiElement, jsDoc: Boolean) {
        appendTypeWithSeparatorForOwner(typeOwner, toUse, substitutor, builder, originalElement, jsDoc,
                FunctionCallType.TYPESCRIPT)
    }

    private fun appendTypeWithSeparatorForOwner(typeOwner: JSElement, toUse: JSType?, substitutor: JSTypeSubstitutor,
            builder: StringBuilder, originalElement: PsiElement, jsDoc: Boolean, functionCallType: FunctionCallType) {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                if (!jsDoc) {
                    appendTypeWithSeparatorForOwner(typeOwner, toUse, substitutor, builder, originalElement,
                            false, FunctionCallType.JS)
                } else if (!appendServiceType(typeOwner, toUse, substitutor, builder, originalElement)) {
                    appendTypeWithSeparatorForOwner(typeOwner, toUse, substitutor, builder, originalElement, true,
                            FunctionCallType.JS)
                }
            }

            FunctionCallType.JAVASCRIPT, FunctionCallType.JS -> {
                val variableOrFieldTypeText = getPresentableTypeText(toUse, substitutor, jsDoc, typeOwner)
                if (variableOrFieldTypeText != null) {
                    appendType(typeOwner, variableOrFieldTypeText, builder)
                }
            }
        }
    }

    private fun appendServiceType(typeOwner: JSElement, toUse: JSType?, substitutor: JSTypeSubstitutor,
            builder: StringBuilder, originalElement: PsiElement): Boolean {
        val info = getParsedServiceInfo(typeOwner, originalElement)
        return if (info == null) {
            false
        } else {
            try {
                val part = info.myRestPartWithPlaceHolders
                if (!part.startsWith(":") && !part.startsWith("?:")) {
                    false
                } else {
                    val serviceTypeText = part.substring(if (part.startsWith(":")) 1 else 2).trim { it <= ' ' }
                    val parsedServiceType = BetterTSHtmlHighlightingUtil.parseType(serviceTypeText, originalElement)
                    if (parsedServiceType != null) {
                        val highlighting = restoreHolders(info,
                                BetterTSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(parsedServiceType,
                                        originalElement, false))
                        if (Registry.`is`("typescript.show.own.type")) {
                            appendType(originalElement, highlighting.toString(), builder)
                            builder.append("\n<span style='color:grey;'>[ide type]</span>")
                            appendTypeWithSeparatorForOwner(typeOwner, toUse, substitutor, builder,
                                    originalElement, true)
                        } else {
                            appendType(originalElement, highlighting.toString(), builder)
                        }
                    } else {
                        builder.append(restoreHolders(info, StringUtil.escapeXmlEntities(part)))
                    }
                    true
                }
            } catch (var11: ProcessCanceledException) {
                throw var11
            } catch (var12: java.lang.Exception) {
                Logger.getInstance(TypeScriptQuickNavigateBuilder::class.java).warn(var12)
                false
            }
        }
    }

    fun appendVariableInitializer(variable: JSVariable, result: StringBuilder) {
        appendVariableInitializer(variable, result, FunctionCallType.TYPESCRIPT)
    }

    private fun appendVariableInitializer(variable: JSVariable, result: StringBuilder,
            functionCallType: FunctionCallType) {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT -> {
                if (variable !is TypeScriptVariable || !variable.isConst()) {
                    val text = variable.literalOrReferenceInitializerText
                    if (text != null) {
                        appendVariableInitializer(variable, result, FunctionCallType.JS)
                    }
                }
            }

            FunctionCallType.JAVASCRIPT, FunctionCallType.JS -> {
                var initializerText = variable.literalOrReferenceInitializerText
                if (initializerText == null) {
                    val initializer = variable.initializer
                    if (initializer != null) {
                        initializerText = initializer.text
                    }
                }

                if (initializerText != null) {
                    if (initializerText.length > 50) {
                        initializerText = initializerText.substring(0, 50) + " ..."
                    }
                    result.append(" = ").append(initializerText)
                }
            }
        }
    }

    fun appendClassExtendsAndImplements(jsClass: JSClass, originalElement: PsiElement,
            packageNameOrEmptyString: String, result: StringBuilder) {
        appendGenerics(jsClass, originalElement, result)
        var extendsList = generateReferenceTargetList(jsClass.extendsList, packageNameOrEmptyString)
        if (extendsList == null && isIncludeObjectInExtendsList && "Object" != jsClass.name) {
            extendsList = "Object"
        }

        if (extendsList != null) {
            result.append(" extends ").append(extendsList)
        }

        val implementsList = generateReferenceTargetList(jsClass.implementsList, packageNameOrEmptyString)
        if (implementsList != null) {
            result.append("\nimplements ").append(implementsList)
        }
    }

    fun getKindForImport(part: ES6ImportExportDeclarationPart): ObjectKind {
        return if (part is ES6ImportSpecifier) {
            ObjectKind.IMPORT_SPECIFIER
        } else if (part is ES6ImportedBinding) {
            if (part.isNamespaceImport) ObjectKind.IMPORT_ALL else ObjectKind.IMPORT_DEFAULT
        } else if (part !is ES6ExportSpecifier && part !is ES6ExportedDefaultBinding) {
            ObjectKind.SIMPLE_DECLARATION
        } else {
            ObjectKind.EXPORT
        }
    }

    fun getFieldOrVariableKind(variableOrField: JSFieldVariable): ObjectKind {
        return getFieldOrVariableKind(variableOrField, FunctionCallType.JAVASCRIPT)
    }

    private fun getFieldOrVariableKind(variableOrField: JSFieldVariable,
            functionCallType: FunctionCallType): ObjectKind {
        return when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                if (variableOrField is JSField
                        || (variableOrField is JSParameter && TypeScriptPsiUtil.isFieldParameter(variableOrField))) {
                    ObjectKind.PROPERTY
                } else {
                    getFieldOrVariableKind(variableOrField, FunctionCallType.JS)
                }
            }

            FunctionCallType.JS -> {
                if (variableOrField is JSParameter) ObjectKind.PARAMETER else ObjectKind.SIMPLE_DECLARATION
            }
        }
    }

    protected fun getTypeSubstitutor(candidate: JSElement, originalElement: PsiElement): JSTypeSubstitutor {
        val element = getOriginalElementOrParentIfLeaf(originalElement)!!
        val isSourceElement = isElementFromTSSources(candidate, element)
        var typeSubstitutorTarget = candidate
        if (isSourceElement) {
            typeSubstitutorTarget = getOriginalResolvedElement(candidate, element as JSReferenceExpression)
        }

        return TypeScriptGenericTypesEvaluator.getInstance()
                .getTypeSubstitutorForMember(typeSubstitutorTarget, element)
    }

    protected fun appendClassAttributes(jsClass: JSClass, originalElement: PsiElement, packageOrModule: String?,
            result: StringBuilder) {
        appendClassAttributes(jsClass, originalElement, packageOrModule, result, FunctionCallType.JAVASCRIPT)
    }

    private fun appendClassAttributes(jsClass: JSClass, originalElement: PsiElement, packageOrModule: String?,
            result: StringBuilder, functionCallType: FunctionCallType) {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                val isAlias = jsClass is TypeScriptTypeAlias
                val isEnum = jsClass is TypeScriptEnum
                if (!isAlias && !isEnum) {
                    appendClassAttributes(jsClass, originalElement, packageOrModule, result, FunctionCallType.JS)
                } else {
                    appendAttrList(jsClass, result)
                    result.append(if (isAlias) "type " else "enum ")
                }
            }

            FunctionCallType.JS -> {
                appendAttrList(jsClass, result)
                result.append(if (jsClass.isInterface) "interface " else "class ")
            }
        }
    }

    private fun appendGenerics(jsClass: JSClass, originalElement: PsiElement, result: StringBuilder) {
        if (jsClass is TypeScriptTypeParameterListOwner) {
            val substitutor = getTypeSubstitutor(jsClass, originalElement)
            val generics = getGenerics(jsClass, substitutor)
            if (generics != null) {
                result.append(generics)
            }
        }
    }

    protected fun getVarPrefix(variable: JSVariable): String {
        return getVarPrefix(variable, FunctionCallType.JAVASCRIPT)
    }

    private fun getVarPrefix(variable: JSVariable, functionCallType: FunctionCallType): String {
        return when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                if (variable !is JSField && variable !is JSParameter) {
                    getVarPrefix(variable, FunctionCallType.JS)
                } else {
                    ""
                }
            }

            FunctionCallType.JS -> {
                val statement = variable.statement
                val keyword = statement?.varKeyword
                if (keyword != null) keyword.text + " " else ""
            }
        }
    }

    protected fun getParentInfo(parent: PsiElement?, element: PsiNamedElement,
            substitutor: JSTypeSubstitutor): String? {
        return getParentInfo(parent, element, substitutor, FunctionCallType.JAVASCRIPT)
    }

    private fun getParentInfo(parent: PsiElement?, element: PsiNamedElement, substitutor: JSTypeSubstitutor,
            functionCallType: FunctionCallType): String? {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                var realParent = parent
                if (element is JSParameter && TypeScriptPsiUtil.isFieldParameter(element)) {
                    realParent = JSUtils.getMemberContainingClass(element)
                }

                if (realParent is TypeScriptInterfaceClass) {
                    val generics = getGenerics(realParent, substitutor)
                    if (generics != null) {
                        val className = StringUtil.notNullize((realParent as JSClass).qualifiedName, "default")
                        return className + StringUtil.escapeXmlEntities(generics)
                    }
                }

                return if (realParent is TypeScriptModule && realParent.isInternal) {
                    realParent.qualifiedName
                } else {
                    getParentInfo(realParent, element, substitutor, FunctionCallType.JS)
                }
            }

            FunctionCallType.JS -> {
                return if (parent is JSClass) StringUtil.notNullize(parent.qualifiedName, "default") else ""
            }
        }
    }

    protected fun formatVisibility(owner: JSAttributeListOwner, attributeList: JSAttributeList,
            type: JSAttributeList.AccessType): String? {
        return formatVisibility(owner, attributeList, type, FunctionCallType.JAVASCRIPT)
    }

    private fun formatVisibility(owner: JSAttributeListOwner, attributeList: JSAttributeList,
            type: JSAttributeList.AccessType, functionCallType: FunctionCallType): String? {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                return if (type == JSAttributeList.AccessType.PUBLIC && attributeList.explicitAccessType == null) {
                    null
                } else {
                    if (type == JSAttributeList.AccessType.PRIVATE && attributeList.hasPrivateSharp()) {
                        null
                    } else {
                        formatVisibility(owner, attributeList, type, FunctionCallType.JS)
                    }
                }
            }

            FunctionCallType.JS -> {
                return JSFormatUtil.formatVisibility(type, owner)
            }
        }
    }

    protected fun expandParameters(functionItem: JSFunctionItem,
            parameters: Collection<BetterTSDocParameterInfoPrinter>,
            substitutor: JSTypeSubstitutor): Collection<BetterTSDocParameterInfoPrinter> {
        val result = ArrayList<BetterTSDocParameterInfoPrinter>()
        val functionType = JSParameterInfoHandler.mapToFunction(functionItem, substitutor)
        val decorators = TypeScriptJSFunctionTypeImpl.expandRestTupleTypes(functionType.parameters, -1)
        val infos = SmartList(parameters)

        for (i in decorators.indices) {
            val decorator = decorators[i]
            if (infos.size > i) {
                val info = infos[i]
                val item = info!!.parameterItem
                if (info.hasTypeElement() && !item.typeDecorator.isEquivalentTo(decorator, null, true)) {
                    result.add(BetterTSDocParameterInfoPrinter(decorator))
                } else {
                    result.add(info)
                }
            } else {
                result.add(BetterTSDocParameterInfoPrinter(decorator))
            }
        }

        return result
    }

    protected fun getNarrowedType(originalElement: PsiElement, substitutor: JSTypeSubstitutor): JSType? {
        val realOriginalElement = getOriginalElementOrParentIfLeaf(originalElement)
        if (realOriginalElement is JSReferenceExpression
                && JSTypeGuardChecker.isNarrowableReference(realOriginalElement)) {
            val narrowType = JSResolveUtil.getExpressionJSType(realOriginalElement)
            if (narrowType != null && (narrowType.isTypeScript || narrowType !is JSRecordType)) {
                return getTypeWithAppliedSubstitutor(narrowType, substitutor)
            }
        }

        return null
    }

    protected fun isGuessedOptional(variableLikeElement: JSElement): Boolean {
        return isGuessedOptional(variableLikeElement, FunctionCallType.JAVASCRIPT)
    }

    private fun isGuessedOptional(variableLikeElement: JSElement, functionCallType: FunctionCallType): Boolean {
        when (functionCallType) {
            FunctionCallType.TYPESCRIPT, FunctionCallType.JAVASCRIPT -> {
                if (variableLikeElement is JSParameter && DialectDetector.isJavaScript(
                                variableLikeElement) && variableLikeElement.jsType == null) {
                    val function = variableLikeElement.declaringFunction
                    val name = variableLikeElement.getName()
                    if (function != null && name != null) {
                        val optionalCandidates = CachedValuesManager.getCachedValue(function) {
                            val visitor = JSOptionalityEvaluator()
                            visitor.visitElement(function.node)
                            CachedValueProvider.Result(visitor.myOptionalCandidates, function)
                        }
                        return optionalCandidates != null && optionalCandidates.contains(name)
                    }
                }

                return isGuessedOptional(variableLikeElement, FunctionCallType.JS)
            }

            FunctionCallType.JS -> {
                return false
            }
        }
    }

    fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): @Nls String? {
        return if (originalElement == null) {
            null
        } else {
            var realElement = element
            if (realElement is JSOffsetBasedImplicitElement) {
                realElement = realElement.elementAtOffset
            }
            if (realElement != null && !DumbService.isDumb(realElement.project)) {
                this.getQuickNavigateInfoForNavigationElement(realElement.navigationElement, originalElement, false)
            } else {
                null
            }
        }
    }

    protected fun createForProperty(element: JSProperty, originalElement: PsiElement, jsDoc: Boolean): String? {
        var name = JSPsiImplUtils.getNameOrComputedPropertyName(element, true)
        return if (name == null) {
            null
        } else {
            name = BetterTSDocumentationBuilder.ensureBracketsForQuotedName(name, '"')
            val parent = if (element === getOriginalElementOrParentIfLeaf(originalElement)) {
                null
            } else {
                getPropertyParent(element)
            }
            val qName = formatMemberAccess(parent?.qualifiedName, name, ".")
            val substitutor = getTypeSubstitutor(element, originalElement)
            val declaredType = getPropertyType(element)!!
            val typePart = createTypePart(element, originalElement, jsDoc, substitutor, declaredType)
            val result = buildHtmlForProperty(element, qName, if (jsDoc) "" else typePart) + if (jsDoc) typePart else ""
            val kind = if (element.value is JSFunctionExpression) ObjectKind.FUNCTION else ObjectKind.PROPERTY
            if (jsDoc) {
                result
            } else {
                buildResult(kind, result, element, originalElement)
            }
        }
    }

    protected fun getPropertyType(element: JSProperty): JSType? {
        return JSTypeUtils.widenLiteralTypes(element.jsType)
    }

    private fun getPropertyParent(property: JSProperty): JSQualifiedNamedElement? {
        val parent = property.parent
        if (parent is JSObjectLiteralExpression) {
            val literalContainer = parent.getParent()
            if (literalContainer is JSVariable) {
                return literalContainer
            }
        }

        return null
    }

    protected fun createForFunction(function: JSFunction, originalElement: PsiElement): String? {
        val newParameters: List<BetterTSDocParameterInfoPrinter> = mapParametersToInfos(function)
        val newReturnInfo: BetterTSDocBuilderSimpleInfo = mapReturnTypeToInfo(function)
        return getFunctionDefinitionWithHighlighting(function, newParameters, newReturnInfo, originalElement,
                false).toString()
    }

    protected fun getQNameSeparator(element: PsiElement): String {
        return "."
    }

    fun getFunctionDefinitionWithHighlighting(functionItem: JSFunctionItem,
            parameters: Collection<BetterTSDocParameterInfoPrinter>, returnInfo: BetterTSDocBuilderSimpleInfo,
            originalElement: PsiElement, jsDoc: Boolean): CharSequence {
        val substitutor = getTypeSubstitutor(functionItem, originalElement)
        val escapedName = this.getFunctionNameWithHtml(functionItem, substitutor)
        return this.getFunctionDefinition(functionItem, escapedName, substitutor, parameters, returnInfo,
                originalElement, jsDoc)
    }

    protected fun getFunctionKind(function: JSFunctionItem, isGetterOrSetter: Boolean,
            shouldAppendKeyword: Boolean): ObjectKind {
        return if (isGetterOrSetter) {
            ObjectKind.PROPERTY
        } else if (function is JSFunction && function.isConstructor) {
            ObjectKind.SIMPLE_DECLARATION
        } else if (shouldAppendKeyword) {
            ObjectKind.SIMPLE_DECLARATION
        } else {
            ObjectKind.FUNCTION
        }
    }

    protected fun createForVariableOrField(variableOrField: JSFieldVariable, originalElement: PsiElement?,
            jsDoc: Boolean): String {
        val parent = JSResolveUtil.findParent(variableOrField)
        val modifiers = java.lang.StringBuilder()
        val substitutor = getTypeSubstitutor(variableOrField, originalElement!!)
        appendAttrList(variableOrField, modifiers)
        var hasVarPrefix = false
        var prefix: String?
        if (variableOrField is JSVariable) {
            prefix = this.getVarPrefix(variableOrField)
            if (prefix.isNotEmpty()) {
                hasVarPrefix = true
                modifiers.append(prefix)
            }
        }

        prefix = getParentInfo(parent, variableOrField, substitutor)
        val name = BetterTSDocumentationBuilder.getNameForDocumentation(variableOrField)
        val qName = formatMemberAccess(prefix, name, getQNameSeparator(variableOrField))
        val declaredType = getVariableOrFieldType(variableOrField)!!
        val typeAndInitializer = createTypePart(variableOrField, originalElement, jsDoc, substitutor, declaredType)
        val textWithHighlighting = buildHtmlForVariableOrField(variableOrField, modifiers, qName, hasVarPrefix,
                (if (jsDoc) "" else typeAndInitializer))

        return if (jsDoc) {
            textWithHighlighting + typeAndInitializer
        } else {
            val kind = getFieldOrVariableKind(variableOrField)
            buildResult(kind, textWithHighlighting, variableOrField, originalElement)
        }
    }

    private fun createTypePart(element: JSElement, originalElement: PsiElement, jsDoc: Boolean,
            substitutor: JSTypeSubstitutor, declaredType: JSType): CharSequence {
        val typePart = StringBuilder()
        val narrowedType = getNarrowedType(originalElement, substitutor)
        val type = appendOptionality(element, declaredType, narrowedType, typePart, originalElement)
        this.appendTypeWithSeparatorForOwner(element, type, substitutor, typePart, originalElement, jsDoc)
        if (element is JSVariable && element.hasInitializer() && !jsDoc) {
            appendVariableInitializer(element, typePart)
        }

        return typePart
    }

    protected fun mapParametersToInfos(parsedFunction: JSFunctionItem): List<BetterTSDocParameterInfoPrinter> {
        return ContainerUtil.map(parsedFunction.parameters) { parameter: JSParameterItem? ->
            BetterTSDocParameterInfoPrinter(parameter)
        }
    }

    protected fun mapReturnTypeToInfo(parsedFunction: JSFunctionItem): BetterTSDocBuilderSimpleInfo {
        val newReturnInfo = BetterTSDocBuilderSimpleInfo()
        newReturnInfo.jsType = parsedFunction.returnType

        return newReturnInfo
    }

    fun createForJSClass(jsClass: JSClass, originalElement: PsiElement?, jsDoc: Boolean): String? {
        var qName = jsClass.qualifiedName
        return if (qName == null) {
            null
        } else {
            val attributesAndKeyword = StringBuilder()
            val packageName = StringUtil.getPackageName(qName)
            this.appendClassAttributes(jsClass, originalElement!!, packageName, attributesAndKeyword)
            qName = getClassQualifiedName(jsClass, packageName)!!
            val extendsImplements = StringBuilder()
            appendClassExtendsAndImplements(jsClass, originalElement, packageName, extendsImplements)
            val htmlForClass = buildHtmlForClass(jsClass, attributesAndKeyword, qName, extendsImplements)
            if (jsDoc) {
                htmlForClass
            } else {
                buildResult(ObjectKind.SIMPLE_DECLARATION, htmlForClass, jsClass, originalElement)
            }
        }
    }

    protected fun getReturnTypeForQuickNavigate(function: JSFunctionItem, set: Boolean, returnType: JSType?,
            substitutor: JSTypeSubstitutor): JSType? {
        return if (!set) {
            var type = returnType
            if (returnType is JSFunctionReturnWrapperType) {
                type = returnType.substitute()
            }
            if (!JSFormatUtil.isPossiblyPresentableType(type, function, true)) {
                null
            } else {
                getTypeWithAppliedSubstitutor(type, substitutor)
            }
        } else {
            val parameters = function.parameters
            if (parameters.isNotEmpty()) {
                getTypeWithAppliedSubstitutor(parameters[0].inferredType, substitutor)
            } else {
                null
            }
        }
    }

    protected fun getPresentableTypeText(rawType: JSType?, substitutor: JSTypeSubstitutor, jsDoc: Boolean,
            context: PsiElement?): String? {
        var type = getTypeWithAppliedSubstitutor(rawType, substitutor)
        return if (type == null) {
            null
        } else if (jsDoc) {
            BetterTSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(expandTypeForPresentation(type, false),
                    context,
                    false).toString()
        } else if (type.isJavaScript && type is JSEvaluableOnlyType) {
            null
        } else {
            type = expandTypeForPresentation(type, false)
            type.getTypeText(TypeTextFormat.PRESENTABLE)
        }
    }

    protected fun getTypeWithAppliedSubstitutor(rawType: JSType?, substitutor: JSTypeSubstitutor): JSType? {
        return JSTypeUtils.applyGenericArguments(JSTypeUtils.applyCompositeMapping(rawType) { el: JSType? ->
            GenericArrayBuilder.asArrayIfGenericType(JSCompositeTypeImpl.optimizeTypeIfComposite(el))
        }, substitutor)
    }

    protected fun appendFunctionAttributes(functionItem: JSFunctionItem, result: StringBuilder,
            shouldAppendKeyword: Boolean) {
        if (functionItem is JSFunction) {
            appendAttrList(functionItem, result)
            if (shouldAppendKeyword) {
                result.append(if (functionItem.kind == FunctionKind.DECORATOR) "decorator @" else "function ")
            }
        } else if (shouldAppendKeyword) {
            result.append("function ")
        }
    }

    protected fun shouldAppendFunctionKeyword(function: JSFunctionItem, parent: PsiElement?): Boolean {
        return if (JSPsiImplUtils.isGetterOrSetter(function)) {
            false
        } else if (function !is JSFunctionProperty && function !is TypeScriptFunctionSignature
                && function !is TypeScriptFunctionType) {
            parent !is JSClass
        } else {
            false
        }
    }

    protected fun appendAttrList(owner: JSAttributeListOwner, result: StringBuilder) {
        val attributeList = owner.attributeList
        if (attributeList != null) {
            val type = attributeList.accessType
            var visibilityOrExportPart: String? = null
            val jsClass = JSUtils.getMemberContainingClass(owner)
            if (jsClass != null) {
                visibilityOrExportPart = formatVisibility(owner, attributeList, type)
            } else if (owner is JSQualifiedNamedElement) {
                val exportScope = ES6PsiUtil.getExportScope(owner)
                if (exportScope is TypeScriptModule || ES6PsiUtil.isExternalModule(exportScope)) {
                    if ((owner as JSQualifiedNamedElement).isExported) {
                        visibilityOrExportPart = "export"
                    } else if (ES6ImportHandler.isExportedWithDefault(owner)) {
                        visibilityOrExportPart = "export default"
                    }
                }
            }
            if (visibilityOrExportPart != null) {
                result.append(visibilityOrExportPart)
                result.append(" ")
            }
            appendPlainModifierList(attributeList, result)
        }
    }

    protected fun appendPlainModifierList(attributeList: JSAttributeList, result: StringBuilder) {
        appendModifierWithSpace(result, attributeList, JSAttributeList.ModifierType.ASYNC)
        appendModifierWithSpace(result, attributeList, JSAttributeList.ModifierType.STATIC)
        appendModifierWithSpace(result, attributeList, JSAttributeList.ModifierType.GENERATOR)
    }

    protected fun getVariableOrFieldType(variable: JSTypeDeclarationOwner): JSType? {
        return JSShowTypeInfoAction.getTypeForDocumentation(variable)
    }

    protected fun appendType(context: PsiElement, typeText: String?, builder: StringBuilder) {
        if (!StringUtil.isEmpty(typeText)) {
            builder.append(JSSymbolPresentationProvider.getDefaultTypeSeparator(context)).append(typeText)
        }
    }

    protected fun getClassQualifiedName(jsClass: JSClass, packageOrModule: String?): String? {
        return if (!StringUtil.isEmpty(packageOrModule)) packageOrModule + "." + jsClass.name else jsClass.name
    }

    protected val isIncludeObjectInExtendsList: Boolean
        get() {
            return false
        }

    protected fun createQuickNavigateForJSElement(element: JSElement, originalElement: PsiElement, declaration: String,
            kind: ObjectKind, jsDoc: Boolean): String? {
        val qName = if (element is JSQualifiedNamedElement) element.qualifiedName else element.name
        return if (StringUtil.isEmpty(qName)) {
            null
        } else {
            val attributesAndDeclaration = StringBuilder()
            if (element is JSAttributeListOwner) {
                appendAttrList(element, attributesAndDeclaration)
            }
            attributesAndDeclaration.append(declaration)
            var result = buildHtmlForJSElement(element, attributesAndDeclaration, qName!!, declaration.isNotEmpty())
            val declared = getJSElementType(element, originalElement)
            if (declared != null) {
                val substitutor = getTypeSubstitutor(element, originalElement)
                val narrowedType = getNarrowedType(originalElement, substitutor)
                val builder = java.lang.StringBuilder()
                val type = this.appendOptionality(element, declared, narrowedType, builder, originalElement)
                this.appendTypeWithSeparatorForOwner(element, type, substitutor, builder, originalElement, true)
                result += builder
            }
            if (jsDoc) result else buildResult(kind, result, element, originalElement)
        }
    }

    protected fun getJSElementType(element: JSElement, originalElement: PsiElement): JSType? {
        return if (element is JSTypeOwner) {
            (element as JSTypeOwner).jsType
        } else {
            if (element is ES6ImportedBinding) {
                val results = element.multiResolve(false)
                if (results.size == 1) {
                    val el = results[0].element
                    if (el is JSExpression) {
                        return JSResolveUtil.getElementJSType(el)
                    }
                }
            }
            null
        }
    }

    private class JSOptionalityEvaluator : JSRecursiveNodeVisitor() {

        val myOptionalCandidates: MutableSet<String> = HashSet()

        override fun visitBinaryExpression(node: ASTNode) {
            var child = node.findChildByType(JSTokenTypes.OROR)
            if (child != null) {
                addRef(node)
            } else {
                child = node.findChildByType(JSTokenTypes.ANDAND)
                if (child != null) {
                    addRef(node)
                } else {
                    val childByType = node.findChildByType(JSTokenTypes.EQUALITY_OPERATIONS)
                    if (childByType != null) {
                        val identifier = node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET, childByType)
                        if (identifier != null) {
                            if (identifier.findChildByType(JSTokenTypes.UNDEFINED_KEYWORD) != null) {
                                addRef(node)
                            } else {
                                val firstIdentifier = node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET)
                                if (firstIdentifier?.findChildByType(JSTokenTypes.UNDEFINED_KEYWORD) != null) {
                                    addExpr(identifier)
                                }
                            }
                        }
                    }
                }
            }
            super.visitBinaryExpression(node)
        }

        override fun visitPrefixExpression(node: ASTNode) {
            var child = node.findChildByType(JSTokenTypes.TYPEOF_KEYWORD)
            if (child == null) {
                child = node.findChildByType(JSTokenTypes.EXCL)
            }
            if (child != null) {
                addRef(node)
            }
            super.visitPrefixExpression(node)
        }

        override fun visitIfStatement(node: ASTNode) {
            addRef(node)
            super.visitIfStatement(node)
        }

        override fun visitProperty(node: ASTNode) {
            val name = JSPropertyImpl.findNameIdentifier(node)
            if (name != null) {
                val expr = node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET, name.treeNext)
                if (expr != null && expr !== name) {
                    addExpr(expr)
                }
            }
            super.visitProperty(node)
        }

        override fun visitArgumentList(node: ASTNode) {
            var treeNext: ASTNode
            var expr = node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET)
            while (expr != null) {
                addExpr(expr)
                treeNext = expr.treeNext
                expr = if (treeNext != null) {
                    node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET, treeNext)
                } else {
                    null
                }
            }
            super.visitArgumentList(node)
        }

        private fun addRef(node: ASTNode) {
            val firstExpr = node.findChildByType(JSVariableBaseImpl.IDENTIFIER_TOKENS_SET)
            addExpr(firstExpr)
            if (firstExpr != null && node.elementType === JSElementTypes.BINARY_EXPRESSION) {
                val treeParent = node.treeParent
                if (treeParent.elementType === JSElementTypes.BINARY_EXPRESSION) {
                    val treeNext = firstExpr.treeNext
                    if (treeNext != null) {
                        addExpr(node.findChildByType(JSExtendedLanguagesTokenSetProvider.EXPRESSIONS, treeNext))
                    }
                }
            }
        }

        private fun addExpr(expr: ASTNode?) {
            if (expr != null && expr.elementType === JSElementTypes.REFERENCE_EXPRESSION
                    && JSReferenceExpressionImpl.getQualifierNode(expr) == null) {
                val node = expr.firstChildNode ?: return
                if (node.treeNext == null) {
                    val ref = node.text
                    myOptionalCandidates.add(ref)
                }
            }
        }

        override fun visitAsFunction(function: ASTNode?): Boolean {
            return true
        }

        override fun visitDocComment(node: ASTNode?) {}

    }

    enum class ObjectKind {
        SIMPLE_DECLARATION {

            override fun toPrefix(): String {
                return ""
            }

        },
        PROPERTY {

            override fun toPrefix(): String {
                return "(property) "
            }

        },
        PARAMETER {

            override fun toPrefix(): String {
                return "(parameter) "
            }

        },
        FUNCTION {

            override fun toPrefix(): String {
                return "(function) "
            }

        },
        METHOD {

            override fun toPrefix(): String {
                return "(method) "
            }

        },
        IMPORT_SPECIFIER {

            override fun toPrefix(): String {
                return "(named import) "
            }

            override fun toJSDocPrefix(): String {
                return "import "
            }

        },
        IMPORT_DEFAULT {

            override fun toPrefix(): String {
                return "(default import) "
            }

            override fun toJSDocPrefix(): String {
                return "import "
            }

        },
        IMPORT_ALL {

            override fun toPrefix(): String {
                return "(namespace import) "
            }

        },
        EXPORT {

            override fun toPrefix(): String {
                return "(export element) "
            }

        };

        abstract fun toPrefix(): String

        open fun toJSDocPrefix(): String {
            return "(property) "
        }
    }

    private enum class FunctionCallType {

        TYPESCRIPT,

        JAVASCRIPT,

        JS

    }

    companion object {

        const val TIMEOUT_MILLIS = 500

        private fun getServiceResult(element: PsiElement, originalElement: PsiElement): String? {
            val future = BetterTSServiceQuickInfoParser.requestServiceQuickInfo(element, originalElement)
            return JSLanguageServiceUtil.awaitFuture(future, 500L)
        }

        private fun isWhitelisted(originalElement: PsiElement): Boolean {
            return originalElement is JSThisExpression || originalElement is TypeScriptThisType
        }

        private fun restoreHolders(info: BetterTSServiceQuickInfoParser.ParsedInfo,
                highlighting: CharSequence): CharSequence {
            var placeholder: TextPlaceholder
            var realHighlighting = highlighting
            if (info.myPlaceholders.isNotEmpty()) {
                val var2 = info.myPlaceholders.iterator()
                while (var2.hasNext()) {
                    placeholder = var2.next()
                    realHighlighting = placeholder.restoreText(realHighlighting)!!
                }
            }
            return realHighlighting
        }

        private fun parseFunctionText(text: String, originalElement: PsiElement): JSFunction? {
            return try {
                val prefix = "function test"
                val suffix = " {}"
                val candidate = JSPsiElementFactory.createJSExpression(prefix + text + suffix, originalElement)
                if (!PsiUtilCore.hasErrorElementChild(candidate) && candidate is JSFunction
                        && candidate.getTextLength() == prefix.length + suffix.length + text.length) {
                    candidate
                } else {
                    null
                }
            } catch (var5: ProcessCanceledException) {
                throw var5
            } catch (var6: Exception) {
                Logger.getInstance(BetterTSQuickNavigateBuilder::class.java).warn(var6)
                null
            }
        }

        private fun getParsedServiceInfo(typeOwner: JSElement,
                originalElement: PsiElement): BetterTSServiceQuickInfoParser.ParsedInfo? {
            val serviceResult = getServiceResult(typeOwner, originalElement)
            return if (serviceResult == null) {
                null
            } else {
                BetterTSServiceQuickInfoParser.parseServiceTextAsInfo(serviceResult)
            }
        }

        private fun isElementFromTSSources(member: JSElement, originalElement: PsiElement): Boolean {
            return originalElement is JSReferenceExpression
                    && java.lang.Boolean.TRUE == TypeScriptDeclarationMappings.SOURCE_FILE_MARKER[member]
        }

        private fun getOriginalResolvedElement(member: JSElement, originalElement: JSReferenceExpression): JSElement {
            val results = originalElement.multiResolve(false)
            val resolvedElements = JSResolveResult.toElements(results)

            return if (resolvedElements.size != 1) {
                member
            } else {
                val candidate = ContainerUtil.getFirstItem(resolvedElements)
                if (candidate is JSElement) {
                    candidate
                } else {
                    member
                }
            }
        }

        protected fun getGenerics(element: JSElement, substitutor: JSTypeSubstitutor): String? {
            return if (element is TypeScriptTypeParameterListOwner) {
                val list = element.typeParameterList
                if (list == null) {
                    null
                } else {
                    val parameters = list.typeParameters
                    if (parameters.isEmpty()) {
                        null
                    } else {
                        val newBuilder = java.lang.StringBuilder()
                        newBuilder.append("<")
                        newBuilder.append(StringUtil.join(parameters, { el ->
                            var name = el.name
                            if (name == null) {
                                name = "?"
                            }
                            val id = el.genericId
                            val type = substitutor[id]
                            type?.getTypeText(TypeTextFormat.PRESENTABLE) ?: name
                        }, ","))
                        newBuilder.append(">")
                        newBuilder.toString()
                    }
                }
            } else {
                null
            }
        }

        private fun buildHtmlForProperty(element: JSProperty, qName: String, typePart: CharSequence): String? {
            return buildHtmlForVariableOrField(element, "", qName, false, typePart)
        }

        protected fun isFileNameRequired(element: PsiElement, originalElement: PsiElement): Boolean {
            val elementFile = PsiUtilCore.getVirtualFile(element)
            val originalFile = PsiUtilCore.getVirtualFile(originalElement)
            return elementFile == null || elementFile != originalFile
        }

        fun buildHtmlForFunction(context: PsiElement, modifiers: CharSequence, qName: CharSequence,
                hasFunctionKeyword: Boolean, parametersWithReturnType: CharSequence): String {
            val prefix = if (hasFunctionKeyword) "" else "class Foo { "
            val finalText = "$prefix$modifiers$\$Name$$$parametersWithReturnType"
            val beforeNameLength = prefix.length + modifiers.length
            val toHighlight = TextRange(beforeNameLength, beforeNameLength + "$\$Name$$".length)
            return getQuickNavigateHtmlHighlighting(context, qName, prefix, finalText, toHighlight)
        }

        protected fun buildResult(kind: ObjectKind, objectText: String, element: PsiElement,
                originalElement: PsiElement): String {
            val needFileName = isFileNameRequired(element, originalElement)
            return if (!needFileName) {
                kind.toPrefix() + objectText
            } else {
                val psiFile = element.containingFile
                val fileNameText = "(" + psiFile.name + ")"
                val kindText = kind.toPrefix()
                kindText + objectText + buildAdditionalInformationText(fileNameText)
            }
        }

        private fun formatMemberAccess(parentQualifier: String?, unescapedName: String, separator: String): String {
            var qName = ""
            if (!StringUtil.isEmpty(parentQualifier)) {
                qName = parentQualifier!!
                if (!StringUtil.startsWithChar(unescapedName, '[')) {
                    qName = parentQualifier + separator
                }
            }

            qName += XmlStringUtil.escapeString(unescapedName)

            return qName
        }

        fun buildHtmlForVariableOrField(owner: PsiElement, modifiers: CharSequence, escapedName: CharSequence,
                hasVarPrefix: Boolean, typeAndInitializer: CharSequence): String {
            val prefix = if (hasVarPrefix) "" else if (owner is JSParameter) "function f( " else "class { "
            val finalText = "$prefix$modifiers$\$Name$$$typeAndInitializer"
            val modifiersWithPrefixLength = modifiers.length + prefix.length
            val toHighlight = TextRange(modifiersWithPrefixLength, modifiersWithPrefixLength + "$\$Name$$".length)
            return getQuickNavigateHtmlHighlighting(owner, escapedName, prefix, finalText, toHighlight)
        }

        fun getQuickNavigateHtmlHighlighting(owner: PsiElement, escapedName: CharSequence, prefixToExclude: String,
                finalText: String, toHighlight: TextRange?): String {
            var result = if (ApplicationManager.getApplication().isUnitTestMode) {
                null
            } else {
                BetterTSHtmlHighlightingUtil.tryGetHtmlHighlighting(owner, finalText, toHighlight,
                        prefixToExclude.length,
                        finalText.length)
            }
            result = result?.toString()?.replace("font-style:italic;", "")
                    ?: XmlStringUtil.escapeString(finalText.substring(prefixToExclude.length), false)

            return result.toString().replace("$\$Name$$", escapedName.toString())
        }

        private fun buildAdditionalInformationText(text: String): String {
            return if (StringUtil.isEmpty(text)) {
                ""
            } else {
                val color = CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.defaultAttributes.foregroundColor
                val result = java.lang.StringBuilder()
                result.append(" <span")
                result.append(" style=\"")
                val font = StartupUiUtil.getLabelFont()
                if (!ApplicationManager.getApplication().isUnitTestMode) {
                    if (font != null) {
                        result.append("font-size:").append(font.size).append("pt").append(";")
                    }
                    if (color != null) {
                        result.append("color:").append(ColorUtil.toHex(color)).append(";")
                    }
                }
                result.append("\">")
                result.append(text)
                result.append("</span>")
                result.toString()
            }
        }

        private fun buildHtmlForClass(jsClass: JSClass, attributesAndKeyword: CharSequence, qName: String,
                extendsImplements: CharSequence): String {
            val finalText = "$attributesAndKeyword$\$Name$$$extendsImplements"
            val range = TextRange(attributesAndKeyword.length, attributesAndKeyword.length + "$\$Name$$".length)
            return getQuickNavigateHtmlHighlighting(jsClass, qName, "", finalText, range)
        }

        private fun checkAndGetXmlAttributeQuickNavigate(element: PsiElement): String? {
            var xmlAttributeDoc: String? = null
            val xmlAttribute = if (element.parent is XmlAttribute) {
                element.parent
            } else if (element.parent is XmlAttributeValue && element.parent.parent is XmlAttribute) {
                element.parent.parent
            } else {
                null
            }
            if (xmlAttribute != null) {
                val value = StringUtil.unquoteString(StringUtil.notNullize((xmlAttribute as XmlAttribute).value))
                xmlAttributeDoc = "${xmlAttribute.name} $value"
            }

            return xmlAttributeDoc
        }

        private fun expandTypeForPresentation(type: JSType, nested: Boolean): JSType {
            return if (type is JSEvaluableType || type is JSUnionOrIntersectionType || type is JSGenericTypeImpl
                    || (type is JSTypeImpl && type.jsTypedef is JSEvaluableType)) {
                if (nested) {
                    type.substitute()
                } else {
                    simplifyGenericArguments(type.substitute())
                }
            } else {
                type
            }
        }

        private fun simplifyGenericArguments(type: JSType): JSType {
            return if (type is JSGenericTypeImpl) {
                val nested = ContainerUtil.map(type.arguments) { el ->
                    expandTypeForPresentation(el, true)
                }
                JSGenericTypeImpl(type.getSource(), type.type, nested)
            } else {
                type
            }
        }

        private fun generateReferenceTargetList(implementsList: JSReferenceList?, packageName: String): String? {
            return if (implementsList == null) {
                null
            } else {
                var result: StringBuilder? = null
                val referenceExpressionTexts = implementsList.referenceTexts
                val var5 = referenceExpressionTexts.size
                for (var6 in 0 until var5) {
                    var refExprText = referenceExpressionTexts[var6]
                    refExprText = JSImportHandlingUtil.resolveTypeName(refExprText, implementsList)
                    if (result == null) {
                        result = StringBuilder()
                    } else {
                        result.append(", ")
                    }
                    refExprText = StringUtil.notNullize(refExprText)
                    val referencedPackageName = StringUtil.getPackageName(refExprText)
                    result.append(if (referencedPackageName == packageName) {
                        refExprText.substring(refExprText.lastIndexOf(46.toChar()) + 1)
                    } else {
                        refExprText
                    })
                }
                result?.toString()
            }
        }

        protected fun appendModifierWithSpace(result: StringBuilder, attributeList: JSAttributeList,
                modifier: JSAttributeList.ModifierType) {
            if (attributeList.hasModifier(modifier)) {
                result.append(modifier.keyword).append(" ")
            }
        }

        private fun buildHtmlForJSElement(element: JSElement, prefix: CharSequence, qName: String,
                hasDeclaration: Boolean): String {
            return if (hasDeclaration) {
                val finalText = "$prefix$\$Name$$"
                val range = TextRange(prefix.length, prefix.length + "$\$Name$$".length)
                getQuickNavigateHtmlHighlighting(element, qName, "", finalText, range)
            } else {
                "" + prefix + buildHtmlForName(element, qName)
            }
        }

        private fun buildHtmlForName(element: JSElement, name: String): String {
            val result = if (ApplicationManager.getApplication().isUnitTestMode) {
                null
            } else BetterTSHtmlHighlightingUtil.tryGetHtmlHighlightingForName(element, name)
            return result ?: XmlStringUtil.escapeString(name, false)
        }

        @Contract("!null->!null")
        fun getOriginalElementOrParentIfLeaf(originalElement: PsiElement?): PsiElement? {
            if (originalElement is LeafPsiElement) {
                val file = originalElement.getContainingFile()
                val offset = originalElement.startOffset
                var candidate = originalElement
                var node = originalElement.getNode()
                if (offset > 0 && !isIdentifier(node!!)) {
                    candidate = file.findElementAt(offset - 1)
                    if (candidate != null) {
                        node = candidate.node
                    }
                }
                if (candidate != null && isIdentifier(node!!)) {
                    return candidate.parent
                }
            }

            return originalElement
        }

        private fun isIdentifier(node: ASTNode): Boolean {
            return JSKeywordSets.IDENTIFIER_NAMES.contains(node.elementType)
        }

    }

}