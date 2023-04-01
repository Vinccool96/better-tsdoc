package io.github.vinccool96.idea.bettertsdoc.documentation

import com.intellij.lang.typescript.documentation.TypeScriptQuickNavigateBuilder

class BetterTSQuickNavigateBuilder {

    private val base = TypeScriptQuickNavigateBuilder()

    enum class ObjectKind {
        SIMPLE_DECLARATION {
            override fun toPrefix(): String {
                return ""
            }
        },
        PROPERTY {
            override fun toPrefix(): String {
                return "(property) "
            }
        },
        PARAMETER {
            override fun toPrefix(): String {
                return "(parameter) "
            }
        },
        FUNCTION {
            override fun toPrefix(): String {
                return "(function) "
            }
        },
        METHOD {
            override fun toPrefix(): String {
                return "(method) "
            }
        },
        IMPORT_SPECIFIER {
            override fun toPrefix(): String {
                return "(named import) "
            }

            override fun toJSDocPrefix(): String {
                return "import "
            }
        },
        IMPORT_DEFAULT {
            override fun toPrefix(): String {
                return "(default import) "
            }

            override fun toJSDocPrefix(): String {
                return "import "
            }
        },
        IMPORT_ALL {
            override fun toPrefix(): String {
                return "(namespace import) "
            }
        },
        EXPORT {
            override fun toPrefix(): String {
                return "(export element) "
            }
        };

        abstract fun toPrefix(): String

        open fun toJSDocPrefix(): String {
            return "(property) "
        }
    }

    companion object {

        const val TIMEOUT_MILLIS = 500

    }

}