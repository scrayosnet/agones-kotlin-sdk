package net.justchunks.agones.client;

import agones.dev.sdk.Sdk.GameServer;
import net.justchunks.agones.client.AgonesSdk.Alpha;
import net.justchunks.agones.client.AgonesSdk.Beta;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@SuppressWarnings({"PatternValidation", "ConstantConditions", "ResultOfMethodCallIgnored"})
class GrpcAgonesSdkTest {

    private static final int GRPC_PORT = 9357;
    private static final int HTTP_PORT = 9358;
    private static final int WAIT_TIMEOUT_MILLIS = 10_000;
    private static final String META_PREFIX = "agones.dev/sdk-";


    private static ScheduledExecutorService executorService;


    @Container
    private GenericContainer<?> sdkContainer = new GenericContainer<>(
        DockerImageName.parse("gcr.io/agones-images/agones-sdk:1.18.0")
    )
        .withCommand(
            "--local",
            "--address=0.0.0.0",
            "--sdk-name=AgonesClientSDK",
            "--grpc-port=" + GRPC_PORT,
            "--http-port=" + HTTP_PORT,
            "--feature-gates=PlayerTracking=true"
        )
        .withExposedPorts(GRPC_PORT, HTTP_PORT)
        .waitingFor(
            Wait
                .forHttp("/")
                .forPort(HTTP_PORT)
                .forStatusCode(404)
        );
    private GrpcAgonesSdk sdk;
    private WaitingConsumer logConsumer;


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
        sdk = new GrpcAgonesSdk(
            executorService,
            sdkContainer.getHost(),
            sdkContainer.getMappedPort(GRPC_PORT)
        );
        logConsumer = new WaitingConsumer();
        sdkContainer.followOutput(logConsumer);
    }

    @AfterEach
    void afterEach() {
        sdk.close();
    }


    @Test
    @DisplayName("Should be ready after #ready()")
    void shouldBeReady() throws TimeoutException {
        // when
        sdk.ready();

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Ready request has been received!"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        Assertions.assertTrue(containsLogLine("Ready request has been received!"));
        Assertions.assertEquals("Ready", sdk.gameServer().getStatus().getState());
    }

    @Test
    @DisplayName("Should send health ping")
    void shouldSendHealthPing() throws TimeoutException {
        // when
        sdk.health();

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Health stream closed."),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        Assertions.assertTrue(containsLogLine("Health Ping Received!"));
        Assertions.assertTrue(containsLogLine("Health stream closed."));
    }

    @ParameterizedTest(name = "#reserve({0})")
    @ValueSource(ints = {0, 10, 13423})
    @DisplayName("Should be reserved after reserve(int)")
    void shouldBeReserved(int seconds) throws TimeoutException, ParseException {
        // when
        sdk.reserve(seconds);

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Reserve request has been received!"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        String logLine = getLogLine("Reserve request has been received!").orElseThrow();
        JSONObject durationObject = (JSONObject) ((JSONObject) new JSONParser().parse(logLine)).get("duration");
        Assertions.assertEquals("Reserved", sdk.gameServer().getStatus().getState());
        if (seconds == 0) {
            Assertions.assertNull(durationObject.get("seconds"));
        } else {
            Assertions.assertEquals(seconds, (long) durationObject.get("seconds"));
        }
    }

    @ParameterizedTest(name = "#reserve({0})")
    @ValueSource(ints = {-1, -10, -12312})
    @DisplayName("Should throw IAE on negative reserve seconds")
    void shouldThrowOnNegativeReserveSeconds(int seconds) {
        // when, then
        Assertions.assertThrows(IllegalArgumentException.class, () -> sdk.reserve(seconds));
    }

    @Test
    @DisplayName("Should be allocated after #allocate()")
    void shouldBeAllocated() throws TimeoutException {
        // when
        sdk.allocate();

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Allocate request has been received!"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        Assertions.assertTrue(containsLogLine("Allocate request has been received!"));
        Assertions.assertEquals("Allocated", sdk.gameServer().getStatus().getState());
    }

    @Test
    @DisplayName("Should be shut down after #shutdown()")
    void shouldBeShutDown() throws TimeoutException {
        // when
        sdk.shutdown();

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Shutdown request has been received!"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        Assertions.assertTrue(containsLogLine("Shutdown request has been received!"));
        Assertions.assertEquals("Shutdown", sdk.gameServer().getStatus().getState());
    }

    @ParameterizedTest(name = "#label(\"{0}\", \"{1}\")")
    @CsvSource({"valid_key,example", "example-key,anotherexample", "very1ongk3y,___"})
    @DisplayName("Should set label")
    void shouldSetLabel(String key, String value) throws TimeoutException, ParseException {
        // when
        sdk.label(key, value);

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Setting label"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        String logLine = getLogLine("Setting label").orElseThrow();
        JSONObject valuesObject = (JSONObject) ((JSONObject) new JSONParser().parse(logLine)).get("values");
        GameServer gameServer = sdk.gameServer();
        Assertions.assertEquals(key, valuesObject.get("key"));
        Assertions.assertTrue(gameServer.getObjectMeta().getLabelsMap().containsKey(META_PREFIX + key));
        Assertions.assertEquals(value, valuesObject.get("value"));
        Assertions.assertTrue(gameServer.getObjectMeta().getLabelsMap().containsValue(value));
    }

    @ParameterizedTest(name = "#label(\"{0}\", \"example\")")
    @ValueSource(strings = {"", "_", "ae-", "-ae"})
    @DisplayName("Should throw IAE on invalid label key")
    void shouldThrowOnInvalidLabelKey(String key) {
        // when, then
        Assertions.assertThrows(IllegalArgumentException.class, () -> sdk.label(key, "example"));
    }

    @ParameterizedTest(name = "#annotation(\"{0}\", \"{1}\")")
    @CsvSource({"valid_key,example", "example-key,anotherexample", "very1ongk3y,___"})
    @DisplayName("Should set annotation")
    void shouldSetAnnotation(String key, String value) throws TimeoutException, ParseException {
        // when
        sdk.annotation(key, value);

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Setting annotation"),
            WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        String logLine = getLogLine("Setting annotation").orElseThrow();
        JSONObject valuesObject = (JSONObject) ((JSONObject) new JSONParser().parse(logLine)).get("values");
        GameServer gameServer = sdk.gameServer();
        Assertions.assertEquals(key, valuesObject.get("key"));
        Assertions.assertTrue(gameServer.getObjectMeta().getAnnotationsMap().containsKey(META_PREFIX + key));
        Assertions.assertEquals(value, valuesObject.get("value"));
        Assertions.assertTrue(gameServer.getObjectMeta().getAnnotationsMap().containsValue(value));
    }

    @ParameterizedTest(name = "#annotation(\"{0}\", \"example\")")
    @ValueSource(strings = {"", "_", "ae-", "-ae"})
    @DisplayName("Should throw IAE on invalid annotation key")
    void shouldThrowOnInvalidAnnotationKey(String key) {
        // when, then
        Assertions.assertThrows(IllegalArgumentException.class, () -> sdk.annotation(key, "example"));
    }

    @Test
    @DisplayName("Should get GameServer")
    void shouldGetGameServer() {
        // when
        GameServer gameServer = sdk.gameServer();

        // then
        Assertions.assertNotNull(gameServer);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should not receive initial GameServer update")
    void shouldNotReceiveInitialUpdate() throws TimeoutException {
        // given
        Consumer<GameServer> gameServerConsumer = (Consumer<GameServer>) mock(Consumer.class);

        // when
        sdk.watchGameServer(gameServerConsumer);

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Connected to watch GameServer..."),
            (int) AgonesSdk.HEALTH_PING_INTERVAL.toMillis() + WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        verify(gameServerConsumer, timeout(WAIT_TIMEOUT_MILLIS).times(0)).accept(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should receive GameServer updates")
    void shouldReceiveUpdates() throws TimeoutException {
        // given
        String labelKey = "valid_key";
        String labelValue = "valid_value";
        Consumer<GameServer> gameServerConsumer = (Consumer<GameServer>) mock(Consumer.class);
        ArgumentCaptor<GameServer> captor = ArgumentCaptor.forClass(GameServer.class);

        // when
        sdk.watchGameServer(gameServerConsumer);

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Connected to watch GameServer..."),
            (int) AgonesSdk.HEALTH_PING_INTERVAL.toMillis() + WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // when
        sdk.label(labelKey, labelValue);

        // then
        verify(gameServerConsumer, timeout(WAIT_TIMEOUT_MILLIS)).accept(captor.capture());
        System.out.println(sdkContainer.getLogs());
        Map<String, String> labelMap = captor.getValue().getObjectMeta().getLabelsMap();
        Assertions.assertTrue(labelMap.containsKey(META_PREFIX + labelKey));
        Assertions.assertTrue(labelMap.containsValue(labelValue));
    }

    @Test
    @DisplayName("Should throw NPE on #watchGameServer(null)")
    void shouldThrowOnNullCallbackObserver() {
        // when, then
        Assertions.assertThrows(NullPointerException.class, () -> sdk.watchGameServer(null));
    }

    @Test
    @DisplayName("Should schedule health task")
    void shouldScheduleHealthTask() {
        // given
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        AgonesSdk testSdk = new GrpcAgonesSdk(
            executorService,
            sdkContainer.getHost(),
            sdkContainer.getMappedPort(GRPC_PORT)
        );

        // when
        testSdk.startHealthTask();

        // then
        verify(executorService, times(1)).scheduleAtFixedRate(
            any(),
            eq(0L),
            eq(AgonesSdk.HEALTH_PING_INTERVAL.toMillis()),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("Health task should trigger health pings")
    void healthTaskShouldTriggerHealthPings() throws TimeoutException {
        // when
        sdk.startHealthTask();

        // wait
        logConsumer.waitUntil(
            frame -> frame.getUtf8String().contains("Health Ping Received!"),
            (int) AgonesSdk.HEALTH_PING_INTERVAL.toMillis() + WAIT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS
        );

        // then
        Assertions.assertTrue(containsLogLine("Health Ping Received!"));
        Assertions.assertFalse(containsLogLine("Health stream closed."));
    }

    @Test
    @DisplayName("Health task should trigger health pings")
    void shouldThrowOnDuplicateStartHealthTask() {
        // when
        sdk.startHealthTask();

        // then
        Assertions.assertThrows(IllegalStateException.class, () -> sdk.startHealthTask());
    }

    @Test
    @DisplayName("Close should be idempotent")
    void closeShouldBeIdempotent() {
        // when
        sdk.close();

        // then
        Assertions.assertDoesNotThrow(() -> sdk.close());
    }

    @Test
    @DisplayName("Close should refresh interrupted flag")
    void closeShouldBeInterruptable() {
        // given
        Thread.currentThread().interrupt();

        // when
        sdk.close();

        // then
        Assertions.assertTrue(Thread.interrupted());
    }

    @Test
    @DisplayName("Should return valid Alpha channel")
    void shouldReturnAlphaSdk() {
        // given
        Alpha alphaSdk = sdk.alpha();

        // then
        Assertions.assertNotNull(alphaSdk);
    }

    @Test
    @DisplayName("Should return valid Beta channel")
    void shouldReturnBetaSdk() {
        // given
        Beta betaSdk = sdk.beta();

        // then
        Assertions.assertNotNull(betaSdk);
    }

    @Nested
    class AlphaTest {

        @BeforeEach
        void beforeEach() throws TimeoutException {
            // obtain an alpha sdk
            Alpha alphaSdk = sdk.alpha();

            // when
            alphaSdk.playerCapacity(64);

            // wait
            logConsumer.waitUntil(
                frame -> frame.getUtf8String().contains("Setting Player Capacity"),
                WAIT_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            );
        }


        @Test
        @DisplayName("Should connect player")
        void shouldConnectPlayer() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when, then
            Assertions.assertTrue(alphaSdk.playerConnect(playerId));
            Assertions.assertTrue(alphaSdk.isPlayerConnected(playerId));
        }

        @Test
        @DisplayName("Should throw ISE on player connect above capacity limit")
        void shouldThrowOnConnectPlayerOnCapacityLimit() throws TimeoutException {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when
            alphaSdk.playerCapacity(0);

            // wait
            logConsumer.waitUntil(
                frame -> frame.getUtf8String().contains("Setting Player Capacity"),
                WAIT_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            );

            // when, then
            Assertions.assertThrows(IllegalStateException.class, () -> alphaSdk.playerConnect(playerId));
        }

        @Test
        @DisplayName("Should rethrow normal exception on error during player connect")
        void shouldRethrowOnConnectPlayerException() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when
            sdk.close();

            // when, then
            Assertions.assertNotEquals(
                IllegalStateException.class,
                Assertions.assertThrows(Throwable.class, () -> alphaSdk.playerConnect(playerId)).getClass()
            );
        }

        @Test
        @DisplayName("Should not connect already connected player")
        void shouldNotConnectAlreadyConnectedPlayer() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when, then
            Assertions.assertTrue(alphaSdk.playerConnect(playerId));
            Assertions.assertFalse(alphaSdk.playerConnect(playerId));
        }

        @Test
        @DisplayName("Should throw NPE on connect null player ID")
        void shouldThrowOnConnectNullPlayerId() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertThrows(NullPointerException.class, () -> alphaSdk.playerConnect(null));
        }

        @Test
        @DisplayName("Should disconnect player")
        void shouldDisconnectPlayer() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when
            alphaSdk.playerConnect(playerId);

            // then
            Assertions.assertTrue(alphaSdk.playerDisconnect(playerId));
        }

        @Test
        @DisplayName("Should not disconnect unconnected player")
        void shouldNotDisconnectPlayerIfNotConnected() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when, then
            Assertions.assertFalse(alphaSdk.playerDisconnect(playerId));
        }

        @Test
        @DisplayName("Should throw NPE on disconnect null player ID")
        void shouldThrowOnDisconnectNullPlayerId() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertThrows(NullPointerException.class, () -> alphaSdk.playerDisconnect(null));
        }

        @Test
        @DisplayName("Should get empty connected players")
        void shouldGetConnectedPlayersEmpty() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertEquals(0, alphaSdk.connectedPlayers().size());
        }

        @Test
        @DisplayName("Should get populated connected players")
        void shouldGetConnectedPlayersPopulated() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId1 = UUID.randomUUID();
            UUID playerId2 = UUID.randomUUID();
            UUID playerId3 = UUID.randomUUID();

            // when
            alphaSdk.playerConnect(playerId1);
            alphaSdk.playerConnect(playerId2);
            alphaSdk.playerConnect(playerId3);

            // then
            Assertions.assertEquals(3, alphaSdk.connectedPlayers().size());
            List<UUID> connectedPlayers = alphaSdk.connectedPlayers();
            Assertions.assertTrue(connectedPlayers.contains(playerId1));
            Assertions.assertTrue(connectedPlayers.contains(playerId2));
            Assertions.assertTrue(connectedPlayers.contains(playerId3));
        }

        @Test
        @DisplayName("Should get connected player")
        void shouldGetConnectedPlayer() {
            // given
            Alpha alphaSdk = sdk.alpha();
            UUID playerId = UUID.randomUUID();

            // when
            alphaSdk.playerConnect(playerId);

            // then
            Assertions.assertTrue(alphaSdk.isPlayerConnected(playerId));
        }

        @Test
        @DisplayName("Should not get disconnected player")
        void shouldNotGetDisconnectedPlayer() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertFalse(alphaSdk.isPlayerConnected(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Should throw NPE on null player ID")
        void shouldThrowOnConnectedNullPlayerId() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertThrows(NullPointerException.class, () -> alphaSdk.isPlayerConnected(null));
        }

        @Test
        @DisplayName("Should get player count")
        void shouldGetPlayerCount() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when
            long playerCount = alphaSdk.playerCount();

            // then
            Assertions.assertEquals(0L, playerCount);
        }

        @ParameterizedTest(name = "#playerCapacity({0})")
        @ValueSource(longs = {0L, 10L, 1337L, 100000L})
        @DisplayName("Should set player capacity")
        void shouldSetPlayerCapacity(long capacity) throws TimeoutException {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when
            alphaSdk.playerCapacity(capacity);

            // wait
            logConsumer.waitUntil(
                frame -> frame.getUtf8String().contains("Setting Player Capacity"),
                WAIT_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            );

            // then
            Assertions.assertEquals(capacity, alphaSdk.playerCapacity());
        }

        @ParameterizedTest(name = "#playerCapacity({0})")
        @ValueSource(longs = {-1L, -100L, -1337L, -100000L})
        @DisplayName("Should throw IAE on negative player capacity")
        void shouldThrowOnNegativeCapacity(long capacity) {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when, then
            Assertions.assertThrows(IllegalArgumentException.class, () -> alphaSdk.playerCapacity(capacity));
        }

        @Test
        @DisplayName("Should get player capacity")
        void shouldGetPlayerCapacity() {
            // given
            Alpha alphaSdk = sdk.alpha();

            // when
            long playerCapacity = alphaSdk.playerCapacity();

            // then
            Assertions.assertEquals(64L, playerCapacity);
        }
    }

    @Nested
    class BetaTest {
        // no tests so for beta far
    }


    private boolean containsLogLine(String logMessagePart) {
        return getLogLine(logMessagePart).isPresent();
    }

    private Optional<String> getLogLine(String logMessagePart) {
        for (final String line : sdkContainer.getLogs().split("\\n")) {
            if (line.contains(logMessagePart)) {
                return Optional.of(line);
            }
        }

        return Optional.empty();
    }
}
