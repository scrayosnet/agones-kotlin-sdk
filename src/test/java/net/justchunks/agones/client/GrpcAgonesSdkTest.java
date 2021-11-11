package net.justchunks.agones.client;

import net.justchunks.agones.client.AgonesSdk.Alpha;
import net.justchunks.agones.client.AgonesSdk.Beta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Testcontainers
@Tag("integration")
@Disabled("The tests are not yet working")
class GrpcAgonesSdkTest {

    @Container
    private final GenericContainer<?> sdkConformanceContainer = new GenericContainer<>(
        new ImageFromDockerfile()
            .withFileFromPath("Dockerfile", Path.of("Dockerfile.agones-compliance"))
    )
        .withExposedPorts(9357)
        .waitingFor(Wait.forHealthcheck());
    private static ScheduledExecutorService executorService;


    private GrpcAgonesSdk sdk;


    @BeforeAll
    static void before() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void after() {
        executorService.shutdown();
    }

    @BeforeEach
    void beforeEach() {
        sdk = new GrpcAgonesSdk(executorService);
    }

    @AfterEach
    void afterEach() {
        sdk.shutdown();
    }


    @Test
    void ready() {
    }

    @Test
    void health() {
    }

    @Test
    void reserve() {
    }

    @Test
    void allocate() {
    }

    @Test
    void shutdown() {
    }

    @Test
    void label() {
    }

    @Test
    void annotation() {
    }

    @Test
    void gameServer() {
    }

    @Test
    void watchGameServer() {
    }

    @Test
    void startHealthTask() {
    }

    @Test
    void shouldReturnAlphaSdk() {
        // given
        Alpha alphaSdk = sdk.alpha();

        // then
        Assertions.assertNotNull(alphaSdk);
    }

    @Test
    void shouldReturnBetaSdk() {
        // given
        Beta betaSdk = sdk.beta();

        // then
        Assertions.assertNotNull(betaSdk);
    }

    @Nested
    class AlphaTest {

        @Test
        void playerConnect() {
        }

        @Test
        void playerDisconnect() {
        }

        @Test
        void connectedPlayers() {
        }

        @Test
        void isPlayerConnected() {
        }

        @Test
        void playerCount() {
        }

        @Test
        void setPlayerCapacity() {
        }

        @Test
        void getPlayerCapacity() {
        }
    }

    @Nested
    class BetaTest {

    }
}
