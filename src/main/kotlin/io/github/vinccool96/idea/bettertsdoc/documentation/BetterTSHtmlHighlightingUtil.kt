package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.Language
import com.intellij.lang.javascript.DialectDetector
import com.intellij.lang.javascript.JSAnalysisHandlersFactory
import com.intellij.lang.javascript.JSLanguageDialect
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.documentation.JSExampleDocBuilderInfo
import com.intellij.lang.javascript.documentation.JSLinkTypeTextStringBuilder
import com.intellij.lang.javascript.highlighting.JSSemanticHighlightingVisitor
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSFunctionItem
import com.intellij.lang.javascript.psi.JSPresentableTypeTextStringBuilder
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.JSType.TypeTextFormat
import com.intellij.lang.javascript.psi.ecma6.JSTypeDeclaration
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.types.JSTypeImpl
import com.intellij.lang.javascript.validation.JSAnnotatingVisitor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.editor.richcopy.SyntaxInfoBuilder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.Contract
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

object BetterTSHtmlHighlightingUtil {

    private fun tryGetHtmlHighlighting(fakeFile: PsiFile, text: String, iterator: SyntaxInfoBuilder.RangeIterator?,
            startOffset: Int, endOffset: Int): String? {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return readHtmlText(HtmlSyntaxInfoUtil.getHtmlContent(fakeFile, text, iterator, scheme, startOffset, endOffset))
    }

    private fun tryGetHtmlHighlighting(text: String, iterator: SyntaxInfoBuilder.RangeIterator,
            endOffset: Int): String? {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return readHtmlText(HtmlSyntaxInfoUtil.getHtmlContent(text, iterator, scheme, endOffset))
    }

    private fun readHtmlText(text: CharSequence?): String? {
        return text?.toString()?.replace("font-weight:bold;", "")
    }

    private fun tryGetHtmlHighlighting(elementOrContext: PsiElement, fakeText: String, language: Language,
            identifierRange: TextRange?, startOffset: Int, endOffset: Int): CharSequence? {
        return if (!DumbService.isDumb(elementOrContext.project) && !StringUtil.isEmpty(fakeText)) {
            try {
                val fakeFile = PsiFileFactory.getInstance(elementOrContext.project)
                        .createFileFromText("dummy", language, fakeText, false, false)
                val holder: MutableList<RangeWithAttributes> = ArrayList()
                if (identifierRange != null) {
                    highlightName(elementOrContext, holder, identifierRange, language)
                }
                highlightSemanticKeywords(fakeFile, holder, language)
                val iterator = createAnnotationsRangeIterator(holder, startOffset, endOffset)
                tryGetHtmlHighlighting(fakeFile, fakeText, iterator, startOffset, endOffset)
            } catch (var9: ProcessCanceledException) {
                throw var9
            } catch (var10: Exception) {
                Logger.getInstance(BetterTSExampleDocBuilderInfo::class.java)
                        .error("Cannot process html highlighting for: $fakeText", var10)
                null
            }
        } else {
            null
        }
    }

    private fun highlightName(elementOrContext: PsiElement, holder: MutableList<RangeWithAttributes>,
            identifierRange: TextRange, language: Language) {
        var realElementOrContext = elementOrContext
        val highlighter = JSAnnotatingVisitor.getHighlighter(language)
        if (realElementOrContext is JSExpression) {
            val initializedElement = JSPsiImplUtils.getInitializedElement(realElementOrContext)
            if (initializedElement != null) {
                realElementOrContext = initializedElement
            }
        }

        val descriptor =
                JSSemanticHighlightingVisitor.buildHighlightForResolveResult(realElementOrContext, realElementOrContext)
        if (descriptor != null) {
            holder.add(RangeWithAttributes(identifierRange.startOffset, identifierRange.endOffset,
                    descriptor.getAttributesKey(highlighter).defaultAttributes))
        }
    }

    fun tryGetHtmlHighlighting(element: PsiElement, fakeText: String, identifierRange: TextRange?, startOffset: Int,
            endOffset: Int): CharSequence? {
        return tryGetHtmlHighlighting(element, fakeText, getLanguageForHighlighting(element), identifierRange,
                startOffset, endOffset)
    }

