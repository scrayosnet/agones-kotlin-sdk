package net.justchunks.agones.client

import agones.dev.sdk.Sdk.GameServer
import io.grpc.Status
import io.grpc.StatusException
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import net.justchunks.agones.client.AgonesSdk.Alpha
import net.justchunks.agones.client.AgonesSdk.Companion.METADATA_KEY_PREFIX
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
internal class GrpcAgonesSdkTest {

    @Container
    private val sdkContainer: GenericContainer<*> = GenericContainer(
        DockerImageName.parse("us-docker.pkg.dev/agones-images/release/agones-sdk:1.31.0")
    )
        .withCommand(
            "--local",
            "--address=0.0.0.0",
            "--sdk-name=AgonesClientSDK",
            "--grpc-port=$GRPC_PORT",
            "--http-port=$HTTP_PORT",
            "--feature-gates=PlayerTracking=true&CountsAndLists=true"
        )
        .withExposedPorts(GRPC_PORT, HTTP_PORT)
        .waitingFor(
            Wait
                .forHttp("/")
                .forPort(HTTP_PORT)
                .forStatusCode(404)
        )
    private lateinit var sdk: GrpcAgonesSdk
    private lateinit var logConsumer: WaitingConsumer

    @BeforeEach
    fun beforeEach() {
        sdk = GrpcAgonesSdk(
            sdkContainer.host,
            sdkContainer.getMappedPort(GRPC_PORT)
        )
        logConsumer = WaitingConsumer()
        sdkContainer.followOutput(logConsumer)
    }

    @AfterEach
    fun afterEach() {
        sdk.close()
    }

    @Test
    @DisplayName("Should have default values for construction")
    fun shouldHaveDefaultConstructionValues() {
        // when
        val testSdk = GrpcAgonesSdk()

        // then
        assertEquals("127.0.0.1", testSdk.host)
        assertEquals(9357, testSdk.port)

        // cleanup
        testSdk.close()
    }

    @Test
    @DisplayName("Should be ready after #ready()")
    fun shouldBeReady() = runTest {
        // when
        sdk.ready()

        // then
        assertTrue(containsLogLine("Ready request has been received!", WAIT_TIMEOUT_MILLIS))
        assertEquals("Ready", sdk.gameServer().status.state)
    }

    @Test
    @DisplayName("Should send health ping")
    fun shouldSendHealthPing() = runTest {
        // when
        sdk.health()

        // then
        assertTrue(containsLogLine("Health stream closed.", WAIT_TIMEOUT_MILLIS))
        assertTrue(containsLogLine("Health Ping Received!", count = 1))
    }

    @Test
    @DisplayName("Should send no health ping")
    fun shouldSendNoHealthPing() = runTest {
        // when
        sdk.health(flowOf())

        // then
        assertTrue(containsLogLine("Health stream closed.", WAIT_TIMEOUT_MILLIS))
        assertTrue(containsLogLine("Health Ping Received!", count = 0))
    }

    @ParameterizedTest(name = "#health({0})")
    @ValueSource(ints = [1, 2, 3, 4, 5, 10, 11, 100])
    @DisplayName("Should send consecutive health pings")
    fun shouldSendHealthPings(pings: Int) = runTest {
        // given
        val flowValues = mutableListOf<Unit>()
        for (i in 0 until pings) {
            flowValues.add(Unit)
        }

        // when
        sdk.health(flowOf(*flowValues.toTypedArray()))

        // then
        assertTrue(containsLogLine("Health stream closed.", WAIT_TIMEOUT_MILLIS))
        assertTrue(containsLogLine("Health Ping Received!", count = pings))
    }

    @ParameterizedTest(name = "#reserve({0})")
    @ValueSource(ints = [0, 10, 13423, 1000000])
    @DisplayName("Should be reserved after reserve(int)")
    fun shouldBeReserved(seconds: Int) = runTest {
        // when
        sdk.reserve(seconds.toLong())

        // then
        val logLine: String = getLogLines("Reserve request has been received!", WAIT_TIMEOUT_MILLIS).first()
        val durationObject = (JSONParser().parse(logLine) as JSONObject)["duration"] as JSONObject
        assertEquals("Reserved", sdk.gameServer().status.state)
        if (seconds == 0) {
            assertNull(durationObject["seconds"])
        } else {
            assertEquals(seconds.toLong(), durationObject["seconds"] as Long)
        }
    }

