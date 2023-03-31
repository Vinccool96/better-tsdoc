package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.javascript.psi.JSParameterTypeDecorator

class BetterTSDocParameterInfoBuilder : BetterTSDocBuilderSimpleInfo() {

    var optional = false

    var rest = false

    var initialValue: String? = null

    var defaultValue: String? = null

    var docName: String? = null

    var optionsMap: MutableMap<String, BetterTSDocParameterInfoBuilder>? = null

    fun updateFromDecorator(decorator: JSParameterTypeDecorator) {
        jsType = decorator.simpleType
        optional = decorator.isOptional
        rest = decorator.isRest
    }

}