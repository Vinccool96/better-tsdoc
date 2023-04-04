package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.JSKeywordSets
import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor.MetaDocType
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.NonNls
import java.util.regex.Pattern

object BetterTSDocumentationUtils {

    val base = JSDocumentationUtils::class

    private const val NO_NAME_CHARS = ".~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}"

    private const val IDENTIFIER_NAME = "[\\p{L}_$][\\p{LD}_$]*"

    const val NAME = "[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+"

    private const val NAME_IN_TYPE = "[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}/]+"

    private const val DOUBLE_QUOTED_NAME = "\"(?:[^\"\\\\]|\\\\.)+\""

    private const val SINGLE_QUOTED_NAME = "'(?:[^'\\\\]|\\\\.)+'"

    private const val IMPORT_TYPE_NAME = "import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)"

    private const val SPECIAL_NAME_PREFIX = "(?:module:|event:|external:)"

    private const val NAME_OR_QUOTED =
            "(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')"

    private const val NAMEPATH_START =
            "import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+"

    private const val NAMEPATH_IN_TYPE_START =
            "(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}/]+"

    private const val NAMEPATH_PART =
            "(?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')"

    const val NAMEPATH_SEPARATORS = ".#~"

    const val NAMEPATH =
            "(?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?"

    private const val NAMEPATH_IN_TYPE =
            "(?:[.#~])?(?:(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}/]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?"

    private val NAMEPATH_START_PATTERN = Pattern.compile(NAMEPATH_START)

    private val NAMEPATH_PART_PATTERN = Pattern.compile(NAMEPATH_PART)

    val NAMEPATH_PATTERN = Pattern.compile(NAMEPATH)

    val NAMEPATH_IN_TYPE_PATTERN = Pattern.compile(NAMEPATH_IN_TYPE)

    private const val NAMEPATH_OPTIONAL =
            "(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)\\s*|(\\s.*)?)"

    private const val PARAM_FIELD = "((?:(?:\\[\\])*\\.[\\p{L}_$][\\p{LD}_$]*)+)?"

    private const val MODULE_PATTERN =
            "^\\s*%s(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?))?(.*)$"

    val ourDojoParametersPattern = Pattern.compile("^\\s*([\\p{L}_$][\\p{LD}_$]*):(.*)$", 64)

    private val ourJSDocParametersPattern = Pattern.compile("^\\s*@param((:? |\\s*\\{).*)$")

    private val ourJSDocParametersRestPattern = Pattern.compile(
            "^\\s*(?:(\\[?\\(?\\{?`?([\\p{L}_$][\\p{LD}_$]*(?:\\|[\\p{L}_$][\\p{LD}_$]*)*)((?:(?:\\[\\])*\\.[\\p{L}_$][\\p{LD}_$]*)+)?(\\.\\.\\.)?`?(?:\\s*=\\s*((?:\\[[^\\]]*\\])|[^\\]\\s]*|(?:'[^']*')|(?:\"[^\"]*\")))?\\)?\\]?)(?:\\s:\\s(\\S+))?)?(?:\\s*-\\s*)?(.*)$")

    private val ourYuiDocParametersPattern = Pattern.compile("^\\s*([\\p{L}_$][\\p{LD}_$]*)\\s*(\\{.*)$")

    private val ourJSDocEventPattern = Pattern.compile(
            "^\\s*@event\\s*((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(?:\\s:\\s(\\S+))?(?:\\s*-\\s*)?(.*)$")

    private val ourJSDocRemarkPattern = Pattern.compile("^\\s*@remarks (.*)$")

    private val ourJSDocMethodPattern = Pattern.compile(
            "^\\s*@method(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*))?$")

    private val ourJSDocClassPattern = Pattern.compile(
            "^\\s*@class(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)\\s*|(\\s.*)?)$")

    private val ourJSDocDeprecatedPattern = Pattern.compile("^\\s*@deprecated\\s*(.*)$")

    private val ourJSDocConstructorPattern = Pattern.compile(
            "^\\s*@constructor(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)\\s*|(\\s.*)?)$")

    private val ourJSDocConstructsPattern = Pattern.compile("^\\s*@construct(?:s?)\\s*$")

    private val ourJSDocConstantPattern = Pattern.compile("^\\s*@const(?:ant)?(?:\\s+(.*))?$")

    private val ourJSDocFinalPattern = Pattern.compile("^\\s*@final\\s*$")