    private fun createAnnotationsRangeIterator(holder: List<RangeWithAttributes>, startOffset: Int,
            endOffset: Int): SyntaxInfoBuilder.RangeIterator? {
        return if (holder.isEmpty()) {
            null
        } else {
            val annotations: MutableList<RangeWithAttributes> =
                    holder.stream().sorted(Comparator.comparing { el -> el.startOffset })
                            .filter { el -> startOffset <= el.startOffset && el.endOffset <= endOffset }
                            .collect(Collectors.toList())
            object : SyntaxInfoBuilder.RangeIterator {
                var index = -1
                override fun atEnd(): Boolean {
                    return index >= annotations.size - 1
                }

                override fun advance() {
                    ++index
                }

                override fun getRangeStart(): Int {
                    val annotation = this.get()
                    return annotation?.startOffset ?: 0
                }

                override fun getRangeEnd(): Int {
                    val annotation = this.get()
                    return annotation?.endOffset ?: 0
                }

                override fun getTextAttributes(): TextAttributes? {
                    val annotation = this.get()
                    return annotation?.textAttributes
                }

                private fun get(): RangeWithAttributes? {
                    return if (annotations.size > index) annotations[index] else null
                }

                override fun dispose() {
                    annotations.clear()
                }
            }
        }
    }

    private fun highlightSemanticKeywords(fakeFile: PsiFile, holder: MutableList<RangeWithAttributes>,
            language: Language) {
        if (language is JSLanguageDialect) {
            val optionHolder = language.optionHolder
            val highlightInfoHolder = HighlightInfoHolder(fakeFile, *arrayOfNulls(0))
            val visitor = JSAnalysisHandlersFactory.forLanguage(language)
                    .createKeywordHighlighterVisitor(highlightInfoHolder, optionHolder)
            SyntaxTraverser.psiTraverser(fakeFile).traverse().forEach(Consumer { it: PsiElement ->
                it.accept(visitor)
            })
            for (i in 0 until highlightInfoHolder.size()) {
                val info = highlightInfoHolder[i]
                holder.add(RangeWithAttributes(info.getStartOffset(), info.getEndOffset(),
                        info.getTextAttributes(fakeFile, null)))
            }
        }
    }

    fun getTypeWithLinksPlaceholder(type: JSType?, hasFiredEvents: Boolean,
            placeHolderPrefix: String): TextPlaceholder {
        return if (type == null) {
            object : TextPlaceholder {
                override val holderText: String
                    get() {
                        return "any"
                    }

                override fun restoreText(text: CharSequence): CharSequence {
                    return text
                }
            }
        } else {
            val replacements: MutableMap<String, JSTypeImpl> = HashMap()
            val counter = AtomicInteger()
            val builder: JSPresentableTypeTextStringBuilder = object : JSPresentableTypeTextStringBuilder() {
                override fun startProcessType(type: JSType): Boolean {
                    return if (!checkLimit()) {
                        false
                    } else if (type is JSTypeImpl) {
                        val replacement = placeHolderPrefix + counter.incrementAndGet()
                        replacements[replacement] = type
                        this.append(replacement)
                        false
                    } else {
                        super.startProcessType(type)
                    }
                }
            }
            type.buildTypeText(TypeTextFormat.PRESENTABLE, builder)
            object : TextPlaceholder {
                override val holderText: String
                    get() {
                        return builder.result
                    }

                override fun restoreText(text: CharSequence): CharSequence {
                    var realText = text
                    var key: String
                    var value: JSTypeImpl
                    val var2 = replacements.entries.iterator()
                    while (var2.hasNext()) {
                        val (key1, value1) = var2.next()
                        key = key1
                        value = value1
                        realText = replaceSubSequence(realText, key, getSimpleTypeWithLinkText(value, hasFiredEvents))
                    }
                    return realText
                }
            }
        }
    }

    fun tryGetHtmlHighlightingForName(context: PsiElement, name: String): String? {
        val holder: MutableList<RangeWithAttributes> = ArrayList()
        val range = TextRange(0, name.length)
        highlightName(context, holder, range, getLanguageForHighlighting(context))
        val iterator = createAnnotationsRangeIterator(holder, 0, name.length)
        if (iterator != null) {
            try {
                return tryGetHtmlHighlighting(name, iterator, name.length)
            } catch (var6: ProcessCanceledException) {
                throw var6
            } catch (var7: java.lang.Exception) {
                Logger.getInstance(JSExampleDocBuilderInfo::class.java).error(
                        "Cannot process html highlighting for: $name", var7)
            }
        }

        return null
    }

    fun createSimpleHolder(originalText: String, holder: String): TextPlaceholder {
        return object : TextPlaceholder {
            override val holderText: CharSequence
                get() {
                    return holder
                }

            override fun restoreText(text: CharSequence): CharSequence {
                return replaceSubSequence(text, holder, originalText)
            }
        }
    }

