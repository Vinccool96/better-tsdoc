package io.github.vinccool96.idea.bettertsdoc

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class BetterTSDocsTestCaseBase : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

}