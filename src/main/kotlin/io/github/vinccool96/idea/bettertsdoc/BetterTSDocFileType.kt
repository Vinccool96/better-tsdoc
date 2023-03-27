package io.github.vinccool96.idea.bettertsdoc

import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.openapi.fileTypes.LanguageFileType
import icons.JavaScriptPsiIcons
import javax.swing.Icon

class BetterTSDocFileType : LanguageFileType(JavaScriptSupportLoader.TYPESCRIPT) {

    override fun getName(): String {
        return "Typescript"
    }

    override fun getDescription(): String {
        return JavaScriptBundle.message("filetype.typescript.description")
    }

    override fun getDefaultExtension(): String {
        return "ts"
    }

    override fun getIcon(): Icon {
        return JavaScriptPsiIcons.FileTypes.TypeScriptFile
    }

}