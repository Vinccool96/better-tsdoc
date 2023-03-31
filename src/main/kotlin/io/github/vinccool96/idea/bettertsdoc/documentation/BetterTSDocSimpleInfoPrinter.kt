package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.documentation.JSDocumentationProvider
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.documentation.JSHtmlHighlightingUtil
import com.intellij.lang.javascript.index.JSItemPresentation
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.JSType.TypeTextFormat
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSPackageStatement
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.JSTypeImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import java.util.function.Consumer

open class BetterTSDocSimpleInfoPrinter<T : BetterTSDocBuilderSimpleInfo>(builder: T, element: PsiElement,
        protected val contextElement: PsiElement?, canBeNamed: Boolean) {

    protected val namedItem = if (element is JSPsiNamedElementBase && canBeNamed) element else null

    protected val myElement: PsiElement = element

    protected val myBuilder: T = builder

    init {
        if (namedItem != null) {
            val parent = element.parent
            builder.namespace = if (element is JSClass && parent is JSPackageStatement) parent.qualifiedName else null
        }
    }

    open fun appendDoc(result: StringBuilder, provider: BetterTSDocumentationProvider) {
        this.appendDefinitionDoc(result, provider)
        val hasDefinition = result.isNotEmpty()
        if (!this.appendMdnDoc(result, hasDefinition)) {
            this.appendDescriptionsAndSections(result, provider, hasDefinition)
        }
    }

    @Nls
    fun getRenderedDoc(provider: BetterTSDocumentationProvider): String? {
        val result = StringBuilder()
        if (!this.appendMdnDoc(result, false)) {
            this.appendDescriptionsAndSections(result, provider, false)
        }

        return result.toString()
    }

    private fun appendDescriptionsAndSections(result: StringBuilder, provider: BetterTSDocumentationProvider,
            hasDefinition: Boolean) {
        appendDescriptionContent(this, result)
        result.append("<table class='sections'>")
        this.appendInnerSections(result, provider, hasDefinition)
        result.append("</table>")
    }

    protected fun appendMdnDoc(result: StringBuilder, hasDefinition: Boolean): Boolean {
        val documentation = JSDocumentationUtils.getJsMdnDocumentation(myElement, contextElement)
        return if (documentation == null) {
            false
        } else {
            result.append(documentation.getDocumentation(false,
                    Consumer { builder: StringBuilder ->
                        if (hasDefinition) {
                            this.appendLocation(builder)
                        }
                    }))
            true
        }
    }

    protected open fun appendInnerSections(result: StringBuilder, provider: BetterTSDocumentationProvider,
            hasDefinition: Boolean) {
        this.appendBodyDoc(result, hasDefinition)
        addSections(myBuilder.myUnknownTags, result)
        if (hasDefinition) {
            this.appendLocation(result)
        }
    }

    protected fun appendLocation(result: StringBuilder) {
        if (!DialectDetector.isActionScript(myElement) && myElement is JSElement) {
            val file = myElement.getContainingFile()
            if (file != null) {
                var rawLocation = JSItemPresentation.getFileName(file, true, true, false)
                rawLocation = StringUtil.trimEnd(StringUtil.trimStart(rawLocation, "("), ")")
                val location = getLocationWithEllipsis(rawLocation, PATH_LENGTH)
                if (!location.isEmpty()) {
                    val sections = result.indexOf("<tr><td valign='top' class='section'><p>")
                    result.append("<tr><td valign='top'")
                    if (sections > 0) {
                        result.append(" colspan='2'")
                    }
                    result.append(">")
                    val icon = getFileIconPath(file)
                    result.append("<icon src='").append(icon).append("'/>&nbsp;")
                            .append(StringUtil.escapeXmlEntities(location))
                    result.append("</td>")
                }
            }
        }
    }

    protected open fun appendPropertiesAndDefaultValue(result: StringBuilder) {}

    protected open fun appendBodyDoc(result: StringBuilder, hasDefinition: Boolean) {
        if (hasDefinition) {
            appendPropertiesAndDefaultValue(result)
            var text: String?
            if (myElement is JSConstantValueOwner) {
                text = (myElement as JSConstantValueOwner).constantValueDescription
                if (text != null) {
                    startNamedSection("Constant value:", result)
                    result.append("<td valign='top'>")
                    appendSingleNamedDescriptionSection(text, "", result)
                    result.append("</td>")
                }
            }
            if (myElement is TypeScriptTypeAlias) {
                val declaration = myElement.parsedTypeDeclaration
                if (declaration != null) {
                    startNamedSection("Alias for:", result)
                    result.append("<td valign='top'>")
                    val text = JSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(declaration, myElement, false)
                    appendSingleNamedDescriptionSection(text, "", result)
                    result.append("</td>")
                    val expanded = JSTypeUtils.unwrapType(declaration.substitute())
                    if (!expanded.isEquivalentTo(myBuilder.jsType, null)) {
                        val expandedCodeText = expanded.getTypeText(TypeTextFormat.CODE)
                        if (expandedCodeText != declaration.getTypeText(TypeTextFormat.CODE)) {
                            startNamedSection("Initial type:", result)
                            result.append("<td valign='top'>")
                            val initialType =
                                    JSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(expanded, myElement, false)
                            appendSingleNamedDescriptionSection(initialType, "", result)
                            result.append("</td>")
                        }
                        val completelyExpanded = JSTypeWithIncompleteSubstitution.substituteCompletely(expanded)
                        if (!completelyExpanded.isEquivalentTo(expanded, null)) {
                            startNamedSection("Expanded:", result)
                            result.append("<td valign='top'>")
                            val recordText = JSHtmlHighlightingUtil.getTypeWithLinksHtmlHighlighting(completelyExpanded,
                                    myElement, false)
                            appendSingleNamedDescriptionSection(recordText, "", result)
                            result.append("</td>")
                        }
                    }
                }
            }
            if (contextElement != null && contextElement !is JSImplicitElement) {
                text = contextElement.text
                if (text.startsWith("#") && text.length == 7) {
                    result.append(buildCurrentOrDefaultValue(text.substring(1), true, false, text, true))
                }
            }
        }
    }

    protected open fun appendDefinitionDoc(result: StringBuilder, provider: BetterTSDocumentationProvider) {
        if (namedItem != null) {
            result.append("<div class='definition'><pre>")
            val name = if (myElement is JSAttributeNameValuePair) {
                myElement.simpleValue
            } else {
                BetterTSDocumentationBuilder.getNameForDocumentation(namedItem)
            }
            var text: String?
            if (namedItem !is JSAttributeNameValuePair && namedItem !is JSImplicitElement && namedItem !is JSDefinitionExpression && !DialectDetector.isActionScript(
                            namedItem)) {
                val builder = provider.getQuickNavigateBuilder()
                val element = contextElement ?: namedItem
                text = builder.getQuickNavigateInfoForNavigationElement(namedItem, element, true)
                if (text == null) {
                    text = StringUtil.notNullize(name)
                }
                result.append(text)
            } else {
                var jsType: JSType? = if (myBuilder.jsType != null) myBuilder.jsType!!.substitute() else null
                var nameToUse = StringUtil.notNullize(name)
                if (myElement is JSImplicitElement) {
                    if (myElement is JSOptionalOwner && (myElement as JSOptionalOwner).isOptional) {
                        nameToUse = "$nameToUse?"
                    }
                    if (jsType is JSTypeImpl) {
                        text = jsType.getResolvedTypeText()
                        val names = StringUtil.split(text, ".")
                        if (!ContainerUtil.and(names) { el -> StringUtil.isJavaIdentifier(el!!) }) {
                            jsType = null
                        }
                    }
                }
                result.append(JSHtmlHighlightingUtil.getElementHtmlHighlighting(myElement, nameToUse, jsType))
            }
            result.append("</pre></div>")
        }
    }

    companion object {

        private const val PATH_LENGTH = 40

        fun getLocationWithEllipsis(filePath: String, maxLength: Int): String {
            val fileName = VfsUtil.extractFileName(filePath) ?: return filePath

            return if (fileName.length + 4 >= maxLength) {
                fileName
            } else if (maxLength >= filePath.length) {
                filePath
            } else {
                val acceptableSymbolCount = maxLength - fileName.length - 4
                var leftPartEnd = filePath.indexOf("/")
                if (leftPartEnd == 0) {
                    leftPartEnd = filePath.indexOf("/", 1)
                }

                val startPartSymbolCount = leftPartEnd + 1
                if (startPartSymbolCount > acceptableSymbolCount) {
                    fileName
                } else {
                    val lastIndex = filePath.lastIndexOf("/")
                    if (leftPartEnd >= lastIndex) {
                        fileName
                    } else {
                        val result = StringBuilder()
                        result.append(filePath, 0, leftPartEnd + 1).append(".../")
                        appendMaxRightPath(filePath.substring(leftPartEnd + 1, lastIndex + 1), result,
                                acceptableSymbolCount - startPartSymbolCount)
                        result.append(fileName)
                        result.toString()
                    }
                }
            }
        }

        private fun appendMaxRightPath(subPathWithoutFilename: String, result: StringBuilder, maxLength: Int) {
            if (maxLength > 0 && subPathWithoutFilename.isNotEmpty()) {
                val startOffset = subPathWithoutFilename.length - maxLength
                if (startOffset > 0 && subPathWithoutFilename[startOffset - 1] == '/') {
                    result.append(subPathWithoutFilename.substring(startOffset))
                } else if (startOffset > 0) {
                    val partOfPathToTrim = subPathWithoutFilename.substring(startOffset)
                    val toCropIndex = partOfPathToTrim.indexOf("/")
                    if (toCropIndex >= 0 && partOfPathToTrim.length > toCropIndex + 1) {
                        result.append(partOfPathToTrim.substring(toCropIndex + 1))
                    }
                }
            }
        }

        private fun getFileIconPath(file: PsiFile): String {
            return if (DialectDetector.isTypeScript(file)) {
                "JavaScriptPsiIcons.FileTypes.TypeScriptFile"
            } else {
                if (DialectDetector.isJavaScript(file)) "AllIcons.FileTypes.JavaScript" else "AllIcons.Nodes.Folder"
            }
        }

        protected fun addJSDocVisibilityAndAccess(generationInfo: BetterTSDocBuilderSimpleInfo,
                options: StringBuilder) {
            if (generationInfo.modifiers != null) {
                if (options.isNotEmpty()) {
                    options.append(", ")
                }
                options.append(generationInfo.modifiers)
            }

            if (generationInfo.finalAccess != null) {
                if (options.isNotEmpty()) {
                    options.append(", ")
                }
                options.append(generationInfo.finalAccess)
            }
        }

        fun <T : BetterTSDocBuilderSimpleInfo> appendDescriptionContent(info: BetterTSDocSimpleInfoPrinter<T>,
                result: StringBuilder) {
            if (info.myBuilder.hasDescription) {
                result.append("<div class='content'>")
                addDescription(info.myBuilder.finalDescription, result)
                result.append("</div>")
            }
        }

        fun addSections(sections: Map<String, String>, result: StringBuilder) {
            if (sections.isNotEmpty()) {
                for (entry in sections) {
                    startNamedSection(entry.key, result)
                    result.append("<td valign='top'>")
                    val value = entry.value
                    if (!value.startsWith("<p>")) {
                        result.append("<p>")
                    }

                    result.append(value)
                    result.append("</td>")
                }
            }
        }

        fun startNamedSection(sectionName: String?, result: StringBuilder) {
            result.append("<tr><td valign='top' class='section'><p>")
            result.append(sectionName)
            result.append("</td>")
        }

        fun appendSingleNamedDescriptionSection(name: CharSequence?, description: CharSequence,
                builder: StringBuilder) {
            builder.append(name)
            if (description.isNotEmpty()) {
                builder.append(" &ndash; ")
                builder.append(description)
            }
        }

        fun addDescription(description: String, builder: StringBuilder) {
            builder.append("<p>")
            builder.append(description)
        }

        protected fun buildCurrentOrDefaultValue(content: String, color: Boolean, isDefaultValue: Boolean,
                originalText: String?, needSection: Boolean): String {
            val realContent = if (color) {
                "<code style='background-color:#$content; color: #${contrastColor(content)}'>$originalText</code>"
            } else {
                "<code>$content</code>"
            }

            val label = (if (isDefaultValue) "Default" else "Value") + ":"
            return if (!needSection) {
                "<p>$label$realContent"
            } else {
                "<tr><td valign='top' class='section'><p>$label</td><td valign='top'><p>$realContent"
            }
        }

        private fun contrastColor(remainingLineContent: String): String {
            return try {
                val color = ColorUtil.fromHex(remainingLineContent)
                ColorUtil.toHex(if (ColorUtil.isDark(color)) JBColor.WHITE else JBColor.BLACK)
            } catch (_: IllegalArgumentException) {
                remainingLineContent
            }
        }

    }

}