    @ParameterizedTest(name = "#reserve({0})")
    @ValueSource(ints = [-1, -10, -12312, -1000000])
    @DisplayName("Should throw IAE on negative reserve seconds")
    fun shouldThrowOnNegativeReserveSeconds(seconds: Int) = runTest {
        // when, then
        assertFailsWith<IllegalArgumentException> {
            sdk.reserve(seconds.toLong())
        }
    }

    @Test
    @DisplayName("Should be allocated after #allocate()")
    fun shouldBeAllocated() = runTest {
        // when
        sdk.allocate()

        // then
        assertTrue(containsLogLine("Allocate request has been received!", WAIT_TIMEOUT_MILLIS))
        assertEquals("Allocated", sdk.gameServer().status.state)
    }

    @Test
    @DisplayName("Should be shut down after #shutdown()")
    fun shouldBeShutDown() = runTest {
        // when
        sdk.shutdown()

        // then
        assertTrue(containsLogLine("Shutdown request has been received!", WAIT_TIMEOUT_MILLIS))
        assertEquals("Shutdown", sdk.gameServer().status.state)
    }

    @ParameterizedTest(name = "#label(\"{0}\", \"{1}\")")
    @CsvSource("valid_key,example", "example-key,anotherexample", "very1ongk3y,___")
    @DisplayName("Should set label")
    fun shouldSetLabel(key: String, value: String?) = runTest {
        // when
        sdk.label(key, value!!)

        // then
        val logLine: String = getLogLines("Setting label", WAIT_TIMEOUT_MILLIS).first()
        val valuesObject = (JSONParser().parse(logLine) as JSONObject)["values"] as JSONObject
        val gameServer: GameServer = sdk.gameServer()
        assertEquals(key, valuesObject["key"])
        assertTrue(gameServer.objectMeta.labelsMap.containsKey(METADATA_KEY_PREFIX + key))
        assertEquals(value, valuesObject["value"])
        assertTrue(gameServer.objectMeta.labelsMap.containsValue(value))
    }

    @ParameterizedTest(name = "#label(\"{0}\", \"example\")")
    @ValueSource(strings = ["", "_", "ae-", "-ae"])
    @DisplayName("Should throw IAE on invalid label key")
    fun shouldThrowOnInvalidLabelKey(key: String?) = runTest {
        // when, then
        assertFailsWith<IllegalArgumentException> {
            sdk.label(key!!, "example")
        }
    }

    @ParameterizedTest(name = "#annotation(\"{0}\", \"{1}\")")
    @CsvSource("valid_key,example", "example-key,anotherexample", "very1ongk3y,___")
    @DisplayName("Should set annotation")
    fun shouldSetAnnotation(key: String, value: String?) = runTest {
        // when
        sdk.annotation(key, value!!)

        // then
        val logLine: String = getLogLines("Setting annotation", WAIT_TIMEOUT_MILLIS).first()
        val valuesObject = (JSONParser().parse(logLine) as JSONObject)["values"] as JSONObject
        val gameServer: GameServer = sdk.gameServer()
        assertEquals(key, valuesObject["key"])
        assertTrue(gameServer.objectMeta.annotationsMap.containsKey(METADATA_KEY_PREFIX + key))
        assertEquals(value, valuesObject["value"])
        assertTrue(gameServer.objectMeta.annotationsMap.containsValue(value))
    }

    @ParameterizedTest(name = "#annotation(\"{0}\", \"example\")")
    @ValueSource(strings = ["", "_", "ae-", "-ae"])
    @DisplayName("Should throw IAE on invalid annotation key")
    fun shouldThrowOnInvalidAnnotationKey(key: String?) = runTest {
        // when, then
        assertFailsWith<IllegalArgumentException> {
            sdk.annotation(key!!, "example")
        }
    }

    @Test
    @DisplayName("Should get GameServer")
    fun shouldGetGameServer() = runTest {
        // when
        val gameServer: GameServer = sdk.gameServer()

        // then
        assertNotNull(gameServer)
        assertTrue(gameServer.objectMeta.name == "local")
    }

    @Test
    @DisplayName("Should receive initial GameServer update")
    fun shouldReceiveInitialUpdate() = runTest {
        // when
        val updates: Flow<GameServer> = sdk.watchGameServer()

        // then
        updates.take(1).collect {
            assertTrue(it.objectMeta.name == "local")
        }
    }

