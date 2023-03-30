package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import io.github.vinccool96.idea.bettertsdoc.BetterTSDocsTestCaseBase

class BetterTSDocDocumentationProviderTest : BetterTSDocsTestCaseBase() {

    fun testDocumentation() {
        myFixture.configureByFiles("DocumentationTestData.ts")
        val originalElement = myFixture.elementAtCaret

        var element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, originalElement.containingFile, originalElement)
        if (element == null) {
            element = originalElement;
        }
        val documentationProvider = DocumentationManager.getProviderFromElement(element)
        val generateDoc = documentationProvider.generateDoc(element, originalElement)
        val a = 1 + 2
    }

}