    private fun replaceSubSequence(text: CharSequence, oldText: String, newText: String): CharSequence {
        var realText = text
        val index = StringUtil.indexOf(realText, oldText)
        if (index >= 0) {
            realText = StringUtil.replaceSubSequence(realText, index, index + oldText.length, newText)
        }

        return realText
    }

    fun getElementHtmlHighlighting(element: PsiElement, name: String, type: JSType?): CharSequence {
        var realElement = element
        if (!DumbService.isDumb(realElement.project) && StringUtil.isJavaIdentifier(name)) {
            val placeholder = getTypeWithLinksPlaceholder(type, false, "$\$Type$\$Unique")
            val prefix = "var "
            val fakeText = prefix + name + if (type == null) "" else ": " + placeholder.holderText
            val startOffset = prefix.length
            val endOffset = fakeText.length
            val range = TextRange.create(prefix.length, prefix.length + name.length)
            realElement = getPreferableContext(realElement, type)!!
            val result = tryGetHtmlHighlighting(realElement, fakeText, range, startOffset, endOffset)
            if (result != null) {
                val var10 = placeholder.restoreText(result)
                return var10!!
            }
        }

        return name + ": " + getSimpleTypeWithLinkText(type, false)
    }

    @Contract("!null,_->!null")
    private fun getPreferableContext(element: PsiElement?, type: JSType?): PsiElement? {
        var realElement = element
        if (type != null) {
            val sourceElement = type.sourceElement
            if (sourceElement != null && DialectDetector.isTypeScript(sourceElement)) {
                realElement = sourceElement
            }
        }
        return realElement
    }

    fun getFunctionHtmlHighlighting(functionItem: JSFunctionItem, escapedName: String, hasFunctionKeyword: Boolean,
            modifiers: CharSequence, argumentsAndReturn: CharSequence, holders: List<TextPlaceholder>): CharSequence {
        val prefix = if (hasFunctionKeyword) "" else "class Foo { "
        val fakeText = "$prefix$modifiers$\$Name$$$argumentsAndReturn"
        val startOffset = prefix.length
        val endOffset = fakeText.length
        val range = TextRange.create(prefix.length + modifiers.length,
                modifiers.length + prefix.length + "$\$Name$$".length)
        var result = tryGetHtmlHighlighting(functionItem, fakeText, range, startOffset, endOffset)
        result = if (result != null) {
            replaceSubSequence(result, "$\$Name$$", escapedName)
        } else {
            "" + modifiers + escapedName + argumentsAndReturn
        }

        var holder: TextPlaceholder
        val var12 = holders.iterator()
        while (var12.hasNext()) {
            holder = var12.next()
            result = holder.restoreText(result!!)
        }

        return result!!
    }

    fun parseType(typeText: String, context: PsiElement): JSType? {
        val typePsi = JSChangeUtil.tryCreateTypeElement(typeText, context)
        return if (typePsi is JSTypeDeclaration && !PsiUtilCore.hasErrorElementChild(typePsi)
                && typeText.length == typePsi.getTextLength()) {
            typePsi.jsType
        } else {
            null
        }
    }

    fun getTypeWithLinksHtmlHighlighting(type: JSType?, context: PsiElement?, hasFiredEvents: Boolean): CharSequence {
        val realContext = getPreferableContext(context, type)
        return if (realContext == null) {
            getSimpleTypeWithLinkText(type, hasFiredEvents)
        } else {
            val placeholder = getTypeWithLinksPlaceholder(type, false, "$\$Type$\$Unique")
            val prefix = "var $\$Name$$: "
            val fakeText = prefix + placeholder.holderText
            val startOffset = prefix.length
            val endOffset = fakeText.length
            val result = tryGetHtmlHighlighting(realContext, fakeText, null, startOffset, endOffset)
            if (result != null) {
                placeholder.restoreText(result)!!
            } else {
                getSimpleTypeWithLinkText(type, hasFiredEvents)
            }
        }
    }

    fun getSimpleTypeWithLinkText(type: JSType?, hasFiredEvents: Boolean): String {
        return if (type == null) {
            "any"
        } else {
            val builder = JSLinkTypeTextStringBuilder(hasFiredEvents)
            type.buildTypeText(TypeTextFormat.PRESENTABLE, builder)
            builder.result
        }
    }

    private fun getLanguageForHighlighting(context: PsiElement): Language {
        return if (DialectDetector.isJavaScript(context)) {
            JavaScriptSupportLoader.FLOW_JS
        } else {
            context.language
        }
    }

    class RangeWithAttributes(val startOffset: Int, val endOffset: Int, val textAttributes: TextAttributes?)

    interface TextPlaceholder {

        val holderText: CharSequence

        fun restoreText(text: CharSequence): CharSequence?

    }

}