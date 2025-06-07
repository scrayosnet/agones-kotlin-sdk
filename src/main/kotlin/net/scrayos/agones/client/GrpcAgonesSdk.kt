package net.scrayos.agones.client

import agones.dev.sdk.SDKGrpcKt
import agones.dev.sdk.Sdk.Empty
import agones.dev.sdk.Sdk.GameServer
import agones.dev.sdk.alpha.Alpha
import agones.dev.sdk.alpha.count
import agones.dev.sdk.alpha.playerID
import agones.dev.sdk.beta.addListValueRequest
import agones.dev.sdk.beta.counterUpdateRequest
import agones.dev.sdk.beta.getCounterRequest
import agones.dev.sdk.beta.getListRequest
import agones.dev.sdk.beta.list
import agones.dev.sdk.beta.removeListValueRequest
import agones.dev.sdk.beta.updateCounterRequest
import agones.dev.sdk.beta.updateListRequest
import agones.dev.sdk.duration
import agones.dev.sdk.keyValue
import com.google.protobuf.Int64Value
import com.google.protobuf.fieldMask
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.scrayos.agones.client.AgonesSdk.Companion.AGONES_SDK_HOST
import net.scrayos.agones.client.AgonesSdk.Companion.META_KEY_PATTERN
import net.scrayos.agones.client.GrpcAgonesSdk.Companion.AGONES_SDK_PORT_ENV_KEY
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import agones.dev.sdk.alpha.SDKGrpcKt as AlphaSDKGrpcKt
import agones.dev.sdk.beta.SDKGrpcKt as BetaSDKGrpcKt

/**
 * A [GrpcAgonesSdk] represents the gRPC implementation of the [Agones SDK][AgonesSdk]. The implementation is based on
 * the official Protobufs released with Agones. Each platform only needs one implementation of the Agones SDK, but does
 * not have to worry about selecting the best implementation. Instead, the factory method specifies the implementation.
 * All implementations fully comply with the Agones specification.
 *
 * When creating an instance of this implementation, the corresponding [network channel][Channel] is dynamically
 * assembled for this purpose. The port is obtained through the environment variable [AGONES_SDK_PORT_ENV_KEY], if any
 * value has been set for this key. The corresponding stubs for coroutine-based communication with the interface are
 * instantiated for the [channel][Channel] to the attached sidecar. No action is taken by creating this instance, and
 * communication with the external interface is not initiated.
 *
 * @param host The host, under which the gRPC server of the sidecar SDK can be reached and that will therefore be used
 * to establish the connection.
 * @param port The port, under which the gRPC server of the sidecar SDK can be reached and that will therefore be used
 * to establish the connection.
 */
