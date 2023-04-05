package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.ide.BrowserUtil
import com.intellij.lang.ASTNode
import com.intellij.lang.ecmascript6.psi.JSExportAssignment
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor
import com.intellij.lang.javascript.documentation.JSDocumentationProcessor.MetaDocType
import com.intellij.lang.javascript.documentation.JSDocumentationUtils
import com.intellij.lang.javascript.documentation.JSExternalLibraryDocBundle
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptObjectType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeMember
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSNamespaceDeclaration
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.impl.JSVariableBaseImpl
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.jsdoc.JSDocTag
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.resolve.ImplicitJSVariableImpl
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil
import com.intellij.lang.javascript.psi.stubs.TypeScriptProxyImplicitElement
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import org.jetbrains.annotations.NonNls
import java.util.*
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

    fun processDocumentationTextFromComment(context: PsiElement, _initialComment: ASTNode,
            processor: JSDocumentationProcessor) {
        var prev = _initialComment.treePrev
        if (prev != null && prev.psi is OuterLanguageElement) {
            while (prev != null && prev.psi is OuterLanguageElement) {
                prev = prev.treePrev
            }
        } else {
            prev = null
        }

        if (prev != null && prev.psi !is PsiComment) {
            prev = null
        }

        val initialComment = prev ?: _initialComment
        val eolComment = initialComment.elementType === JSTokenTypes.END_OF_LINE_COMMENT
        val commentLineIterator: Any
        if (eolComment) {
            commentLineIterator = object : Enumeration<String> {
                var commentNode: ASTNode? = initialComment
                override fun hasMoreElements(): Boolean {
                    return commentNode != null
                }

                override fun nextElement(): String {
                    val resultCommentNode = commentNode
                    commentNode = commentNode!!.treeNext
                    if (commentNode != null && commentNode!!.elementType === TokenType.WHITE_SPACE) {
                        commentNode = commentNode!!.treeNext
                    }
                    if (commentNode != null && commentNode!!.elementType !== JSTokenTypes.END_OF_LINE_COMMENT) {
                        commentNode = null
                    }
                    val text = resultCommentNode!!.text
                    return if (text.startsWith("//")) text.substring(2) else ""
                }
            }
        } else {
            var text = initialComment.text
            text = unwrapCommentDelimiters(text)
            commentLineIterator = StringTokenizer(text, "\r\n")
        }

        val needPlainCharData = processor.needsPlainCommentData()
        var lastParameterName: String? = null
        var tag: DocTagBuilder? = null
        val multilineCommentTag = StringBuilder()

        while ((commentLineIterator as Enumeration<String>).hasMoreElements()) {
            var s = commentLineIterator.nextElement() as String
            if (s.indexOf(64.toChar()) != -1 || s.indexOf(
                            58.toChar()) != -1 || needPlainCharData || tag != null && tag.continueType) {
                if (needPlainCharData && s.contains("{@link")) {
                    val m = ourJSDocLinkPattern.matcher(s)
                    val b = StringBuilder()
                    var lastEnd = 0
                    while (m.find()) {
                        val b2 = StringBuilder()
                        val linkText = m.group(1)
                        var linkUrl = m.group(2).trim { it <= ' ' }
                        var text: String?
                        if (linkText != null && linkText.length > 2) {
                            text = linkText.substring(1, linkText.length - 1).trim { it <= ' ' }
                        } else {
                            val separatorIndex = StringUtil.indexOfAny(linkUrl, " |")
                            if (separatorIndex >= 0) {
                                text = linkUrl.substring(separatorIndex + 1).trim { it <= ' ' }
                                linkUrl = linkUrl.substring(0, separatorIndex)
                            } else {
                                text = null
                            }
                        }
                        if (BrowserUtil.isAbsoluteURL(linkUrl)) {
                            b2.append("<a href=\"").append(linkUrl).append("\">").append(text ?: linkUrl).append("</a>")
                        } else {
                            appendHyperLinkToElement(null, linkUrl, b2, if (StringUtil.isEmpty(text)) linkUrl else text,
                                    true, true, 0)
                        }
                        b.append(s, lastEnd, m.start())
                        lastEnd = m.end()
                        b.append(b2)
                    }
                    b.append(s.substring(lastEnd))
                    s = b.toString()
                }
                val commentText = prepareCommentLine(s)
                tag = handlePossiblyMultilinedTag(context, tag, multilineCommentTag, commentText, lastParameterName,
                        processor, eolComment)
                if (tag != null) {
                    if (tag.breakEnd) {
                        break
                    }
                    lastParameterName = tag.lastParameterName
                } else if (needPlainCharData && !processor.onCommentLine(commentText)) {
                    break
                }
            }
        }

        processor.postProcess()
    }

    private fun handlePossiblyMultilinedTag(context: PsiElement, previousTag: DocTagBuilder?,
            multilineCommentTag: StringBuilder, commentLine: String, lastParameterName: String?,
            processor: JSDocumentationProcessor?, eolComment: Boolean): DocTagBuilder? {
        return if (previousTag != null && previousTag.continueType && !commentLine.contains("@")) {
            multilineCommentTag.append(commentLine)
            handleCommentLine(context, multilineCommentTag.toString(), lastParameterName, processor,
                    patternToMetaDocTypeMap)
        } else {
            multilineCommentTag.setLength(0)
            val tag = handleCommentLine(context, commentLine, lastParameterName, processor,
                    if (eolComment) inlinePatternToMetaDocTypeMap else patternToMetaDocTypeMap)
            if (tag != null && tag.continueType) {
                multilineCommentTag.append(commentLine)
            }
            tag
        }
    }

    private fun prepareCommentLine(s: String): String {
        var commentText = s.replace('\t', ' ')
        val var2 = prefixToPatternToHintMap.entries.iterator()

        while (var2.hasNext()) {
            val (key, value) = var2.next()
            val matcher = if (commentText.contains(value)) key.matcher(commentText) else null
            if (matcher != null && matcher.matches()) {
                commentText = matcher.group(1)
                break
            }
        }

        return commentText
    }

    private fun trimBrackets(type: String): String {
        var outerBrackets = true
        val length = type.length
        var begin = 0
        var end = length - 1

        while (begin <= end) {
            if (Character.isSpaceChar(type[begin])) {
                ++begin
            } else if (Character.isSpaceChar(type[end])) {
                --end
            } else {
                if (!outerBrackets || type[begin] != '{' || type[end] != '}') {
                    break
                }
                outerBrackets = false
                ++begin
                --end
            }
        }

        if (begin < length && type[begin] == '{') {
            var nextNonWs = begin + 1
            while (nextNonWs < length && Character.isSpaceChar(type[nextNonWs])) {
                ++nextNonWs
            }
            val matchedClosingBracketPos = type.lastIndexOf(125.toChar(), end - 1)
            if (nextNonWs < length && type[nextNonWs] == '{' && matchedClosingBracketPos > 0) {
                begin = nextNonWs
                end = matchedClosingBracketPos
            }
        }

        return type.substring(begin, end + 1)
    }

    private fun getTypeStringLength(context: PsiElement, tailText: String): Int {
        var i = 0

        var startsWithBrace = false
        while (i < tailText.length && tailText[i] == ' ') {
            ++i
        }

        if (i < tailText.length && tailText[i] == '{') {
            ++i
            startsWithBrace = true
        }

        return if (startsWithBrace && tailText.indexOf(123.toChar(), i) == -1 && tailText.indexOf(125.toChar(),
                        i) >= 0) {
            tailText.indexOf(125.toChar(), i) + 1
        } else {
            val parser = JSTypeParser(context.project, tailText.substring(i), JSTypeSource.EMPTY)
            val type = parser.parseParameterType(true)
            if (type == null) {
                if (startsWithBrace) tailText.length + 1 else 0
            } else if (!startsWithBrace) {
                i + parser.typeStringLength
            } else {
                i += parser.typeStringLength
                while (i < tailText.length && tailText[i] == ' ') {
                    ++i
                }
                if (i < tailText.length && tailText[i] == '}') i + 1 else tailText.length + 1
            }
        }
    }

    private fun createParameterOrParameterFieldReference(matchName: String, fieldName: String?): String {
        var realFieldName = fieldName
        return if (realFieldName == null) {
            matchName
        } else {
            if (!StringUtil.startsWithChar(realFieldName, '.') && !StringUtil.startsWithChar(realFieldName, '[')) {
                realFieldName = ".$realFieldName"
            }
            matchName + realFieldName
        }
    }

    fun unwrapCommentDelimiters(text: String): String {
        var realText = text
        var marker = "/**"
        if (realText.startsWith(marker)) {
            realText = realText.substring(marker.length)
        } else {
            var shoulChangeText = true
            marker = "/*"
            if (!realText.startsWith(marker)) {
                marker = "//"
                if (!realText.startsWith(marker)) {
                    shoulChangeText = false
                }
            }
            if (shoulChangeText) {
                realText = realText.substring(marker.length)
            }
        }

        marker = "*/"
        if (realText.endsWith(marker)) {
            realText = realText.substring(0, realText.length - marker.length)
        }

        marker = "-->"
        if (realText.endsWith(marker)) {
            realText = realText.substring(0, realText.length - marker.length)
        }

        marker = "<!---"
        if (realText.startsWith(marker)) {
            realText = realText.substring(marker.length)
        }

        return realText
    }

    private fun findTrailingCommentInFunctionBody(function: JSFunction): ASTNode? {
        val block = function.node.findChildByType(JSElementTypes.BLOCK_STATEMENT)
        return if (block == null) {
            null
        } else {
            var prev = block.lastChildNode
            while (prev != null) {
                if (prev.elementType === JSStubElementTypes.RETURN_STATEMENT) {
                    var comment = block.findChildByType(JSTokenTypes.COMMENTS, prev)
                    if (comment != null) {
                        val prevLeaf = PsiTreeUtil.prevLeaf(comment.psi)
                        if (prevLeaf is PsiWhiteSpace && prevLeaf.textContains('\n')) {
                            comment = null
                        }
                    }
                    return comment
                }
                if (JSExtendedLanguagesTokenSetProvider.STATEMENTS.contains(prev.elementType)) {
                    break
                }
                prev = prev.treePrev
            }
            null
        }
    }

    fun findLeadingCommentInFunctionBody(element: PsiElement): ASTNode? {
        val functionNode = element.node
        val block = functionNode?.findChildByType(JSElementTypes.BLOCK_STATEMENT)
        return if (block == null) {
            null
        } else {
            val firstChildNode = block.firstChildNode
            var node = firstChildNode?.treeNext
            while (node != null) {
                val nodeType = node.elementType
                if (nodeType !== TokenType.WHITE_SPACE) {
                    return if (JSTokenTypes.COMMENTS.contains(nodeType)) {
                        node
                    } else {
                        null
                    }
                }
                node = node.treeNext
            }
            null
        }
    }

    fun findTypeFromInlineComment(element: PsiElement): JSType? {
        var prevSibling: PsiElement
        prevSibling = element.prevSibling
        while (prevSibling is PsiWhiteSpace) {
            prevSibling = prevSibling.prevSibling
        }

        return if (prevSibling is JSDocComment) {
            val type = prevSibling.type
            JSTypeParser.createTypeFromJSDoc(element.project, type,
                    JSTypeSourceFactory.createTypeSource(prevSibling, true))
        } else {
            null
        }
    }

    fun findOwnDocComment(element: PsiElement): JSDocComment? {
        var parent: PsiElement?
        if (element is JSExpression) {
            val anchor = if (element is JSDefinitionExpression) element.getParent() else element
            parent = PsiTreeUtil.skipWhitespacesBackward(anchor)
            if (parent is JSDocComment) {
                return parent
            }
        }

        if (element is JSQualifiedNamedElement || element is JSExportAssignment) {
            var comment = getStartingChildDocComment(element)
            if (comment != null) {
                return comment
            }
            if (element is JSVariable) {
                parent = element.getParent()
                if (parent is JSVarStatement) {
                    comment = getStartingChildDocComment(parent)
                    if (comment != null && hasSingleVariable((parent as JSVarStatement?)!!)) {
                        return comment
                    }
                }
            }
        }

        return if (element is JSExpression && element.getParent() is JSExportAssignment) {
            findOwnDocComment(element.getParent())
        } else {
            null
        }
    }

    private fun hasSingleVariable(statement: JSVarStatement): Boolean {
        val declarations = statement.declarations
        return declarations.size == 1 && declarations[0] is JSVariable
    }

    fun findScopeComment(element: JSElement): PsiComment? {
        val scope = PsiTreeUtil.getParentOfType<PsiElement>(element, JSFunction::class.java, JSClass::class.java)
        if (scope != null) {
            val scopeComment = findDocComment(scope)
            if (scopeComment != null) {
                return scopeComment
            }
            if (scope is JSFunction) {
                val clazz = PsiTreeUtil.getParentOfType(scope, JSClass::class.java)
                if (clazz != null) {
                    return findDocComment(clazz)
                }
            }
        }

        return null
    }

    fun findDocComment(element: PsiElement): PsiComment? {
        return findDocComment(element, null, null)
    }

    private fun findAttributeListAnchor(element: PsiElement, context: PsiElement?): PsiElement {
        if (element is JSAttributeListOwner) {
            if (context == null) {
                val attributeList = element.attributeList
                var anchor: PsiElement? = null
                if (attributeList == null) {
                    return element
                }
                var currentNode = attributeList.node.lastChildNode
                while (currentNode != null) {
                    val nodeType = currentNode.elementType
                    if (!JSTokenTypes.MODIFIERS.contains(
                                    nodeType) && nodeType !== JSStubElementTypes.ES6_DECORATOR && nodeType !== JSTokenTypes.WHITE_SPACE && nodeType !== JSElementTypes.REFERENCE_EXPRESSION && nodeType !== JSStubElementTypes.ATTRIBUTE) {
                        val nextNode = currentNode.treeNext
                        if (nextNode != null) {
                            anchor = nextNode.psi
                        }
                        break
                    }
                    currentNode = currentNode.treePrev
                }
                if (anchor != null) {
                    return anchor
                }
                return element
            }
        }

        return element
    }

    fun findDocComment(psiElement: PsiElement, context: PsiElement?,
            elementToAttach: Ref<in PsiElement>?): PsiComment? {
        var realPsiElement: PsiElement? = psiElement
        ProgressManager.checkCanceled()
        return if (realPsiElement is JSDocComment) {
            realPsiElement
        } else {
            if (realPsiElement is TypeScriptProxyImplicitElement) {
                realPsiElement = realPsiElement.explicitElement
                if (realPsiElement == null) {
                    return null
                }
            }

            var realElement = findAttributeListAnchor(realPsiElement!!, context)
            if (realElement !is PsiFileSystemItem && realElement.containingFile != null) {
                if (realElement is ImplicitJSVariableImpl) {
                    null
                } else {
                    var docComment: PsiComment? = null
                    var parentToSearchDocComment: PsiElement?
                    if (realElement is JSParameter) {
                        parentToSearchDocComment = PsiTreeUtil.skipWhitespacesBackward(realElement)
                        if (parentToSearchDocComment is PsiComment) {
                            docComment = parentToSearchDocComment
                        }
                        val function = realElement.declaringFunction
                        if (function != null) {
                            realElement = function
                        }
                    }
                    if (realElement is JSFunctionExpression || realElement is JSDefinitionExpression) {
                        realElement = getElementOverAssignmentParent(realElement)
                    }
                    if (realElement is JSFieldVariable) {
                        val _docComment = getStartingChildDocComment((realElement as PsiElement?))
                        if (_docComment != null) {
                            docComment = _docComment
                        } else {
                            val parent = (realElement as PsiElement).parent
                            if (parent !is TypeScriptObjectType) {
                                realElement = parent
                            }
                        }
                    }
                    parentToSearchDocComment = getParentToSearchDocComment(realElement)
                    if (parentToSearchDocComment != null) {
                        val _docComment = getStartingChildDocComment(parentToSearchDocComment)
                        if (_docComment != null) {
                            docComment = _docComment
                        }
                    }
                    if (docComment == null) {
                        docComment = searchPreviousNonDocComment(realElement)
                    }
                    if (docComment != null) {
                        docComment = skipOuterElements((docComment as PsiComment?)!!)
                    }
                    if (docComment != null && isSelfSufficientComment(
                                    (docComment as PsiComment?)!!)) {
                        null
                    } else {
                        elementToAttach?.set(getAssociatedElement(realElement, parentToSearchDocComment))
                        docComment
                    }
                }
            } else {
                null
            }
        }
    }

    private fun isSelfSufficientComment(comment: PsiComment): Boolean {
        return if (comment !is JSDocComment) {
            false
        } else {
            val typedefs = comment.typedefs
            typedefs.isNotEmpty() && !ContainerUtil.exists(typedefs) { pair -> StringUtil.isEmpty(pair.first) }
        }
    }

    private fun searchPreviousNonDocComment(element: PsiElement): PsiComment? {
        var propName: String? = null
        var parent = element
        var elementToProcess: PsiElement? = element
        var docComment: PsiComment? = null

        while (elementToProcess !is PsiFile) {
            if (elementToProcess is JSExpressionStatement) {
                var continueIf = true
                val currentPropertyName = getPropertyNameFromExprStatement(elementToProcess)
                if (propName == null) {
                    if (elementToProcess !== parent) {
                        continueIf = false
                    }
                } else if (propName != currentPropertyName) {
                    continueIf = false
                }
                if (continueIf) {
                    docComment = getStartingChildDocComment(elementToProcess)
                    if (docComment != null) {
                        break
                    }
                    if (propName == null) {
                        propName = currentPropertyName
                    }
                    if (propName != null) {
                        elementToProcess = elementToProcess.getPrevSibling()
                        continue
                    }
                }
            }
            if (elementToProcess is PsiComment) {
                break
            }
            if (elementToProcess == null || elementToProcess !is PsiWhiteSpace && elementToProcess !== parent) {
                if (parent !is JSExpression) {
                    break
                }
                parent = parent.getParent()
                elementToProcess = parent
            } else {
                elementToProcess = elementToProcess.prevSibling
            }
        }

        if (docComment != null) {
            val docCommentPrevSibling = docComment.prevSibling
            if (docCommentPrevSibling != null && !JSUtils.isLineBreakWhiteSpace(docCommentPrevSibling) &&
                    docCommentPrevSibling.prevSibling is JSSourceElement) {
                docComment = null
            }
            if (docComment != null && docComment !is JSDocComment) {
                val docCommentNextSibling = docComment.nextSibling
                if (docCommentNextSibling is PsiWhiteSpace &&
                        StringUtil.countNewLines(docCommentNextSibling.getText()) > 1) {
                    docComment = null
                }
            }
        }

        return docComment
    }

    private fun skipOuterElements(comment: PsiComment): PsiComment? {
        return null
    }

    private fun getParentToSearchDocComment(element: PsiElement): PsiElement? {
        val parent = element.parent
        return parent as? JSExportAssignment
                ?: if (element !is JSFunction && element !is JSClass && element !is JSExpressionStatement && element !is JSNamespaceDeclaration && element !is TypeScriptTypeMember && element !is JSVarStatement && element !is JSExportAssignment) {
                    if (element !is JSProperty) {
                        null
                    } else {
                        if (!element.isGetProperty && !element.isSetProperty) {
                            element
                        } else {
                            element.value
                        }
                    }
                } else {
                    element
                }
    }

    private fun getElementOverAssignmentParent(element: PsiElement): PsiElement {
        var realElement = element
        var parent = realElement.parent
        while (parent is JSAssignmentExpression) {
            realElement = parent
            parent = parent.parent
        }

        if (parent is JSProperty || parent is JSVariable || parent is JSExportAssignment) {
            realElement = parent
        }

        return realElement
    }

    private fun getAssociatedElement(element: PsiElement, parentToSearchDocComment: PsiElement?): PsiElement? {
        var associatedElement = parentToSearchDocComment ?: element
        if (associatedElement is JSFunction || associatedElement is JSVarStatement || associatedElement is JSProperty || associatedElement is JSClass) {
            associatedElement = associatedElement.firstChild
            while (associatedElement is OuterLanguageElement) {
                associatedElement = associatedElement.getNextSibling()
            }
        }

        return associatedElement
    }

    fun getStartingChildDocComment(element: PsiElement): JSDocComment? {
        val node = element.node
        return if (node == null) {
            null
        } else {
            var child = node.firstChildNode
            while (child != null) {
                val type = child.elementType
                if (type === JSStubElementTypes.DOC_COMMENT) {
                    return child.psi as JSDocComment
                }
                if (!DOC_COMMENT_ALLOWED_AFTER.contains(type)) {
                    break
                }
                child = child.treeNext
            }
            null
        }
    }

    fun createOrUpdateTagsInDocComment(element: PsiElement, tagsToCreate: List<String>?,
            tagsToUpdate: Map<Int, String>?, tagsToDelete: Set<Int>?) {
        var indexedTagsToCreate: MutableList<Pair<Int, String>>? = null
        if (tagsToCreate != null) {
            indexedTagsToCreate = ArrayList(tagsToCreate.size)
            val var5 = tagsToCreate.iterator()
            while (var5.hasNext()) {
                val s = var5.next()
                indexedTagsToCreate.add(Pair.create(Int.MAX_VALUE, s))
            }
        }

        createOrUpdateTagsWithInsertionIndexes(element, indexedTagsToCreate, tagsToUpdate, tagsToDelete)
    }

    fun createOrUpdateTagsWithInsertionIndexes(element: PsiElement, tagsToCreate: MutableList<Pair<Int, String>>?,
            tagsToUpdate: Map<Int, String>?, tagsToDelete: Set<Int>?) {
        tagsToCreate?.sortWith(Pair.comparingByFirst())

        val elementToAttachRef = Ref.create<PsiElement>(null)
        val docComment = findDocComment(element, null, elementToAttachRef)
        val comment: java.lang.StringBuilder
        if (docComment is JSDocComment) {
            val oldText = docComment.getText()
            comment = java.lang.StringBuilder(oldText)
            var modifiedCommentLengthDelta = 0
            val tags: Array<JSDocTag> = docComment.tags
            val tagsToCreateIterator = tagsToCreate?.listIterator()
            var newTagOffset: Int
            newTagOffset = 0
            while (newTagOffset < tags.size) {
                var tagStart: Int
                var j: Int
                var tagToInsert: String
                val tag = tags[newTagOffset]
                while (tagsToCreateIterator != null && tagsToCreateIterator.hasNext()) {
                    val next: Pair<Int, String> = tagsToCreateIterator.next()
                    if (next.first as Int != newTagOffset) {
                        tagsToCreateIterator.previous()
                        break
                    }
                    tagStart = modifiedCommentLengthDelta + tag.startOffsetInParent
                    val spacesPrefix = StringBuilder()
                    var c = '1'
                    j = tag.startOffsetInParent - 1
                    while (j >= 0 &&
                            (oldText[j].also { c = it } == ' ' || c == '\t' || c == '*' && j > 0 &&
                                    oldText[j - 1] != '*')) {
                        spacesPrefix.insert(0, c)
                        --j
                    }
                    val var10000 = next.second as String
                    tagToInsert = "@$var10000\n$spacesPrefix"
                    comment.insert(tagStart, tagToInsert)
                    modifiedCommentLengthDelta += tagToInsert.length
                }
                val newTagValue = if (tagsToUpdate != null) tagsToUpdate[newTagOffset] else null
                var tagEnd: Int
                if (newTagValue != null) {
                    val oldTagValue = tag.value
                    tagEnd = modifiedCommentLengthDelta + if (oldTagValue != null) {
                        oldTagValue.startOffsetInParent + tag.startOffsetInParent
                    } else {
                        tag.startOffsetInParent + tag.textLength
                    }
                    j = oldTagValue?.textLength ?: 0
                    comment.replace(tagEnd, tagEnd + j, newTagValue)
                    modifiedCommentLengthDelta += newTagValue.length - j
                } else if (tagsToDelete != null && tagsToDelete.contains(newTagOffset)) {
                    tagStart = oldText.lastIndexOf(10.toChar(), tag.startOffsetInParent)
                    if (tagStart == -1) {
                        tagStart = tag.startOffsetInParent
                    }
                    tagEnd = oldText.indexOf(10.toChar(), tag.startOffsetInParent)
                    if (tagEnd == -1) {
                        tagEnd = tag.startOffsetInParent + tag.textLength
                    }
                    comment.delete(tagStart + modifiedCommentLengthDelta, tagEnd + modifiedCommentLengthDelta)
                    modifiedCommentLengthDelta -= tagEnd - tagStart
                }
                ++newTagOffset
            }
            var newComment: String
            if (tagsToCreateIterator != null && tagsToCreateIterator.hasNext()) {
                newComment = comment.toString()
                val lastIndexOfNewline = newComment.lastIndexOf(10.toChar())
                newTagOffset = if (lastIndexOfNewline != -1) {
                    lastIndexOfNewline + 1
                } else {
                    comment.insert(comment.length - 2, '\n')
                    comment.length - 2
                }
                while (tagsToCreateIterator.hasNext()) {
                    val tag = (tagsToCreateIterator.next() as Pair<*, *>).second as String
                    comment.insert(newTagOffset, "\n").insert(newTagOffset, tag).insert(newTagOffset, " * @")
                    newTagOffset += tag.length + 1 + 4
                }
            }
            newComment = comment.toString()
            if (newComment.replace("[\\s*/]".toRegex(), "").isEmpty()) {
                docComment.delete()
            } else {
                docComment.replace(JSPsiElementFactory.createJSDocComment(newComment, docComment))
            }
        } else {
            val elementToAttach = elementToAttachRef.get() ?: return
            comment = StringBuilder("/**\n")
            if (tagsToCreate != null) {
                val var21 = tagsToCreate.iterator()
                while (var21.hasNext()) {
                    val tag: Pair<Int, String> = var21.next()
                    comment.append(" * @").append(tag.second).append("\n")
                }
            }
            comment.append(" */")
            val newDocComment = JSPsiElementFactory.createJSDocComment(comment.toString(), elementToAttach)
            elementToAttach.parent.addBefore(newDocComment, elementToAttach)
            JSChangeUtil.addWs(elementToAttach.parent.node, elementToAttach.node, "\n")
        }
    }

    private fun getPropertyNameFromExprStatement(element: PsiElement): String? {
        var realElement = element
        var propName: String? = null
        if (realElement is JSExpressionStatement) {
            realElement = realElement.expression!!
        }

        if (realElement is JSAssignmentExpression) {
            val rOperand = realElement.rOperand
            if (rOperand is JSFunctionExpression) {
                val name = rOperand.getName()
                propName = getPropertyName(name)
            }
        }

        return propName
    }

    fun getPropertyName(name: String?): String? {
        return if (name == null || name.length <= 3 ||
                (!StringUtil.startsWith(name, "get") && !StringUtil.startsWith(name, "set"))) {
            null
        } else {
            name.substring(3)
        }
    }

    private fun getTypeFromPreceedingComment(variable: JSVariable): JSType? {
        return if (variable is JSParameter) {
            val paramType = getParameterTypeFromPrecedingComment(variable)
            paramType?.simpleType
        } else {
            val prevSibling = getReasonablePrevElement(variable)
            if (prevSibling is PsiComment) {
                tryCreateTypeFromComment(prevSibling, false, false, false)
            } else {
                null
            }
        }
    }

    fun getParameterTypeFromPrecedingComment(parameter: JSParameter): JSParameterTypeDecorator? {
        var prevSibling = getReasonablePrevElement(parameter)
        if (prevSibling == null) {
            prevSibling = JSDocCommentImpl.findPossiblyRelatedCommentForArrowFunctionParam(parameter)
        }

        return if (prevSibling is PsiComment &&
                (prevSibling as PsiElement).node.elementType !== JSTokenTypes.END_OF_LINE_COMMENT &&
                isTypeStringAcceptable(unwrapCommentDelimiters((prevSibling as PsiElement).text), true)) {
            JSTypeParser.createParameterType(parameter.project,
                    unwrapCommentDelimiters((prevSibling as PsiElement).text),
                    JSTypeSourceFactory.createTypeSource(prevSibling as PsiElement, true), true, true)
        } else {
            null
        }
    }

    private fun getReasonablePrevElement(variable: JSVariable): PsiElement? {
        var prevSibling = variable.firstChild
        if (prevSibling != null && JSVariableBaseImpl.IDENTIFIER_TOKENS_SET.contains(prevSibling.node.elementType)) {
            prevSibling = variable.prevSibling
        }

        if (prevSibling is PsiWhiteSpace) {
            prevSibling = prevSibling.getPrevSibling()
        }

        return prevSibling
    }

    private fun rawGetTypeForVariable(variable: JSVariable): JSType? {
        val typeFromPreceedingComment = getTypeFromPreceedingComment(variable)
        return if (typeFromPreceedingComment != null) {
            typeFromPreceedingComment
        } else {
            val docComment = findDocComment(variable)
            if (docComment is JSDocComment) {
                val typeString = getTypeFromComment(docComment)
                if (typeString != null) {
                    return JSTypeParser.createTypeFromJSDoc(docComment.getProject(), typeString,
                            JSTypeSourceFactory.createTypeSource(docComment, true))
                }
            }
            null
        }
    }

    fun findDocCommentWider(psiAnchor: PsiElement?): PsiComment? {
        var anchor = psiAnchor
        var docComment: PsiComment? = null
        if (psiAnchor is JSExpression && findDocComment(psiAnchor).also { docComment = it } == null) {
            anchor = PsiTreeUtil.getParentOfType<PsiElement>(psiAnchor, JSStatement::class.java, JSProperty::class.java)
        }

        if (anchor != null && docComment == null) {
            docComment = findDocComment(anchor)
        }

        return docComment
    }

    fun findType(def: PsiElement?): String? {
        val docComment: PsiElement? = findDocCommentWider(def)
        return if (docComment is JSDocComment) docComment.type else null
    }

    private fun getTypeFromComment(comment: JSDocComment): String? {
        return comment.type
    }

    fun findContextType(anchor: PsiElement?): JSType? {
        return if (anchor != null && !DialectDetector.isTypeScript(anchor)) {
            val element: PsiElement? = findDocCommentWider(anchor)
            var fromType: String?
            if (element is JSDocComment) {
                fromType = element.thisType
                if (fromType != null) {
                    return JSTypeParser.createTypeFromJSDoc(element.getProject(), fromType,
                            JSTypeSourceFactory.createTypeSource(element, true))
                }
            }
            fromType = findType(anchor)
            if (fromType != null) {
                val type = JSTypeParser.createTypeFromJSDoc(anchor.project, fromType, JSTypeSource.EMPTY)
                if (type is JSFunctionTypeImpl) {
                    val thisType = type.thisType
                    if (thisType != null) {
                        return thisType
                    }
                }
            }
            null
        } else {
            null
        }
    }

    fun findEnumType(anchor: PsiElement?): String? {
        val docComment: PsiElement? = JSDocumentationUtils.findDocCommentWider(anchor)
        return if (docComment is JSDocComment) docComment.enumType else null
    }

    fun hasJSDocTag(anchor: PsiElement?, vararg docTypesArray: MetaDocType?): Boolean {
        val docComment: PsiElement? = findDocCommentWider(anchor)
        return if (docTypesArray.isEmpty()) {
            false
        } else {
            val docTypes: Set<MetaDocType> = EnumSet.of(docTypesArray[0], *docTypesArray)
            if (docComment != null) {
                val hasNeededTag = Ref.create(java.lang.Boolean.FALSE)
                processDocumentationTextFromComment(docComment, docComment.node,
                        object : JSDocumentationProcessor {

                            override fun needsPlainCommentData(): Boolean {
                                return false
                            }

                            override fun onCommentLine(line: String): Boolean {
                                return true
                            }

                            override fun onPatternMatch(type: MetaDocType, matchName: String?, matchValue: String?,
                                    remainingLineContent: String?, line: String, patternMatched: String): Boolean {
                                return if (docTypes.contains(type)) {
                                    hasNeededTag.set(java.lang.Boolean.TRUE)
                                    false
                                } else {
                                    true
                                }
                            }

                            override fun postProcess() {}

                        })
                hasNeededTag.get()
            } else {
                false
            }
        }
    }

    fun isDeprecated(element: PsiElement?): Boolean {
        return element is JSElementBase && (element as JSElementBase).isDeprecated
    }

    fun calculateDeprecated(element: PsiElement): Boolean {
        val docComment = findOwnDocComment(element)
        return docComment != null && docComment.hasDeprecatedTag()
    }

    fun calculateConst(element: PsiElement): Boolean {
        val docComment = JSDocumentationUtils.findOwnDocComment(element)
        return docComment != null && docComment.hasConstTag()
    }

    fun appendHyperLinkToElement(element: PsiElement?, elementName: String?, buffer: StringBuilder?,
            presentableName: String?, addElementName: Boolean, addFilename: Boolean, elementId: Int) {
        val containingFile = element?.containingFile
        val fileName = containingFile?.viewProvider?.virtualFile?.name
        val path = if (containingFile != null && !JSResolveUtil.isFromPredefinedFile(
                        containingFile)) containingFile.virtualFile.presentableUrl else fileName
        val isJsElement = element is JSElementBase
        val namespace = if (isJsElement) (element as JSElementBase).namespace else null
        val prefix = if (path != null) FileUtilRt.toSystemIndependentName(path) + "%" else ""
        var linkText =
                prefix + elementName + if (namespace != null) "%" + namespace.qualifiedName else if (isJsElement) "%null" else ""
        if (elementId > 0) {
            linkText = "$linkText:$elementId"
        }

        val quote = if (element != null) "`" else ""
        val quotedName = if (addElementName) quote + presentableName + quote else ""
        val addName = if (addElementName) " in " else ""
        val inName = if (!StringUtil.isEmpty(fileName) && addFilename) addName + fileName else ""
        DocumentationManagerUtil.createHyperlink(buffer, linkText, quotedName + inName, true, false)
    }

    fun findFunctionComment(functionAnchor: JSFunction?): PsiComment? {
        var anchor: PsiElement? = functionAnchor
        var docComment: PsiComment?
        if (functionAnchor is JSFunctionExpression) {
            docComment = findDocComment(functionAnchor)
            if (docComment is JSDocComment && findFunctionAppliedTo(docComment) === functionAnchor) {
                return docComment
            }
            val parent = functionAnchor.getParent()
            val grandparent = parent.parent
            anchor = if (parent is JSVariable &&
                    grandparent.node.findChildByType(JSElementTypes.BODY_VARIABLES) !== parent.getNode()) {
                parent
            } else {
                PsiTreeUtil.getParentOfType(functionAnchor, JSStatement::class.java, JSProperty::class.java)
            }
        }

        if (anchor != null) {
            docComment = findDocComment(anchor)
            if (docComment is JSDocComment && findFunctionAppliedTo(docComment) === functionAnchor) {
                return docComment
            }
        }

        return null
    }

    fun findFunctionAppliedTo(comment: JSDocComment): JSFunction? {
        var parent = comment.parent
        return if (parent is JSFunction) {
            parent
        } else {
            if (parent is JSExpressionStatement) {
                parent = parent.expression
            }
            if (parent is JSVarStatement) {
                parent = PsiTreeUtil.getNextSiblingOfType(comment, JSVariable::class.java)
            }
            val assignedExpression = JSPsiImplUtils.getAssignedExpression(parent as PsiElement)
            if (assignedExpression is JSFunction) {
                assignedExpression
            } else {
                null
            }
        }
    }

    fun getTypeFromTrailingComment(function: JSFunction): JSType? {
        val lastCommentInFunctionBody = findTrailingCommentInFunctionBody(function)
        return if (lastCommentInFunctionBody != null) {
            var type = tryCreateTypeFromComment(lastCommentInFunctionBody.psi!!, false, true, false)
            if (type !is JSSpecialNamedTypeImpl) {
                type = JSTypeUtils.copyWithStrict(type, false)
            }
            type
        } else {
            null
        }
    }

    fun getTypeFromReturnTypeComment(function: JSFunction?): JSType? {
        val list = function!!.parameterList
        var type: JSType? = null
        if (list != null) {
            var reasonableNextElement = list.nextSibling
            if (reasonableNextElement is PsiWhiteSpace) {
                reasonableNextElement = reasonableNextElement.getNextSibling()
            }
            if (reasonableNextElement is JSDocComment) {
                type = tryCreateTypeFromComment(reasonableNextElement, false, true, false)
            }
        }

        return type
    }

    fun tryCreateTypeFromComment(comment: PsiComment, spaceAllowed: Boolean, allowEOLComment: Boolean,
            allowCommentAfterType: Boolean): JSType? {
        return if (!allowEOLComment && comment.node.elementType === JSTokenTypes.END_OF_LINE_COMMENT) {
            null
        } else {
            var body = unwrapCommentDelimiters(comment.text)
            if (allowEOLComment) {
                body = body.trim { it <= ' ' }
            }
            if (!isTypeStringAcceptable(body, spaceAllowed)) {
                null
            } else {
                val typeSource = JSTypeSourceFactory.createTypeSource(comment, true)
                JSTypeParser.createType(comment.project, body, typeSource, allowCommentAfterType)
            }
        }
    }

    private fun isTypeStringAcceptable(typeString: String, spaceAllowed: Boolean): Boolean {
        var realTypeString = typeString
        return if (realTypeString.indexOf(10.toChar()) != -1) {
            false
        } else if (!spaceAllowed && realTypeString.indexOf(32.toChar()) != -1) {
            false
        } else {
            realTypeString = realTypeString.trim { it <= ' ' }
            realTypeString != "TODO" && realTypeString != "INTERNAL" && realTypeString.isNotEmpty() &&
                    !realTypeString.endsWith(",") && !realTypeString.startsWith(",")
        }
    }

    fun findTypeFromComments(element: JSNamedElement?): JSType? {
        return if (element is JSVariable) {
            rawGetTypeForVariable(element)
        } else {
            if (element is JSFunction) {
                val type = getTypeFromTrailingComment(element)
                if (type != null) {
                    return type
                }
                val docComment = findDocCommentWider(element)
                if (docComment is JSDocComment) {
                    var returnType = docComment.returnType
                    if (returnType == null) {
                        returnType = docComment.type
                    }
                    if (returnType != null) {
                        return JSTypeParser.createTypeFromJSDoc(docComment.getProject(), returnType,
                                JSTypeSourceFactory.createTypeSource(element, true))
                    }
                }
            }
            null
        }
    }

    fun doCapitalizeCommentTypeIfNeeded(comment: String): String {
        var realComment = comment
        if (!StringUtil.isEmpty(realComment)) {
            if (realComment != "number" && realComment != "string" && realComment != "function" && realComment != "boolean" && realComment != "object" && realComment != "array") {
                if ("Integer" == realComment) {
                    realComment = "int"
                }
            } else {
                realComment = StringUtil.capitalize(realComment)
            }
        }
        return realComment
    }

    fun stripTypeDecorations(textFragment: String?): String? {
        var realTextFragment = textFragment
        return if (realTextFragment == null) {
            null
        } else {
            realTextFragment = realTextFragment.trim { it <= ' ' }
            realTextFragment = StringUtil.trimStart(realTextFragment, "...")
            realTextFragment = StringUtil.trimStart(realTextFragment, "!")
            var i = realTextFragment.indexOf(63.toChar())
            if (i != -1) {
                realTextFragment = if (i == 0) {
                    realTextFragment.substring(1)
                } else {
                    realTextFragment.substring(0, i)
                }
            }
            if (i < 1) {
                i = realTextFragment.indexOf(61.toChar())
                if (i != -1) {
                    realTextFragment = realTextFragment.substring(0, i)
                }
            }
            realTextFragment = StringUtil.trimEnd(realTextFragment, ".")
            if (realTextFragment.startsWith("(") && realTextFragment.endsWith(")")) {
                realTextFragment = realTextFragment.substring(1, realTextFragment.length - 1)
            }
            val commaIndex = realTextFragment.indexOf(44.toChar())
            if (commaIndex != -1 && realTextFragment.indexOf(123.toChar()) == -1) {
                realTextFragment = realTextFragment.substring(0, commaIndex).trim { it <= ' ' }
            }
            realTextFragment
        }
    }

    fun getLibDocRelativeUrl(baseUrl: String, elementFQN: String): String {
        val baseKey = JSExternalLibraryDocBundle.getBaseURLKey(baseUrl)
        return if (baseKey == null) {
            ""
        } else {
            val directUrl = JSExternalLibraryDocBundle.getElementUrl(baseKey, elementFQN)
            if (directUrl != null) {
                directUrl
            } else {
                val prefix = JSExternalLibraryDocBundle.getPrefix(baseKey)
                var relativeUrl = elementFQN
                val rulesStr = JSExternalLibraryDocBundle.getRules(baseKey)
                val rules = rulesStr.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val var9 = rules.size
                for (var10 in 0 until var9) {
                    val rule = rules[var10]
                    if (rule.trim { it <= ' ' }.length > 0) {
                        val args = rule.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val opType = args[0].trim { it <= ' ' }
                        val sample = if (args.size > 1) args[1].trim { it <= ' ' } else ""
                        val replacement = if (args.size > 2) args[2].trim { it <= ' ' } else ""
                        if (opType.startsWith("S")) {
                            relativeUrl = relativeUrl.replace(sample, replacement)
                        } else if (opType.startsWith("R")) {
                            relativeUrl = relativeUrl.replace(sample.toRegex(), replacement)
                        } else if (opType.startsWith("L")) {
                            relativeUrl = StringUtil.toLowerCase(relativeUrl)
                        } else {
                            assert(false) { "Don't know how to handle $opType in rule $rule must be R or S" }
                        }
                    }
                }
                if (JSExternalLibraryDocBundle.isLowerCase(baseKey)) {
                    relativeUrl = StringUtil.toLowerCase(relativeUrl)
                }
                prefix + relativeUrl
            }
        }
    }

    private class DocTagBuilder(var type: MetaDocType) {

        var matchName: String? = null

        var matchValue: String? = null

        var lastParameterName: String? = null

        var breakEnd = false

        var continueType = false

        fun toDocTag(): DocTag {
            return DocTag(type, matchName!!, matchValue!!)
        }

    }

    class DocTag(val type: MetaDocType, val matchName: String, val matchValue: String)

    class JSTagToParameterMap(private val myMatchedTags: Int2ObjectMap<JSParameterListElement>,
            private val myNonMatchedTags: Int2ObjectMap<JSDocTag>, private val myRestDocTagIndex: Int) {

        fun getMatchedTags(): Int2ObjectMap<JSParameterListElement> {
            return myMatchedTags
        }

        fun getNonMatchedTags(): Int2ObjectMap<JSDocTag> {
            return myNonMatchedTags
        }

        fun getRestDocTagIndex(): Int {
            return myRestDocTagIndex
        }

        fun getTagForParameter(parameter: JSParameterListElement): Int {
            val var2 = Int2ObjectMaps.fastIterable(
                    myMatchedTags).iterator()

            var entry: Int2ObjectMap.Entry<JSParameterListElement>
            var element: JSParameterListElement
            do {
                if (!var2.hasNext()) {
                    return -1
                }
                entry = var2.next()
                element = entry.value as JSParameterListElement
            } while (parameter != element &&
                    (element !is JSParameter || parameter != getOuterParameterForRestPropertyParameter(element)))

            return entry.intKey
        }

        fun isEmpty(): Boolean {
            return myMatchedTags.isEmpty() && myNonMatchedTags.isEmpty() && myRestDocTagIndex == -1
        }

    }

    class JSDocParametersMappingToFunctionInfo(val paramsToAdd: List<Pair<Int, String>>,
            val paramsToRemove: Map<Int, String>, private val myHasParamTag: Boolean) {

        fun hasParamTag(): Boolean {
            return myHasParamTag
        }

    }

}