package net.justchunks.agones.client

import agones.dev.sdk.SDKGrpcKt
import agones.dev.sdk.Sdk.Empty
import agones.dev.sdk.Sdk.GameServer
import agones.dev.sdk.alpha.Alpha
import agones.dev.sdk.alpha.Alpha.PlayerID
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.justchunks.agones.client.AgonesSdk.Companion.AGONES_SDK_HOST
import net.justchunks.agones.client.AgonesSdk.Companion.META_KEY_PATTERN
import net.justchunks.agones.client.GrpcAgonesSdk.Companion.AGONES_SDK_PORT_ENV_KEY
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import agones.dev.sdk.Sdk.Duration as AgonesDuration
import agones.dev.sdk.Sdk.KeyValue as AgonesKeyValue
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
class GrpcAgonesSdk internal constructor(
    /** The host of the external interface of the sidecar SDK, that will be used to establish the connection. */
    val host: String = AGONES_SDK_HOST,
    /** The port of the external interface of the sidecar SDK, that will be used to establish the connection. */
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
            AgonesDuration.newBuilder()
                .setSeconds(seconds)
                .build(),
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
            AgonesKeyValue.newBuilder()
                .setKey(key)
                .setValue(value)
                .build(),
        )
    }

    override suspend fun annotation(key: String, value: String) {
        // check that the label key is allowed within kubernetes
        require(META_KEY_PATTERN.matcher(key).matches()) {
            "The supplied key '$key' does not match the pattern for annotation keys."
        }

        // call the endpoint with the mapping and ignore the response
        stub.setAnnotation(
            AgonesKeyValue.newBuilder()
                .setKey(key)
                .setValue(value)
                .build(),
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

    override fun alpha(): AgonesSdk.Alpha = alphaSdk

    override fun beta(): AgonesSdk.Beta = betaSdk

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
            // capacity overflows throw exceptions, so we need to handle them
            try {
                return stub.playerConnect(
                    PlayerID.newBuilder()
                        .setPlayerID(playerId)
                        .build(),
                ).bool
            } catch (ex: StatusException) {
                // if the player limit is exhausted, convert the exception
                if (ex.status.code == Code.UNKNOWN &&
                    ex.status.description == "Players are already at capacity"
                ) {
                    throw IllegalStateException("Player capacity is exhausted!", ex)
                }

                // rethrow the original exception in every other case
                throw ex
            }
        }

        override suspend fun playerDisconnect(playerId: String): Boolean {
            // call the endpoint with the player ID and return the response
            return stub.playerDisconnect(
                PlayerID.newBuilder()
                    .setPlayerID(playerId)
                    .build(),
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
                PlayerID.newBuilder()
                    .setPlayerID(playerId)
                    .build(),
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
                Alpha.Count.newBuilder()
                    .setCount(capacity)
                    .build(),
            )
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
    }

    companion object {
        /** The logger that will be utilized to perform any logging for the methods of this class. */
        private val logger = LoggerFactory.getLogger(GrpcAgonesSdk::class.java)

        /** The default port, that will be used to communicate with the gRPC server of the sidecar SDK. */
        private const val DEFAULT_AGONES_SDK_PORT: Int = 9357

        /** The key of the environment variable, that can be used to retrieve the assigned gRPC port of the SDK. */
        private const val AGONES_SDK_PORT_ENV_KEY = "AGONES_SDK_GRPC_PORT"

        /** The [duration][Duration], that will be waited at maximum for the successful shutdown of the channel. */
        private val SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(5)

        /**
         * The gRPC port of the Agones SDK sidecar from within a container of the same pod for the communication.
         *
         * In order to get the effective port, it will be first tried to extract this port through an environment
         * variable with the key [AGONES_SDK_PORT_ENV_KEY]. If this variable is not set, [DEFAULT_AGONES_SDK_PORT] will
         * be used instead, which is the default gRPC port for any installation of Agones.
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
