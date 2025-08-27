package net.scrayos.agones.client

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.engine.config.IncludeTestScopeAffixes

class KotestConfig : AbstractProjectConfig() {
    override val includeTestScopeAffixes: IncludeTestScopeAffixes = IncludeTestScopeAffixes.ALWAYS
}
