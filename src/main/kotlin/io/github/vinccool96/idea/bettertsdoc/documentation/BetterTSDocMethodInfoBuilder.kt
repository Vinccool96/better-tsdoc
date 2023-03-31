package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSFunctionItem
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList

class BetterTSDocMethodInfoBuilder : BetterTSDocSymbolInfoBuilder() {

    val parameterInfoMap: MutableMap<String?, BetterTSDocParameterInfoBuilder> = LinkedHashMap()

    val returnInfo = BetterTSDocBuilderSimpleInfo()

    val throwsInfos: MutableSet<BetterTSDocBuilderSimpleInfo> = LinkedHashSet()

    val firesInfos: MutableList<BetterTSDocBuilderSimpleInfo> = SmartList()

    var methodType: String? = null

    fun getInfoForParameterName(name: String?): BetterTSDocParameterInfoBuilder {
        var parameterInfo = parameterInfoMap[name]

        if (parameterInfo != null) {
            return parameterInfo
        } else {
            val var3 = parameterInfoMap.values.iterator()
            var info: BetterTSDocParameterInfoBuilder
            do {
                if (!var3.hasNext()) {
                    parameterInfo = BetterTSDocParameterInfoBuilder()
                    parameterInfoMap[name] = parameterInfo

                    return parameterInfo
                }
                info = var3.next()
            } while (!StringUtil.equals(name, info.docName))

            return info
        }
    }

    fun mergeSignatureWith(function: JSFunctionItem, superFunction: JSFunction,
            superMethodInfo: BetterTSDocMethodInfoBuilder) {
        val unmatchedSuperFunctionParameters: MutableMap<String, BetterTSDocParameterInfoBuilder> = HashMap()
        val var5 = superMethodInfo.parameterInfoMap.entries.iterator()

        while (var5.hasNext()) {
            val (key, value) = var5.next()
            val originalParameterInfo = parameterInfoMap[key]
            if (originalParameterInfo == null) {
                unmatchedSuperFunctionParameters[key as String] = value
            } else if (!originalParameterInfo.hasDescription) {
                originalParameterInfo.mergeRawDescriptionAndPlaceHolders(value)
            }
        }

        val superFunctionParameters = superFunction.parameters
        val parameters = function.parameters

        for (i in superFunctionParameters.indices) {
            val superParameter = superFunctionParameters[i]
            val superParameterInfo = unmatchedSuperFunctionParameters[superParameter.name]
            val name = parameters[i].name
            if (superParameterInfo != null && name != null) {
                parameterInfoMap[name] = superParameterInfo
            }
        }

        if (!returnInfo.hasDescription) {
            returnInfo.mergeRawDescriptionAndPlaceHolders(superMethodInfo.returnInfo)
        }
    }

}