package net.scrayos.agones.client

import agones.dev.sdk.Sdk.GameServer
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.scrayos.agones.client.AgonesSdk.Alpha
import net.scrayos.agones.client.AgonesSdk.Beta
import net.scrayos.agones.client.AgonesSdk.Companion.METADATA_KEY_PREFIX
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
internal class GrpcAgonesSdkTest {

    @Container
    private val sdkContainer: GenericContainer<*> = GenericContainer(
        DockerImageName.parse("us-docker.pkg.dev/agones-images/release/agones-sdk:1.49.0"),
    )
        .withCommand(
            "--local",
            "--address=0.0.0.0",
            "--sdk-name=AgonesClientSDK",
            "--grpc-port=$GRPC_PORT",
            "--http-port=$HTTP_PORT",
            "--feature-gates=PlayerTracking=true&SidecarContainers=true",
        )
        .withExposedPorts(GRPC_PORT, HTTP_PORT)
        .waitingFor(
            Wait
                .forHttp("/")
                .forPort(HTTP_PORT)
                .forStatusCode(404),
        )
    private lateinit var sdk: GrpcAgonesSdk
    private lateinit var logConsumer: WaitingConsumer

    @BeforeEach
    fun beforeEach() {
        sdk = GrpcAgonesSdk(
            sdkContainer.host,
            sdkContainer.getMappedPort(GRPC_PORT),
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
        val logObj = Json.parseToJsonElement(logLine).jsonObject
        logObj shouldHaveKey "duration"
        val duration = logObj["duration"]!!.jsonObject
        assertEquals("Reserved", sdk.gameServer().status.state)
        if (seconds == 0) {
            duration["seconds"].shouldBeNull()
        } else {
            assertEquals(seconds.toLong(), duration["seconds"]!!.jsonPrimitive.long)
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
        val valuesObject = Json.parseToJsonElement(logLine).jsonObject["values"]!!.jsonObject
        val gameServer: GameServer = sdk.gameServer()
        assertEquals(key, valuesObject["key"]!!.jsonPrimitive.content)
        assertTrue(gameServer.objectMeta.labelsMap.containsKey(METADATA_KEY_PREFIX + key))
        assertEquals(value, valuesObject["value"]!!.jsonPrimitive.content)
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
        val valuesObject = Json.parseToJsonElement(logLine).jsonObject["values"]!!.jsonObject
        val gameServer: GameServer = sdk.gameServer()
        assertEquals(key, valuesObject["key"]!!.jsonPrimitive.content)
        assertTrue(gameServer.objectMeta.annotationsMap.containsKey(METADATA_KEY_PREFIX + key))
        assertEquals(value, valuesObject["value"]!!.jsonPrimitive.content)
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
    @DisplayName("Close should force shutdown when graceful shutdown times out")
    fun closeShouldForceOnTimeout() {
        // setup
        val mockChannel = mockk<ManagedChannel>()
        every { mockChannel.shutdown() } returns mockChannel
        every { mockChannel.awaitTermination(any(), any()) } returns false
        every { mockChannel.shutdownNow() } returns mockChannel

        // given
        val sdk = GrpcAgonesSdk()
        val channelField = sdk::class.java.getDeclaredField("channel")
        channelField.isAccessible = true
        channelField.set(sdk, mockChannel)

        // when
        sdk.close()

        // then
        verify { mockChannel.shutdownNow() }
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
    @DisplayName("Should return valid Beta channel")
    fun shouldReturnBetaSdk() {
        // given
        val betaSdk: Beta = sdk.beta()

        // then
        assertNotNull(betaSdk)
    }

    @Test
    @DisplayName("Should return same Beta channel every time")
    fun shouldReturnSameBetaSdk() {
        // given
        val betaSdk1: Beta = sdk.beta()
        val betaSdk2: Beta = sdk.beta()

        // then
        assertEquals(betaSdk1, betaSdk2)
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

    @Nested
    inner class BetaTest {

        @Test
        @DisplayName("Should get counter count")
        fun shouldGetCounterCount() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            assertEquals(1, betaSdk.counterCount("rooms"))
        }

        @Test
        @DisplayName("Should not get unknown counter count")
        fun shouldNotGetUnknownCounterCount() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCount("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @ParameterizedTest(name = "#counterCount(\"rooms\", {0})")
        @ValueSource(longs = [3, 5, 7, 9])
        @DisplayName("Should set counter count")
        fun shouldSetCounterCount(count: Long) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            betaSdk.counterCount("rooms", count)

            // then
            assertEquals(count, betaSdk.counterCount("rooms"))
        }

        @Test
        @DisplayName("Should throw on set unknown counter count")
        fun shouldThrowOnSetUnknownCounterCount() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCount("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @Test
        @DisplayName("Should increment counter")
        fun shouldIncrementCounter() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            assertEquals(1, betaSdk.counterCount("rooms"))
            betaSdk.incrementCounter("rooms")
            assertEquals(2, betaSdk.counterCount("rooms"))
            betaSdk.incrementCounter("rooms")
            assertEquals(3, betaSdk.counterCount("rooms"))
            betaSdk.incrementCounter("rooms")
            assertEquals(4, betaSdk.counterCount("rooms"))
        }

        @Test
        @DisplayName("Should throw on increment unknown counter")
        fun shouldThrowOnIncrementUnknownCounter() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCount("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @Test
        @DisplayName("Should throw on increment counter beyond capacity")
        fun shouldThrowOnIncrementCounterBeyondCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()
            betaSdk.counterCapacity("rooms", 2)
            betaSdk.decrementCounter("rooms")

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.incrementCounter("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @Test
        @DisplayName("Should increment counter")
        fun shouldDecrementCounter() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()
            betaSdk.counterCount("rooms", 10)

            // when, then
            assertEquals(10, betaSdk.counterCount("rooms"))
            betaSdk.decrementCounter("rooms")
            assertEquals(9, betaSdk.counterCount("rooms"))
            betaSdk.decrementCounter("rooms")
            assertEquals(8, betaSdk.counterCount("rooms"))
            betaSdk.decrementCounter("rooms")
            assertEquals(7, betaSdk.counterCount("rooms"))
        }

        @Test
        @DisplayName("Should throw on increment unknown counter")
        fun shouldThrowOnDecrementUnknownCounter() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCount("invalid", 7)
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @Test
        @DisplayName("Should throw on decrement counter below zero")
        fun shouldThrowOnDecrementCounterBelowZero() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()
            betaSdk.decrementCounter("rooms")

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.decrementCounter("rooms")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals(
                "out of range. Count must be within range [0,Capacity]. Found Count: -1, Capacity: 10",
                ex.status.description,
            )
        }

        @Test
        @DisplayName("Should get counter capacity")
        fun shouldGetCounterCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            assertEquals(10, betaSdk.counterCapacity("rooms"))
        }

        @Test
        @DisplayName("Should not get unknown counter capacity")
        fun shouldThrowOnGetUnknownCounterCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCapacity("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @ParameterizedTest(name = "#counterCapacity(\"rooms\", {0})")
        @ValueSource(longs = [10, 15, 100, 1000, 10000])
        @DisplayName("Should set counter capacity")
        fun shouldSetCounterCapacity(capacity: Long) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            betaSdk.counterCapacity("rooms", capacity)

            // then
            assertEquals(capacity, betaSdk.counterCapacity("rooms"))
        }

        @ParameterizedTest(name = "#counterCapacity(\"rooms\", {0})")
        @ValueSource(longs = [-1L, -100L, -1337L, -100000L])
        @DisplayName("Should throw on set negative counter capacity")
        fun shouldThrowOnSetNegativeCounterCapacity(capacity: Long) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<IllegalArgumentException> {
                betaSdk.counterCapacity("rooms", capacity)
            }
            assertEquals("The supplied capacity '$capacity' is not positive!", ex.message)
        }

        @Test
        @DisplayName("Should throw on set unknown counter capacity")
        fun shouldThrowOnSetUnknownCounterCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.counterCapacity("invalid", 10)
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid Counter not found", ex.status.description)
        }

        @Test
        @DisplayName("Should get list size")
        fun shouldGetListSize() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            assertEquals(3, betaSdk.listSize("players"))
        }

        @Test
        @DisplayName("Should not get unknown list size")
        fun shouldNotGetUnknownListSize() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.listSize("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @Test
        @DisplayName("Should get list values")
        fun shouldGetListValues() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            val values = betaSdk.listValues("players")
            // then
            assertNotNull(values)
            assertContains(values, "test0")
            assertContains(values, "test1")
            assertContains(values, "test2")
        }

        @Test
        @DisplayName("Should not get unknown list values")
        fun shouldNotGetUnknownListValues() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.listValues("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @Test
        @DisplayName("Should contain list value")
        fun shouldContainListValue() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            val contains = betaSdk.listContains("players", "test0")

            // then
            assertNotNull(contains)
            assertTrue(contains)
        }

        @Test
        @DisplayName("Should not contain missing list value")
        fun shouldNotContainMissingListValue() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            val contains = betaSdk.listContains("players", "test4")

            // then
            assertNotNull(contains)
            assertFalse(contains)
        }

