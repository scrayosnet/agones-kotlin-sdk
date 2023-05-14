package net.justchunks.agones.client

import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

internal class AgonesSdkFactoryTest {

    @Test
    @DisplayName("Should get an instance")
    fun shouldGetInstance() {
        // given
        val sdk = AgonesSdkFactory.createNewSdk()

        // then
        assertNotNull(sdk)

        // cleanup
        sdk.close()
    }

    @Test
    @DisplayName("Should get a new instance")
    fun shouldGetNewInstance() {
        // given
        val sdk1 = AgonesSdkFactory.createNewSdk()
        val sdk2 = AgonesSdkFactory.createNewSdk()

        // then
        assertNotEquals(sdk1, sdk2)

        // cleanup
        sdk1.close()
        sdk2.close()
    }
}
