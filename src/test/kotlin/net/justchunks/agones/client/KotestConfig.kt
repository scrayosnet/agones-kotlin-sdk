package net.justchunks.agones.client

import io.kotest.core.config.AbstractProjectConfig

class KotestConfig : AbstractProjectConfig() {
    override val includeTestScopePrefixes: Boolean = true
}