        @Test
        @DisplayName("Should not contain list value for unknown list")
        fun shouldNotContainListValueForUnknownList() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.listContains("invalid", "test0")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @ParameterizedTest(name = "#addListValue(\"players\", {0})")
        @ValueSource(strings = ["test3", "agones", "justchunks"])
        @DisplayName("Should add list value")
        fun shouldAddListValue(value: String) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            betaSdk.addListValue("players", value)

            // then
            assertEquals(4, betaSdk.listSize("players"))
            val values = betaSdk.listValues("players")
            assertNotNull(values)
            assertContains(values, value)
        }

        @Test
        @DisplayName("Should not add existing list value")
        fun shouldThrowOnAddExistingListValue() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.addListValue("players", "test0")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("already exists. Value: test0 already in List: players", ex.status.description)
        }

        @Test
        @DisplayName("Should throw on add list value for unknown list")
        fun shouldThrowOnAddListValueForUnknownList() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.addListValue("invalid", "test")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @Test
        @DisplayName("Should throw on add list value for list at capacity")
        fun shouldThrowOnAddListValueForListAtCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()
            betaSdk.listCapacity("players", 3)

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.addListValue("players", "test3")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals(
                "out of range. No available capacity. Current Capacity: 3, List Size: 3",
                ex.status.description,
            )
        }

        @Test
        @DisplayName("Should remove list value")
        fun shouldRemoveListValue() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            betaSdk.removeListValue("players", "test0")

            // then
            assertEquals(2, betaSdk.listSize("players"))
            val values = betaSdk.listValues("players")
            assertNotNull(values)
            assertFalse(values.contains("test0"))
        }

        @Test
        @DisplayName("Should not remove missing list value")
        fun shouldThrowOnRemoveMissingListValue() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.removeListValue("players", "test3")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. Value: test3 not found in List: players", ex.status.description)
        }

        @Test
        @DisplayName("Should throw on remove list value for unknown list")
        fun shouldThrowOnRemoveListValueForUnknownList() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.removeListValue("invalid", "test0")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @Test
        @DisplayName("Should get list capacity")
        fun shouldGetListCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            assertEquals(100, betaSdk.listCapacity("players"))
        }

        @Test
        @DisplayName("Should not get unknown list capacity")
        fun shouldNotGetUnknownListCapacity() = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<StatusException> {
                betaSdk.listCapacity("invalid")
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("not found. invalid List not found", ex.status.description)
        }

        @ParameterizedTest(name = "#listCapacity(\"players\", {0})")
        @ValueSource(longs = [10, 15, 100, 777, 1000])
        @DisplayName("Should set list capacity")
        fun shouldSetListCapacity(capacity: Long) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when
            betaSdk.listCapacity("players", capacity)

            // then
            assertEquals(capacity, betaSdk.listCapacity("players"))
        }

        @ParameterizedTest(name = "#listCapacity(\"players\", {0})")
        @ValueSource(longs = [-1L, -100L, -1337L, -100000L])
        @DisplayName("Should throw on set negative list capacity")
        fun shouldThrowOnSetNegativeListCapacity(capacity: Long) = runTest {
            // given
            val betaSdk: Beta = sdk.beta()

            // when, then
            val ex = assertFailsWith<IllegalArgumentException> {
                betaSdk.listCapacity("players", capacity)
            }
            assertEquals("The supplied capacity '$capacity' is not positive!", ex.message)
        }
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
        @DisplayName("Should throw on player connect above capacity limit")
        fun shouldThrowOnConnectPlayerOnCapacityLimit() = runTest {
            // given
            val alphaSdk: Alpha = sdk.alpha()
            val playerId = "a"

            // when
            alphaSdk.playerCapacity(0)

            // then
            val ex = assertFailsWith<StatusException> {
                alphaSdk.playerConnect(playerId)
            }
            assertEquals(Status.Code.UNKNOWN, ex.status.code)
            assertEquals("Players are already at capacity", ex.status.description)
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

    private fun containsLogLine(logMessagePart: String, waitMs: Long = 0, count: Int = 1): Boolean =
        getLogLines(logMessagePart, waitMs, count).size == count

    private fun getLogLines(logMessagePart: String, waitMs: Long = 0, count: Int = 1): List<String> {
        // wait for the log line to appear if there is any wait time specified
        if (waitMs > 0) {
            logConsumer.waitUntil(
                { it.utf8String.contains(logMessagePart) },
                waitMs,
                TimeUnit.MILLISECONDS,
                count,
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
