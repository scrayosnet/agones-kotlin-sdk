package net.justchunks.agones.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

class AgonesSdkFactoryTest {

    private static ScheduledExecutorService executorService;


    @BeforeAll
    static void before() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void after() {
        executorService.shutdown();
    }


    @Test
    @DisplayName("Should get an instance")
    void shouldGetInstance() {
        // given
        AgonesSdk sdk = AgonesSdkFactory.createNewSdk(executorService);

        // then
        Assertions.assertNotNull(sdk);

        // cleanup
        sdk.close();
    }

    @Test
    @DisplayName("Should get a new instance")
    void shouldGetNewInstance() {
        // given
        AgonesSdk sdk1 = AgonesSdkFactory.createNewSdk(executorService);
        AgonesSdk sdk2 = AgonesSdkFactory.createNewSdk(executorService);

        // then
        Assertions.assertNotEquals(sdk1, sdk2);

        // cleanup
        sdk1.close();
        sdk2.close();
    }
}
