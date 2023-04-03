package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import io.github.vinccool96.idea.bettertsdoc.documentation.BetterTSHtmlHighlightingUtil.TextPlaceholder
import java.util.concurrent.Future

object BetterTSServiceQuickInfoParser {

    private val KEYWORDS =
            ContainerUtil.immutableSet("var", "let", "const", "class", "enum", "function", "module", "namespace",
                    "export", "private", "public", "protected", "type", "interface", "static", "readonly", "async")

    fun requestServiceQuickInfo(element: PsiElement, originalElement: PsiElement): Future<String?>? {
        return if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
            null
        } else {
            val file = PsiUtilCore.getVirtualFile(originalElement)
            if (file == null) {
                null
            } else {
                val quickInfo = TypeScriptService.getForFile(element.project, file)
                quickInfo?.getQuickInfoAt(element, originalElement, file)
            }
        }
    }

    fun findGenericsEnd(rest: String): Int {
        var count = 1
        val array = rest.toCharArray()

        for (i in array.indices) {
            if (i != 0) {
                val c = array[i]
                if (c == '<') {
                    ++count
                }
                if (c == '>') {
                    --count
                }
                if (count == 0) {
                    return i + 1
                }
            }
        }

        return -1
    }

    fun parseServiceText(resolvedElement: PsiElement, originalText: String): String? {
        val info = parseServiceTextAsInfo(originalText)
        return if (info == null) StringUtil.escapeXmlEntities(originalText) else getQuickNavigate(resolvedElement, info)
    }

    fun parseServiceTextAsInfo(originalText: String): ParsedInfo? {
        return try {
            var processText = originalText
            var kind = ""
            var space: Int
            if (originalText.startsWith("(")) {
                space = originalText.indexOf(") ")
                if (space > 0) {
                    kind = originalText.substring(0, space + 2)
                    processText = originalText.substring(space + 2)
                }
            }
            space = processText.indexOf(" ")
            var lastKeywordStartOffset: Int
            lastKeywordStartOffset = 0
            while (space >= 0 && KEYWORDS.contains(processText.substring(lastKeywordStartOffset, space))) {
                lastKeywordStartOffset = space
                space = processText.indexOf(" ", space)
            }
            var keywords = ""
            if (lastKeywordStartOffset > 0) {
                keywords = processText.substring(0, lastKeywordStartOffset + 1)
                processText = processText.substring(lastKeywordStartOffset + 1)
            }
            if (processText.isEmpty()) {
                null
            } else {
                val qNameIndex = readQNameWithGenerics(processText)
                if (qNameIndex == 0) {
                    null
                } else {
                    var restPart = processText.substring(qNameIndex)
                    var postfix: String? = ""
                    val indexOfOverloads = restPart.indexOf("(+")
                    if (indexOfOverloads > 0) {
                        postfix = restPart.substring(indexOfOverloads)
                        restPart = restPart.substring(0, indexOfOverloads)
                    }
                    val qName = processText.substring(0, qNameIndex)
                    ParsedInfo(keywords, restPart, qName, kind, postfix!!)
                }
            }
        } catch (var11: RuntimeException) {
            if (var11 is ControlFlowException) {
                throw var11
            } else {
                Logger.getInstance(BetterTSServiceQuickInfoParser::class.java).warn(
                        "Cannot parse service text: $originalText", var11)
                null
            }
        }
    }

    private fun readQNameWithGenerics(processText: String): Int {
        return if (processText.isEmpty()) {
            0
        } else {
            var qNameIndex = 0
            val ch = processText[0]
            if (Character.isJavaIdentifierStart(ch)) {
                while (processText.length > qNameIndex
                        && (Character.isJavaIdentifierStart(processText[qNameIndex])
                                || processText[qNameIndex] == '<')) {
                    val before = qNameIndex
                    if (processText[qNameIndex] == '<') {
                        val end = findGenericsEnd(processText.substring(qNameIndex))
                        if (end <= 0 || processText.length <= qNameIndex + end) {
                            break
                        }
                        qNameIndex += end
                        if (processText[qNameIndex] == '.') {
                            ++qNameIndex
                        }
                    } else {
                        qNameIndex = readQualifiedName(processText, qNameIndex)
                    }
                    if (before == qNameIndex) {
                        break
                    }
                }
            }
            qNameIndex
        }
    }

    private fun readQualifiedName(processText: String, qNameIndex: Int): Int {
        var index = qNameIndex
        ++index
        while (processText.length > index && (Character.isJavaIdentifierPart(
                        processText[index]) || '.' == processText[index])) {
            ++index
        }
        return index
    }

    private fun getQuickNavigate(element: PsiElement, info: ParsedInfo): String {
        val name = StringUtil.escapeXmlEntities(info.myQName)
        return if (info.myKeywords.isNotEmpty()) {
            info.myKindPrefix + getQuickNavigateByKeyword(element, info.myRestPart, info.myKeywords, name) +
                    info.myOverloadInfo
        } else {
            val htmlText = when (info.objectKind) {
                BetterTSQuickNavigateBuilder.ObjectKind.FUNCTION, BetterTSQuickNavigateBuilder.ObjectKind.METHOD ->
                    BetterTSQuickNavigateBuilder.buildHtmlForFunction(element, "", name, false, info.myRestPart)

                else ->
                    BetterTSQuickNavigateBuilder.buildHtmlForVariableOrField(element, "", name, false, info.myRestPart)
            }
            info.myKindPrefix + htmlText + info.myOverloadInfo
        }
    }

    private fun toObjectKind(kindPrefix: String): BetterTSQuickNavigateBuilder.ObjectKind {
        val var1 = BetterTSQuickNavigateBuilder.ObjectKind.values()
        val var2 = var1.size

        for (var3 in 0 until var2) {
            val value = var1[var3]
            if (value.toPrefix() == kindPrefix) {
                return value
            }
        }

        return BetterTSQuickNavigateBuilder.ObjectKind.SIMPLE_DECLARATION
    }

    private fun getQuickNavigateByKeyword(element: PsiElement, restPart: String, keywords: String,
            qName: String): String {
        val finalText = "$keywords$\$Name$$$restPart"
        val nameRange = TextRange(keywords.length, keywords.length + "$\$Name$$".length)
        return BetterTSQuickNavigateBuilder.getQuickNavigateHtmlHighlighting(element, qName, "", finalText, nameRange)
    }

    class ParsedInfo(keywords: String, restPart: String, qName: String, kindPrefix: String, overloadInfo: String) {

        val myKeywords: String = keywords

        val myRestPart: String = restPart

        val myRestPartWithPlaceHolders: String = this.cropImportPart(this.parseRestPart())

        val myPlaceholders: MutableList<TextPlaceholder> = ArrayList()

        val myQName: String = qName

        val myKindPrefix: String = kindPrefix

        val myOverloadInfo: String = overloadInfo

        val objectKind: BetterTSQuickNavigateBuilder.ObjectKind
            get() {
                return toObjectKind(this.myKindPrefix)
            }

        private fun cropImportPart(restPart: String): String {
            return if (!this.myKindPrefix.contains("alias")) {
                restPart
            } else {
                val importPrefix = "\nimport "
                val lastIndex = restPart.lastIndexOf(importPrefix)
                if (lastIndex > 0) {
                    restPart.substring(0, lastIndex)
                } else {
                    restPart
                }
            }
        }

        private fun parseRestPart(): String {
            var currentString = myRestPart
            var threeDots = currentString.indexOf(PREFIX)
            val newRest = StringBuilder()
            var counter = 0

            while (threeDots > 0 && threeDots + PREFIX.length < currentString.length) {
                ProgressManager.checkCanceled()
                val afterPrefixIndex = threeDots + PREFIX.length
                val afterPrefix = currentString[afterPrefixIndex]
                var skipped: Int
                if (Character.isWhitespace(afterPrefix) && afterPrefixIndex + 1 < currentString.length) {
                    val nextChar = currentString[afterPrefixIndex + 1]
                    if (nextChar != '|' && nextChar != '&') {
                        if (!Character.isDigit(nextChar)) {
                            threeDots = currentString.indexOf(PREFIX, threeDots + PREFIX.length)
                        } else {
                            newRest.append(currentString, 0, threeDots)
                            skipped = skipMessageAndDigits(currentString, afterPrefixIndex + 1)
                            if (skipped <= 0) {
                                myPlaceholders.clear()
                                return myRestPart
                            }
                            val toReplace = currentString.substring(threeDots, skipped)
                            ++counter
                            val holderText = "$\$Type$\$Srv" + counter + "_" + this.hashCode()
                            val holder = BetterTSHtmlHighlightingUtil.createSimpleHolder(toReplace, holderText)
                            newRest.append(holderText)
                            myPlaceholders.add(holder)
                            currentString = currentString.substring(skipped)
                            threeDots = currentString.indexOf(PREFIX)
                        }
                    } else {
                        ++counter
                        this.addHolderForPrefix(currentString, newRest, threeDots, counter)
                        currentString = currentString.substring(threeDots + PREFIX.length)
                        threeDots = currentString.indexOf(PREFIX)
                    }
                } else {
                    val before = threeDots - 1
                    skipped = threeDots + PREFIX.length
                    if (before > 0 && skipped < currentString.length && currentString[before] == '<' && currentString[skipped] == '>') {
                        ++counter
                        this.addHolderForPrefix(currentString, newRest, threeDots, counter)
                        currentString = currentString.substring(skipped)
                        threeDots = currentString.indexOf(PREFIX)
                    } else {
                        threeDots = currentString.indexOf(PREFIX, threeDots + PREFIX.length)
                    }
                }
            }

            newRest.append(currentString)
            return newRest.toString()
        }

        private fun addHolderForPrefix(currentString: String, newRest: StringBuilder, offset: Int, counter: Int) {
            val holderText = "$\$Type$\$Srv" + counter + "_" + this.hashCode()
            val toReplace = currentString.substring(offset, offset + PREFIX.length)
            val holder = BetterTSHtmlHighlightingUtil.createSimpleHolder(toReplace, holderText)
            newRest.append(currentString, 0, offset)
            newRest.append(holder.holderText)
            myPlaceholders.add(holder)
        }

    }

    const val PREFIX = "..."

    private fun skipMessageAndDigits(restPart: String, index: Int): Int {
        var realIndex = index
        while (restPart.length > realIndex && Character.isDigit(restPart[realIndex])) {
            ++realIndex
        }

        return if (restPart.length <= realIndex) {
            -1
        } else {
            val space = restPart[realIndex]
            if (!Character.isWhitespace(space)) {
                -1
            } else {
                ++realIndex
                while (restPart.length > realIndex && Character.isLetter(restPart[realIndex])) {
                    ++realIndex
                }
                if (restPart.length <= realIndex) {
                    -1
                } else {
                    val next = restPart.substring(realIndex)
                    if (next.startsWith(" ...")) " ...".length + realIndex else -1
                }
            }
        }
    }

}