    @ParameterizedTest(name = "{0} updates")
    @ValueSource(ints = [1, 2, 3, 4, 10])
    @DisplayName("Should receive GameServer updates")
    fun shouldReceiveUpdates(count: Int) = runTest {
        // given
        val labelKey = "valid_key"
        val labelValue = "valid_value"
        val updates: Flow<GameServer> = sdk.watchGameServer()

        // when, then
        var iteration = 0
        updates.take(count + 1).collectIndexed { key, value ->
            assertTrue(value.objectMeta.name == "local")
            val labelMap = value.objectMeta.labelsMap
            assertEquals(iteration + 1, labelMap.size)
            if (key < count) {
                sdk.label(labelKey + key, labelValue + key)
            } else {
                for (i in 0 until count) {
                    assertContains(labelMap, METADATA_KEY_PREFIX + labelKey + i)
                    assertContains(labelMap.values, labelValue + i)
                }
            }
            iteration++
        }
    }

    @Test
    @DisplayName("Close should be idempotent")
    fun closeShouldBeIdempotent() {
        // when
        sdk.close()

        // then
        sdk.close()
    }

    @Test
    @DisplayName("Should throw on any method if channel is closed")
    fun shouldThrowIfChannelIsClosed() = runTest {
        // given
        sdk.close()

        // then
        val exception = assertFailsWith<StatusException> {
            sdk.ready()
        }
        assertEquals(Status.UNAVAILABLE.code, exception.status.code)
        assertEquals("Channel shutdown invoked", exception.status.description)
    }

    @Test
    @DisplayName("Close should refresh interrupted flag")
    fun closeShouldBeInterruptable() {
        // given
        Thread.currentThread().interrupt()

        // when
        sdk.close()

        // then
        assertTrue(Thread.interrupted())
    }

    @Test
    @DisplayName("Automatic port should fall back to default port")
    fun automaticPortShouldFallBack() {
        // when
        val defaultPort: Int = GrpcAgonesSdk.AGONES_SDK_PORT

        // then
        assertEquals(GRPC_PORT, defaultPort)
    }

    @Test
    @DisplayName("Should return valid Alpha channel")
    fun shouldReturnAlphaSdk() {
        // given
        val alphaSdk: Alpha = sdk.alpha()

        // then
        assertNotNull(alphaSdk)
    }

    @Test
    @DisplayName("Should return same Alpha channel every time")
    fun shouldReturnSameAlphaSdk() {
        // given
        val alphaSdk1: Alpha = sdk.alpha()
        val alphaSdk2: Alpha = sdk.alpha()

        // then
        assertEquals(alphaSdk1, alphaSdk2)
    }

    @Test
    @DisplayName("Should return valid Beta channel")
    fun shouldReturnBetaSdk() {
        // given
        val betaSdk: AgonesSdk.Beta = sdk.beta()

        // then
        assertNotNull(betaSdk)
    }

    @Test
    @DisplayName("Should return same Beta channel every time")
    fun shouldReturnSameBetaSdk() {
        // given
        val betaSdk1: AgonesSdk.Beta = sdk.beta()
        val betaSdk2: AgonesSdk.Beta = sdk.beta()

        // then
        assertEquals(betaSdk1, betaSdk2)
    }

    @Nested
    inner class AlphaTest {

        @BeforeEach
        fun beforeEach() = runTest {
            // obtain an alpha sdk
            val alphaSdk: Alpha = sdk.alpha()

            // when
            alphaSdk.playerCapacity(64)
        }

        @Test
        @DisplayName("Should connect player")
        fun shouldConnectPlayer() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when, then
            assertTrue(alphaSdk.playerConnect(playerId))
            assertTrue(alphaSdk.isPlayerConnected(playerId))
        }

        @Test
        @DisplayName("Should throw ISE on player connect above capacity limit")
        fun shouldThrowOnConnectPlayerOnCapacityLimit() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when
            alphaSdk.playerCapacity(0)