class GrpcAgonesSdk(
    /** The host of the external interface of the sidecar SDK, that will be used to establish the connection. */
    val host: String = AGONES_SDK_HOST,
    /** The port of the sidecar SDK's external interface that will be used to establish the connection. */
    val port: Int = AGONES_SDK_PORT,
) : AgonesSdk {

    /** The [channel][ManagedChannel], that will be used for the network communication with the external interface. */
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    /** The coroutine-based stub for the communication the external interface of the [Agones SDK][AgonesSdk]. */
    private val stub: SDKGrpcKt.SDKCoroutineStub = SDKGrpcKt.SDKCoroutineStub(channel)

    /** The instance of the encapsulated [Alpha channel][AgonesSdk.Beta] of this [Agones SDK][AgonesSdk]. */
    private val alphaSdk: AgonesSdk.Alpha = GrpcAlpha(channel)

    /** The instance of the encapsulated [Beta channel][AgonesSdk.Beta] of this [Agones SDK][AgonesSdk]. */
    private val betaSdk: AgonesSdk.Beta = GrpcBeta(channel)

    override suspend fun ready() {
        // call the endpoint with an empty request and ignore the response
        stub.ready(Empty.getDefaultInstance())
    }

    override suspend fun health(stream: Flow<Unit>) {
        // call the endpoint with empty requests and ignore the response
        stub.health(stream.map { Empty.getDefaultInstance() })
    }

    override suspend fun reserve(seconds: Long) {
        // check that the seconds are within the allowed bounds
        require(seconds >= 0) {
            "The supplied seconds '$seconds' are not positive!"
        }

        // call the endpoint with the duration and ignore the response
        stub.reserve(
            duration {
                this.seconds = seconds
            },
        )
    }

    override suspend fun allocate() {
        // call the endpoint with an empty request and ignore the response
        stub.allocate(Empty.getDefaultInstance())
    }

    override suspend fun shutdown() {
        // call the endpoint with an empty request and ignore the response
        stub.shutdown(Empty.getDefaultInstance())
    }

    override suspend fun label(key: String, value: String) {
        // check that the label key is allowed within kubernetes
        require(META_KEY_PATTERN.matcher(key).matches()) {
            "The supplied key '$key' does not match the pattern for label keys."
        }

        // call the endpoint with the mapping and ignore the response
        stub.setLabel(
            keyValue {
                this.key = key
                this.value = value
            },
        )
    }

    override suspend fun annotation(key: String, value: String) {
        // check that the label key is allowed within kubernetes
        require(META_KEY_PATTERN.matcher(key).matches()) {
            "The supplied key '$key' does not match the pattern for annotation keys."
        }

        // call the endpoint with the mapping and ignore the response
        stub.setAnnotation(
            keyValue {
                this.key = key
                this.value = value
            },
        )
    }

    override suspend fun gameServer(): GameServer {
        // call the endpoint with an empty request and return the response
        return stub.getGameServer(Empty.getDefaultInstance())
    }

    override fun watchGameServer(): Flow<GameServer> {
        // call the endpoint with an empty request and use the callback to handle responses
        return stub.watchGameServer(Empty.getDefaultInstance())
    }

    override fun beta(): AgonesSdk.Beta = betaSdk

    override fun alpha(): AgonesSdk.Alpha = alphaSdk

    override fun close() {
        try {
            // shutdown and wait for it to complete
            val finishedShutdown = channel
                .shutdown()
                .awaitTermination(SHUTDOWN_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)

            // force shutdown if it did not terminate
            if (!finishedShutdown) {
                channel.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            // log so we know the origin/reason for this interruption
            logger.debug("Thread was interrupted while waiting for the shutdown of a GrpcAgonesSdk.", ex)

            // set interrupted status of this thread
            Thread.currentThread().interrupt()
        }
    }

    /**
     * The [GrpcBeta] class provides gRPC-based implementations for all interfaces of the [Beta-Channel][AgonesSdk.Beta]
     * of the [Agones SDK][AgonesSdk]. The implementation encapsulates the communication the external interface of the
     * SDK and manages the corresponding stubs. This sub SDK operates on the same channel as the primary SDK and
     * therefore shares its connections and related resources as well as its state.
     *
     * The creation of this object only creates the necessary stub and does not initiate any communication to the
     * external SDK interface. It merely equips the SDK with means to communicate with the SDK, if requested by invoking
     * any of the provided Beta interfaces.
     *
     * @param channel The [channel] of the primary [Agones SDK][AgonesSdk] that is used for the communication with the
     * external network interface and that holds the related connections and resources.
     */
    class GrpcBeta(channel: Channel) : AgonesSdk.Beta {

        /** The coroutine-based stub for the communication the external interface of the [Agones SDK][AgonesSdk]. */
        private val stub: BetaSDKGrpcKt.SDKCoroutineStub = BetaSDKGrpcKt.SDKCoroutineStub(channel)

        override suspend fun counterCount(name: String): Long {
            // call the endpoint with the counter name and return the response
            return stub.getCounter(
                getCounterRequest {
                    this.name = name
                },
            ).count
        }

        override suspend fun counterCount(name: String, value: Long) {
            // call the endpoint with the counter name and the new count
            stub.updateCounter(
                updateCounterRequest {
                    counterUpdateRequest = counterUpdateRequest {
                        this.name = name
                        this.count = Int64Value.of(value)
                    }
                },
            )
        }

        override suspend fun incrementCounter(name: String) {
            // call the endpoint with the counter name
            stub.updateCounter(
                updateCounterRequest {
                    counterUpdateRequest = counterUpdateRequest {
                        this.name = name
                        this.countDiff = 1L
                    }
                },
            )
        }

        override suspend fun decrementCounter(name: String) {
            // call the endpoint with the counter name
            stub.updateCounter(
                updateCounterRequest {
                    counterUpdateRequest = counterUpdateRequest {
                        this.name = name
                        this.countDiff = -1L
                    }
                },
            )
        }

        override suspend fun counterCapacity(name: String): Long {
            // call the endpoint with the counter name and return the response
            return stub.getCounter(
                getCounterRequest {
                    this.name = name
                },
            ).capacity
        }

        override suspend fun counterCapacity(name: String, capacity: Long) {
            // check that the capacity is valid
            require(capacity >= 0) {
                "The supplied capacity '$capacity' is not positive!"
            }

            // call the endpoint with the counter name and the new capacity
            stub.updateCounter(
                updateCounterRequest {
                    counterUpdateRequest = counterUpdateRequest {
                        this.name = name
                        this.capacity = Int64Value.of(capacity)
                    }
                },
            )
        }

        override suspend fun listSize(name: String): Int {
            // call the endpoint with the list name and return the response
            return stub.getList(
                getListRequest {
                    this.name = name
                },
            ).valuesCount
        }

        override suspend fun listValues(name: String): List<String> {
            // call the endpoint with the list name and return the response
            return stub.getList(
                getListRequest {
                    this.name = name
                },
            ).valuesList
        }

        override suspend fun listContains(name: String, value: String): Boolean {
            // call the endpoint with the list name and return the response
            return stub.getList(
                getListRequest {
                    this.name = name
                },
            ).valuesList.contains(value)
        }

        override suspend fun addListValue(name: String, value: String) {
            // call the endpoint with the list name and the entry to be added
            stub.addListValue(
                addListValueRequest {
                    this.name = name
                    this.value = value
                },
            )
        }

        override suspend fun removeListValue(name: String, value: String) {
            // call the endpoint with the list name and the entry to be removed
            stub.removeListValue(
                removeListValueRequest {
                    this.name = name
                    this.value = value
                },
            )
        }

        override suspend fun listCapacity(name: String): Long {
            // call the endpoint with the list name and return the response
            return stub.getList(
                getListRequest {
                    this.name = name
                },
            ).capacity
        }

        override suspend fun listCapacity(name: String, capacity: Long) {
            // check that the capacity is valid
            require(capacity >= 0) {
                "The supplied capacity '$capacity' is not positive!"
            }

            // call the endpoint with the list name and new capacity
            stub.updateList(
                updateListRequest {
                    list = list {
                        this.name = name
                        this.capacity = capacity
                    }
                    updateMask = fieldMask {
                        paths.add("capacity")
                    }
                },
            )
        }
    }

    /**
     * The [GrpcAlpha] class provides gRPC-based implementations for all interfaces of the [Alpha-Channel][AgonesSdk.Alpha]
     * of the [Agones SDK][AgonesSdk]. The implementation encapsulates the communication the external interface of the
     * SDK and manages the corresponding stubs. This sub SDK operates on the same channel as the primary SDK and
     * therefore shares its connections and related resources as well as its state.
     *
     * The creation of this object only creates the necessary stub and does not initiate any communication to the
     * external SDK interface. It merely equips the SDK with means to communicate with the SDK, if requested by invoking
     * any of the provided Alpha interfaces.
     *
     * @param channel The [channel] of the primary [Agones SDK][AgonesSdk] that is used for the communication with the
     * external network interface and that holds the related connections and resources.
     */
    class GrpcAlpha(channel: Channel) : AgonesSdk.Alpha {

        /** The coroutine-based stub for the communication the external interface of the [Agones SDK][AgonesSdk]. */
        private val stub: AlphaSDKGrpcKt.SDKCoroutineStub = AlphaSDKGrpcKt.SDKCoroutineStub(channel)

        override suspend fun playerConnect(playerId: String): Boolean {
            // call the endpoint with the player ID and return the response
            return stub.playerConnect(
                playerID {
                    this.playerID = playerId
                },
            ).bool
        }

        override suspend fun playerDisconnect(playerId: String): Boolean {
            // call the endpoint with the player ID and return the response
            return stub.playerDisconnect(
                playerID {
                    this.playerID = playerId
                },
            ).bool
        }

        override suspend fun connectedPlayers(): List<String> {
            // call the endpoint with an empty request and return the response
            return stub.getConnectedPlayers(Alpha.Empty.getDefaultInstance())
                .listList
        }

        override suspend fun isPlayerConnected(playerId: String): Boolean {
            // call the endpoint with the player ID and return the response
            return stub.isPlayerConnected(
                playerID {
                    this.playerID = playerId
                },
            ).bool
        }

        override suspend fun playerCount(): Long {
            // call the endpoint with an empty request and return the response
            return stub.getPlayerCount(
                Alpha.Empty.getDefaultInstance(),
            ).count
        }

        override suspend fun playerCapacity(): Long {
            // call the endpoint with an empty request and return the response
            return stub.getPlayerCapacity(
                Alpha.Empty.getDefaultInstance(),
            ).count
        }

        override suspend fun playerCapacity(capacity: Long) {
            // check that the capacity is within allowed bounds
            require(capacity >= 0) {
                "The supplied capacity '$capacity' is not positive!"
            }

            // call the endpoint with the count and ignore the response
            stub.setPlayerCapacity(
                count {
                    count = capacity
                },
            )
        }
    }

    companion object {
        /** The logger that will be used to perform any logging for the methods of this class. */
        private val logger = LoggerFactory.getLogger(GrpcAgonesSdk::class.java)

        /** The default port, that will be used to communicate with the gRPC server of the sidecar SDK. */
        private const val DEFAULT_AGONES_SDK_PORT: Int = 9357

        /** The key of the environment variable that can be used to retrieve the assigned gRPC port of the SDK. */
        private const val AGONES_SDK_PORT_ENV_KEY = "AGONES_SDK_GRPC_PORT"

        /** The [duration][Duration], that will be waited at maximum for the successful shutdown of the channel. */
        private val SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(5)

        /**
         * The gRPC port of the Agones SDK sidecar from within a container of the same pod for the communication.
         *
         * To get the effective port, it will be first tried to extract this port through an environment variable with
         * the key [AGONES_SDK_PORT_ENV_KEY]. If this variable is not set, [DEFAULT_AGONES_SDK_PORT] will be used
         * instead, which is the default gRPC port for any installation of Agones.
         */
        val AGONES_SDK_PORT: Int
            get() {
                // read the environment variable for the dynamic agones port
                val textPort = System.getenv(AGONES_SDK_PORT_ENV_KEY)

                // check that there was any value and that it is valid
                return if (textPort == null) {
                    // fall back to the default port as it could not be found
                    DEFAULT_AGONES_SDK_PORT
                } else {
                    // parse the number from the textual environment variable value
                    try {
                        textPort.toInt()
                    } catch (ex: NumberFormatException) {
                        throw IllegalArgumentException(
                            "The supplied environment variable for the port did not contain a valid number.",
                        )
                    }
                }
            }
    }
}