    private val ourJSDocPrivatePattern = Pattern.compile("^\\s*@private(?:\\s+(.+)|\\s*)$")

    private val ourJSDocPublicPattern = Pattern.compile("^\\s*@public(?:\\s+(.+)|\\s*)$")

    private val ourJSDocProtectedPattern = Pattern.compile("^\\s*@protected(?:\\s+(.+)|\\s*)$")

    private val ourJSDocOptionalPattern = Pattern.compile("^\\s*@optional(.*)$")

    private val ourJSDocStaticPattern = Pattern.compile("^\\s*@static\\s*$")

    private val ourJSDocSeePattern = Pattern.compile("^\\s*@see (.*)$")

    private val ourJSDocDescriptionPattern = Pattern.compile("^\\s*@description\\s*(.+)$")

    private val ourJSDocReturnPattern = Pattern.compile("^\\s*@return(?:s)?\\s*(?::)?\\s*(.+)?$")

    private val ourJSDocNamespacePattern = Pattern.compile(
            "^\\s*@namespace\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)?(.*)$")

    private val ourJSDocNamePattern = Pattern.compile(
            "^\\s*@name\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocAliasPattern = Pattern.compile(
            "^\\s*@alias\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocConfigPattern = Pattern.compile("^\\s*@config\\s*(\\{)?(.*)$")

    private val ourJSDocPropertyPattern = Pattern.compile("^\\s*@prop(?:erty)?\\s*(\\{)?(.*)$")

    private val ourJSDocPropertyRestPattern = Pattern.compile(
            "^\\s*(\\[?)((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(?:\\s*=\\s*([^\\]\\s]*|(?:'[^']*')|(?:\"[^\"]*\")))?\\]?(?:\\s*-\\s*)?(.*)$")

    private val ourJSDocTypePattern = Pattern.compile("^\\s*@type([ {].*)$")

    private val ourJSDocRequiresPattern = Pattern.compile("^\\s*@requires\\s*(\\S+)(.*)$")

    private val ourJSDocDefaultPattern = Pattern.compile("^\\s*@default\\s*(.*)$")

    private val ourJSDocExtendsPattern = Pattern.compile("^\\s*@extends(.*)$")

    private val ourJSDocAugmentsPattern = Pattern.compile("^\\s*@augments(.*)$")

    private val ourJSDocLendsPattern = Pattern.compile("^\\s*@lends(.*)$")

    private val ourJSDocThrowsPattern = Pattern.compile("^\\s*@throws(?:\\s+(.*))$")

    private val ourJSDocExceptionPattern = Pattern.compile("^\\s*@exception\\s*(?:\\s*-\\s*)?(.*)$")

    private val ourJSDocLinkPattern = Pattern.compile("(\\[[^\\]]+\\])?\\{@link(?:code|plain)?\\s+([^\\}]+)\\}")

    private val ourJSDocBrowserPattern = Pattern.compile("^\\s*@browser\\s+(.*)$")

    private val ourJSDocInheritDocPattern = Pattern.compile("^\\s*@inherit[Dd]oc(.*)$")

    private val ourJSDocTypedefPattern = Pattern.compile("^\\s*@typedef\\s+(.*)$")

    private val ourJSDocNameTailPattern = Pattern.compile(
            "^((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)?(.*)$")

    private val ourJSDocEnumPattern = Pattern.compile("^\\s*@enum(.*)$")

    private val ourJSDocInterfacePattern = Pattern.compile(
            "^\\s*@interface(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?))?(\\s*)$")

    private val ourJSDocImplementsPattern = Pattern.compile("^\\s*@implements(.*)$")

    private val ourJSDocOverridePattern = Pattern.compile("^\\s*@override(.*)$")

    private val ourJSDocThisPattern = Pattern.compile("^\\s*@this(.*)$")

    private val ourJSDocMixinPattern = Pattern.compile("^\\s*@mixin(?:|[^s](.*))$")

    private val ourJSDocMixesPattern = Pattern.compile("^\\s*@mix(?:es|ins)(.*)$")

    private val ourJSDocFunctionPattern = Pattern.compile(
            "^\\s*@func(?:tion)?(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?))?(\\s*)$")

    private val ourJSDocExportsPattern = Pattern.compile(
            "^\\s*@exports(?:\\s+(?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?\\s+as)?\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocTemplatePattern = Pattern.compile("^\\s*@template\\s+(.*)$")

    private val ourJSDocTemplateNamesPattern =
            Pattern.compile("^\\s*((?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|[\\s,])+)(.*)$")

    private val ourJSDocAuthorPattern = Pattern.compile("^\\s*@author\\s+(.*)$")

    private val ourJSDocSincePattern = Pattern.compile("^\\s*@since\\s+(.*)$")

    private val ourJSDocVersionPattern = Pattern.compile("^\\s*@version\\s+(.*)$")

    private val ourJSDocSummaryPattern = Pattern.compile("^\\s*@summary\\s+(.*)$")

    private val ourJSDocExamplePattern = Pattern.compile("^\\s*@example(?:|(.*))$")

    private val ourJSDocFileOverviewPattern = Pattern.compile("^\\s*@fileOverview\\s+(.*)$")

    private val ourJSDocMemberOfPattern = Pattern.compile(
            "^\\s*@member[Oo]f!?\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocMethodOfPattern = Pattern.compile(
            "^\\s*@methodOf\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocFieldOfPattern = Pattern.compile(
            "^\\s*@fieldOf\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocAbstractPattern = Pattern.compile("^\\s*@abstract(\\s.*)?$")

    private val ourJSDocVirtualPattern = Pattern.compile("^\\s*@virtual(\\s.*)?$")

    private val ourJSDocCallbackPattern = Pattern.compile(
            "^\\s*@callback\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)?$")

    private val ourJSDocExternalPattern = Pattern.compile(
            "^\\s*@external\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)?$")

    private val ourJSDocHostPattern = Pattern.compile(
            "^\\s*@host\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)?$")

    private val ourJSDocGlobalPattern = Pattern.compile("^\\s*@global(\\s.*)?$")

    private val ourJSDocMemberPattern = Pattern.compile("^\\s*@member\\s+(.*)$")

    private val ourJSDocVarPattern = Pattern.compile("^\\s*@var\\s+(.*)$")

    private val ourJSDocModulePattern = Pattern.compile(String.format(
            "^\\s*%s(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?))?(.*)$",
            "@module"))

    private val ourProvideModulesPattern = Pattern.compile(String.format(
            "^\\s*%s(?:\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?))?(.*)$",
            "@providesModule"))

    private val ourJSDocFiresPattern = Pattern.compile(
            "^\\s*@fires\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocEmitsPattern = Pattern.compile(
            "^\\s*@emits\\s+((?:[.#~])?(?:import\\s*\\(\\s*(?:'(?:[^\"\\\\]|\\\\.)+'|\"(?:[^\"\\\\]|\\\\.)+\")\\s*\\)|(?:module:|event:|external:)(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+')|[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+)(?:[.#~](?:module:|event:|external:)?(?:[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+|\"(?:[^\"\\\\]|\\\\.)+\"|'(?:[^'\\\\]|\\\\.)+'))*(?:[.#~])?)(.*)$")

    private val ourJSDocTodoPattern = Pattern.compile("^\\s*@todo(?:\\s+(.*))?$")

    private val ourJSDocInstancePattern = Pattern.compile("^\\s*@instance(?:\\s+(.*))?$")

    private val ourJSDocNgModulePattern = Pattern.compile("^\\s*@ngModule\\s+(.*)$")

    private val ourJSDocExtendedTypeNamePattern = Pattern.compile("^[^.~#\\s:(){}\\[\\]<>?!='\",|&*@`\\p{C}]+$")

    private val patternToHintMap = mapOf(
            ourJSDocParametersPattern to "@pa",
            ourDojoParametersPattern to ":",
            ourJSDocMethodPattern to "@m",
            ourJSDocOptionalPattern to "@op",
            ourJSDocEventPattern to "@ev",
            ourJSDocConfigPattern to "@conf",
            ourJSDocExtendsPattern to "@ext",
            ourJSDocAugmentsPattern to "@au",
            ourJSDocThrowsPattern to "@th",
            ourJSDocExceptionPattern to "@exc",
            ourJSDocRemarkPattern to "@rem",
            ourJSDocReturnPattern to "@ret",
            ourJSDocBrowserPattern to "@b",
            ourJSDocPublicPattern to "@pu",
            ourJSDocProtectedPattern to "@prot",
            ourJSDocStaticPattern to "@st",
            ourJSDocSeePattern to "@se",
            ourJSDocDescriptionPattern to "@des",
            ourJSDocDeprecatedPattern to "@dep",
            ourJSDocConstructorPattern to "@cons",
            ourJSDocConstructsPattern to "@cons",
            ourJSDocClassPattern to "@cl",
            ourJSDocLendsPattern to "@le",
            ourJSDocPrivatePattern to "@pri",
            ourJSDocNamespacePattern to "@n",
            ourJSDocNamePattern to "@n",
            ourJSDocPropertyPattern to "@prop",
            ourJSDocTypePattern to "@ty",
            ourJSDocFinalPattern to "@f",
            ourJSDocConstantPattern to "@const",
            ourJSDocRequiresPattern to "@req",
            ourJSDocDefaultPattern to "@def",
            ourJSDocInheritDocPattern to "@inh",
            ourJSDocTypedefPattern to "@typ",
            ourJSDocEnumPattern to "@en",
            ourJSDocInterfacePattern to "@int",
            ourJSDocImplementsPattern to "@imp",
            ourJSDocOverridePattern to "@ov",
            ourJSDocThisPattern to "@th",
            ourJSDocMixinPattern to "@mix",
            ourJSDocMixesPattern to "@mix",
            ourJSDocFunctionPattern to "@fun",
            ourJSDocExportsPattern to "@exp",
            ourJSDocTemplatePattern to "@tem",
            ourJSDocAuthorPattern to "@aut",
            ourJSDocExamplePattern to "@exa",
            ourJSDocFileOverviewPattern to "@fil",
            ourJSDocSincePattern to "@sin",
            ourJSDocVersionPattern to "@ver",
            ourJSDocMemberOfPattern to "@mem",
            ourJSDocFieldOfPattern to "@fie",
            ourJSDocMethodOfPattern to "@met",
            ourJSDocAbstractPattern to "@abs",
            ourJSDocVirtualPattern to "@vir",
            ourJSDocAliasPattern to "@ali",
            ourJSDocCallbackPattern to "@cal",
            ourJSDocExternalPattern to "@ext",
            ourJSDocHostPattern to "@hos",
            ourJSDocGlobalPattern to "@glo",
            ourJSDocMemberPattern to "@mem",
            ourJSDocVarPattern to "@var",
            ourJSDocModulePattern to "@mod",
            ourProvideModulesPattern to "@provi",
            ourJSDocFiresPattern to "@fir",
            ourJSDocEmitsPattern to "@emi",
            ourJSDocTodoPattern to "@tod",
            ourJSDocSummaryPattern to "@summ",
            ourJSDocInstancePattern to "@ins",
            ourJSDocNgModulePattern to "@ngMo",
    )

    private val patternToMetaDocTypeMap = mapOf(
            ourJSDocParametersPattern to MetaDocType.PARAMETER,
            ourJSDocMethodPattern to MetaDocType.METHOD,
            ourJSDocOptionalPattern to MetaDocType.PREVIOUS_IS_OPTIONAL,
            ourJSDocEventPattern to MetaDocType.EVENT,
            ourJSDocConfigPattern to MetaDocType.CONFIG,
            ourJSDocExtendsPattern to MetaDocType.EXTENDS,
            ourJSDocAugmentsPattern to MetaDocType.EXTENDS,
            ourJSDocThrowsPattern to MetaDocType.THROWS,
            ourJSDocExceptionPattern to MetaDocType.THROWS,
            ourJSDocRemarkPattern to MetaDocType.NOTE,
            ourJSDocReturnPattern to MetaDocType.RETURN,
            ourJSDocBrowserPattern to MetaDocType.BROWSER,
            ourJSDocPublicPattern to MetaDocType.PUBLIC,
            ourJSDocProtectedPattern to MetaDocType.PROTECTED,
            ourJSDocStaticPattern to MetaDocType.STATIC,
            ourJSDocSeePattern to MetaDocType.SEE,
            ourJSDocDescriptionPattern to MetaDocType.DESCRIPTION,
            ourJSDocDeprecatedPattern to MetaDocType.DEPRECATED,
            ourJSDocConstructorPattern to MetaDocType.CONSTRUCTOR,
            ourJSDocConstructsPattern to MetaDocType.CONSTRUCTS,
            ourJSDocClassPattern to MetaDocType.CLASS,
            ourJSDocLendsPattern to MetaDocType.LENDS,
            ourJSDocPrivatePattern to MetaDocType.PRIVATE,
            ourJSDocNamespacePattern to MetaDocType.NAMESPACE,
            ourJSDocNamePattern to MetaDocType.NAME,
            ourJSDocPropertyPattern to MetaDocType.PROPERTY,
            ourJSDocTypePattern to MetaDocType.TYPE,
            ourJSDocFinalPattern to MetaDocType.FINAL,
            ourJSDocConstantPattern to MetaDocType.FINAL,
            ourJSDocRequiresPattern to MetaDocType.REQUIRES,
            ourJSDocDefaultPattern to MetaDocType.DEFAULT,
            ourJSDocInheritDocPattern to MetaDocType.INHERIT_DOC,
            ourJSDocTypedefPattern to MetaDocType.TYPEDEF,
            ourJSDocEnumPattern to MetaDocType.ENUM,
            ourJSDocInterfacePattern to MetaDocType.INTERFACE,
            ourJSDocImplementsPattern to MetaDocType.IMPLEMENTS,
            ourJSDocOverridePattern to MetaDocType.OVERRIDE,
            ourJSDocThisPattern to MetaDocType.THIS,
            ourJSDocMixinPattern to MetaDocType.MIXIN,
            ourJSDocMixesPattern to MetaDocType.MIXES,
            ourJSDocFunctionPattern to MetaDocType.FUNCTION,
            ourJSDocExportsPattern to MetaDocType.EXPORTS,
            ourJSDocTemplatePattern to MetaDocType.TEMPLATE,
            ourJSDocAuthorPattern to MetaDocType.AUTHOR,
            ourJSDocExamplePattern to MetaDocType.EXAMPLE,
            ourJSDocFileOverviewPattern to MetaDocType.FILE_OVERVIEW,
            ourJSDocSincePattern to MetaDocType.SINCE,
            ourJSDocVersionPattern to MetaDocType.VERSION,
            ourJSDocMemberOfPattern to MetaDocType.MEMBER_OF,
            ourJSDocFieldOfPattern to MetaDocType.MEMBER_OF,
            ourJSDocMethodOfPattern to MetaDocType.MEMBER_OF,
            ourJSDocAbstractPattern to MetaDocType.ABSTRACT,
            ourJSDocVirtualPattern to MetaDocType.ABSTRACT,
            ourJSDocAliasPattern to MetaDocType.ALIAS,
            ourJSDocCallbackPattern to MetaDocType.CALLBACK,
            ourJSDocExternalPattern to MetaDocType.EXTERNAL,
            ourJSDocHostPattern to MetaDocType.EXTERNAL,
            ourJSDocGlobalPattern to MetaDocType.GLOBAL,
            ourJSDocMemberPattern to MetaDocType.MEMBER,
            ourJSDocVarPattern to MetaDocType.MEMBER,
            ourJSDocModulePattern to MetaDocType.MODULE,
            ourProvideModulesPattern to MetaDocType.MODULE,
            ourJSDocFiresPattern to MetaDocType.FIRES,
            ourJSDocEmitsPattern to MetaDocType.FIRES,
            ourJSDocTodoPattern to MetaDocType.TODO,
            ourJSDocSummaryPattern to MetaDocType.SUMMARY,
            ourJSDocInstancePattern to MetaDocType.INSTANCE,
            ourJSDocNgModulePattern to MetaDocType.NG_MODULE,
    )

    private val inlinePatternToMetaDocTypeMap = mapOf(ourDojoParametersPattern to MetaDocType.PARAMETER)

    const val HYPERLINK_SEPARATOR = "%"

    const val INDEX_POSITION_SEPARATOR = ":"

    @NonNls
    private val prefixToPatternToHintMap = mapOf(
            Pattern.compile("^\\s*description:(.*)$") to "descr",
            Pattern.compile("^ summary(?:\\:)?(.*)\$") to "summ",
            Pattern.compile("^\\s*\\*(?:\\*)?(.*)$") to "*",
            Pattern.compile("^[/]+(.*)$") to "/",
            Pattern.compile("^\\s*Parameters:(.*)$") to "Parame",
    )

    private val DOC_COMMENT_ALLOWED_AFTER =
            TokenSet.orSet(JSTokenTypes.COMMENTS_AND_WHITESPACES, JSStubElementTypes.ATTRIBUTE_LISTS)

    val ourPrimitiveTypeFilter = JSKeywordSets.PRIMITIVE_TYPES

}