            // then
            assertFailsWith<IllegalStateException> {
                alphaSdk.playerConnect(playerId)
            }
        }

        @Test
        @DisplayName("Should rethrow normal exception on error during player connect")
        fun shouldRethrowOnConnectPlayerException() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when
            sdk.close()

            // then
            val exception = assertFailsWith<StatusException> {
                alphaSdk.playerConnect(playerId)
            }
            val status = exception.status
            assertEquals(Status.Code.UNAVAILABLE, status.code)
            assertEquals(status.description, "Channel shutdown invoked")
        }

        @Test
        @DisplayName("Should not connect already connected player")
        fun shouldNotConnectAlreadyConnectedPlayer() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when, then
            assertTrue(alphaSdk.playerConnect(playerId))
            assertFalse(alphaSdk.playerConnect(playerId))
        }

        @Test
        @DisplayName("Should disconnect player")
        fun shouldDisconnectPlayer() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when
            alphaSdk.playerConnect(playerId)

            // then
            assertTrue(alphaSdk.playerDisconnect(playerId))
        }

        @Test
        @DisplayName("Should not disconnect unconnected player")
        fun shouldNotDisconnectPlayerIfNotConnected() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when, then
            assertFalse(alphaSdk.playerDisconnect(playerId))
        }

        @Test
        @DisplayName("Should get empty connected players")
        fun shouldGetConnectedPlayersEmpty() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when, then
            assertEquals(0, alphaSdk.connectedPlayers().size)
        }

        @Test
        @DisplayName("Should get populated connected players")
        fun shouldGetConnectedPlayersPopulated() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId1 = "a"
            val playerId2 = "b"
            val playerId3 = "c"

            // when
            alphaSdk.playerConnect(playerId1)
            alphaSdk.playerConnect(playerId2)
            alphaSdk.playerConnect(playerId3)

            // then
            assertEquals(3, alphaSdk.connectedPlayers().size)
            val connectedPlayers: List<String> = alphaSdk.connectedPlayers()
            assertContains(connectedPlayers, playerId1)
            assertContains(connectedPlayers, playerId2)
            assertContains(connectedPlayers, playerId3)
        }

        @Test
        @DisplayName("Should get connected player")
        fun shouldGetConnectedPlayer() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "apple"

            // when
            alphaSdk.playerConnect(playerId)

            // then
            assertTrue(alphaSdk.isPlayerConnected(playerId))
        }

        @Test
        @DisplayName("Should not get disconnected player")
        fun shouldNotGetDisconnectedPlayer() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when, then
            assertFalse(alphaSdk.isPlayerConnected("a"))
        }

        @Test
        @DisplayName("Should get player count empty")
        fun shouldGetPlayerCountEmpty() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when
            val playerCount: Long = alphaSdk.playerCount()

            // then
            assertEquals(0L, playerCount)
        }

        @Test
        @DisplayName("Should get player count with players")
        fun shouldGetPlayerCount() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            alphaSdk.playerConnect("a")
            alphaSdk.playerConnect("b")
            alphaSdk.playerConnect("c")

            // when
            val playerCount: Long = alphaSdk.playerCount()

            // then
            assertEquals(3L, playerCount)
        }

        @ParameterizedTest(name = "#playerCapacity({0})")
        @ValueSource(longs = [0L, 10L, 1337L, 100000L])
        @DisplayName("Should set player capacity")
        fun shouldSetPlayerCapacity(capacity: Long) = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when
            alphaSdk.playerCapacity(capacity)

            // then
            assertEquals(capacity, alphaSdk.playerCapacity())
        }

        @ParameterizedTest(name = "#playerCapacity({0})")
        @ValueSource(longs = [-1L, -100L, -1337L, -100000L])
        @DisplayName("Should throw IAE on negative player capacity")
        fun shouldThrowOnNegativeCapacity(capacity: Long) = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when, then
            assertFailsWith<java.lang.IllegalArgumentException> {
                alphaSdk.playerCapacity(capacity)
            }
        }

        @Test
        @DisplayName("Should get player capacity")
        fun shouldGetPlayerCapacity() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()

            // when
            val playerCapacity: Long = alphaSdk.playerCapacity()

            // then
            assertEquals(64L, playerCapacity)
        }
    }

    @Nested
    inner class BetaTest {
        // no tests so for beta far
    }

    private fun containsLogLine(logMessagePart: String, waitMs: Long = 0, count: Int = 1): Boolean {
        return getLogLines(logMessagePart, waitMs, count).size == count
    }

    private fun getLogLines(logMessagePart: String, waitMs: Long = 0, count: Int = 1): List<String> {
        // wait for the log line to appear if there is any wait time specified
        if (waitMs > 0) {
            logConsumer.waitUntil(
                { it.utf8String.contains(logMessagePart) },
                waitMs,
                TimeUnit.MILLISECONDS,
                count
            )
        }

        // collect all
        val result = mutableListOf<String>()
        for (line in sdkContainer.logs.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (line.contains(logMessagePart)) {
                result.add(line)
            }
        }
        return result
    }

    companion object {
        private const val GRPC_PORT = 9357
        private const val HTTP_PORT = 9358
        private const val WAIT_TIMEOUT_MILLIS = 7500L
    }
}
