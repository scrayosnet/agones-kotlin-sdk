package net.justchunks.agones.client

import agones.dev.sdk.Sdk.GameServer
import java.time.Duration
import java.util.regex.Pattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The [Agones SDK][AgonesSdk] represents the technical interface through which this server can communicate its current
 * state to Agones. This SDK cannot be used to request new resources in Agones. Instead, its methods are used to
 * manipulate and query the state of the [GameServer] resource associated with this instance. The SDK focuses solely on
 * the individual instance and ignores the rest of the cluster. For requests to Agones or the cluster, the Kubernetes
 * API should be used instead.
 *
 * Agones divides its interfaces into three different channels to signal their usage and maturity and to allow for
 * active changes or complete removal of those interfaces in the case of issues or if they discover that a solution does
 * not really fit into the Agones system. The available channels are:
 *
 * * **Stable**: The endpoints were tested extensively and are explicitly recommended or necessary for the general use
 * of the SDK. It is guaranteed, that those endpoints will be available in their current form for many versions to come.
 * They can be accessed directly through this interface and are not specially isolated or encapsulated.
 * * **Beta**: The endpoints were already tested to some extent or are expected to have very few bugs and the general
 * use is not discouraged. They are enabled by default, and it is guaranteed, that there will be no major changes to
 * those features anymore. The endpoints can be accessed through the [Beta-Channel][beta] in an encapsulated interface.
 * * **Alpha**: The endpoints have been tested only superficially or rarely and general use is not recommended. They
 * are disabled by default and there may be major changes or the endpoints may be removed completely at any time. The
 * endpoints can be accessed through the [Alpha-Channel][alpha] in an encapsulated interface.
 *
 * The interfaces that modify the state do not guarantee that the resource in Kubernetes will actually transition
 * immediately to the desired state. For example, if the instance is transitioned to the `Shutdown` state by another
 * component, the state changes will be silently discarded.
 *
 * All interfaces are executed asynchronously (through coroutines) and return their results after the response from the
 * SDK has been recorded. Interfaces that work with streams of data are also executed asynchronously and are wrapped
 * into [flows][Flow] to be compatible with the coroutines. Errors will always be returned immediately if they are
 * discovered and the operation will only be automatically retried, if the returned condition can be recovered from.
 *
 * The signatures of the endpoints of the SDK were slightly adjusted to unify their naming scheme and to better fit into
 * the Kotlin/Java ecosystem. Generally speaking, no hidden logic is embedded into the calls and the value is therefore
 * relayed to the SDK without further changes. The SDK is always kept compatible with the official recommendations and
 * should be used directly whenever possible, as the individual steps are atomic.
 *
 * @see <a href="https://agones.dev/site/docs/guides/client-sdks/">Agones Client SDK Documentation</a>
 */
interface AgonesSdk : AutoCloseable {

    /**
     * Notifies Agones that this instance is now ready to accept player connections. Once this state is set, the
     * associated [GameServer] resource will be set to `Ready`, and the address and ports for accessing this instance
     * will be populated and published. Prior to calling this method, the initialization of this instance must be fully
     * completed.
     *
     * According to the [Agones Documentation](https://agones.dev/site/docs/integration-patterns/reusing-gameservers/),
     * instances are preferred to be shut down and deleted after a game. However, if an instance needs to be reused
     * (after being allocated), a server with this interface can also be reset to its initial state at any time to mark
     * itself as `Ready` again.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#ready">Agones Documentation</a>
     */
    suspend fun ready()

    /**
     * Notifies Agones that this instance is still running and healthy. This notification must be sent periodically,
     * otherwise this instance will be marked as `Unhealthy` and eventually removed. The interval is configured in
     * the Agones [GameServer] resource and tolerates short delays. These health checks are independent of the normal
     * application lifecycle and should be sent from the beginning. They can be sent at an arbitrary interval.
     *
     * This method should be called only once and the emitting of pings in the [Flow] should happen in some thread that
     * is related to the general game loop of this instance. If it is called in an independent thread, the health pings
     * may not necessarily reflect the real health of this instance. The health ping should be sent at a slightly faster
     * interval than is configured within the [GameServer] resource, so that the status does not switch to `Unhealthy`.
     *
     * @param stream The [Flow] that should emit the health pings to the [AgonesSdk] whenever the [Flow] emits [Unit].
     * Defaults to a single ping, if not specified.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#health">Agones Documentation</a>
     */
    suspend fun health(stream: Flow<Unit> = flowOf(Unit))

    /**
     * Notifies Agones to set this instance to `Reserved` state for a specific duration. This prevents the instance from
     * being deleted during that time period and does not trigger any scaling within the fleet. After the duration
     * passed, the instance will be set back to the `Ready` state. While the instance is in the `Reserved` state, it
     * cannot be deleted by fleet updates or scaling, and it cannot be assigned with a `GameServerAllocation`. Invoking
     * any state changing methods like [allocate], [ready] or [shutdown] deactivates the scheduled reset to `Ready`
     * after the duration expires.
     *
     * This method can be used, for example, to enable registration with an external system such as a matchmaker, which
     * required instances to mark themselves as ready for game sessions for a certain period of time. Once the game
     * session has started, the implementation would then call [allocate] to indicate that the instance is now in
     * active use and possibly hosting players.
     *
     * @param seconds The duration in seconds for which this instance should be set to the `Reserved` state before
     * transitioning back to `Ready`. A value of `0` indicates an unlimited duration.
     *
     * @throws IllegalArgumentException If the supplied seconds are less than `0` and therefore negative. This is
     * impossible, as the time should be positive or `0` to set the time after which to reset its state.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#reserveseconds">Agones Documentation</a>
     */
    suspend fun reserve(seconds: Long)

    /**
     * Notifies Agones that this instance has been allocated/claimed and is currently not available for any other
     * `GameServerAllocation`s. It is preferred that instance are assigned through `GameServerAllocation`s. However,
     * in cases where this is not possible (e.g., when working with external systems), this interface can be used to
     * manually assign the instance.
     *
     * This sets this [GameServer] into the `Allocated` state, which means, that it won't be deleted on fleet scaling or
     * updates. This indicates, that the instance is currently being used and can therefore not be shut down without
     * interrupting the gameplay.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#allocate">Agones Documentation</a>
     */
    suspend fun allocate()

    /**
     * Notified Agones to shut down this instance and release any allocations for it in the process. The state will
     * immediately be set to `Shutdown` and the pod of this instance will be stopped and deleted. The actual instance
     * shutdown is triggered only through this method call, and the instance will be shut down by the pod triggering
     * a `SIGTERM` signal during termination.
     *
     * This method is preferred over a normal shutdown (e.g., using [System.exit]) as it changes the status within the
     * [GameServer] resource earlier, allowing the rest of the infrastructure to prepare for the shutdown of this
     * instance. It also makes the intent more clear, as this means, that the instance not just randomly crashed.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#shutdown">Agones Documentation</a>
     */
    suspend fun shutdown()

    /**
     * Adds a new label with a specific key and value to the [GameServer] resource of this instance. The key always
     * gets a [prefix][METADATA_KEY_PREFIX] assigned automatically to ensure better isolation and security. Therefore,
     * the keys cannot collide with normal Kubernetes labels. This allows further metadata to be published about this
     * instance, which can be read elsewhere, and it is possible to filter the [GameServer] instances more precisely
     * using the key-value pair.
     *
     * @param key The key of the label that should be added or updated for this [GameServer] instance.
     * @param value The value of the label that should be added or updated for this [GameServer] instance.
     *
     * @throws IllegalArgumentException If the key does not match the [pattern][META_KEY_PATTERN] for label keys and
     * can therefore not be accepted for the Kubernetes [GameServer] resource.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#setlabelkey-value">Agones Documentation</a>
     */
    suspend fun label(key: String, value: String)

    /**
     * Adds a new annotation with a specific key and value to the [GameServer] resource of this instance. The key always
     * gets a [prefix][METADATA_KEY_PREFIX] assigned automatically to ensure better isolation and security. Therefore,
     * the keys cannot collide with normal Kubernetes annotations. This allows further metadata to be published about
     * this instance, which can be read elsewhere, and it is possible to filter the [GameServer] instances more
     * precisely using the key-value pair.
     *
     * @param key The key of the annotation that should be added or updated for this [GameServer] instance.
     * @param value The value of the annotation that should be added or updated for this [GameServer] instance.
     *
     * @throws IllegalArgumentException If the key does not match the [pattern][META_KEY_PATTERN] for annotation keys
     * and can therefore not be accepted for the Kubernetes [GameServer] resource.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#setannotationkey-value">Agones Documentation</a>
     */
    suspend fun annotation(key: String, value: String)

    /**
     * Obtains a new, immutable Snapshot of the [GameServer] resource of this instance. The data includes, among meta
     * information, the current state, the allocation/assignment and the configuration as present in the Kubernetes
     * resource. This snapshot cannot be changed and modifications to the state have to happen through the other
     * interfaces of the [Agones SDK][AgonesSdk]. The returned value is guaranteed to match the value set within the
     * SDK, even if the value was not yet synchronized with the Kubernetes resource.
     *
     * @return The [GameServer] resource that represents this instance within Agones and within Kubernetes.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#gameserver">Agones Documentation</a>
     */
    suspend fun gameServer(): GameServer

    /**
     * Subscribes to changes of the [GameServer] resource of this instance. The [Flow] receives a new item, whenever
     * a change is made to the resource. For changes triggered by this SDK as well as for external changes applied
     * directly to the [GameServer] resource within Kubernetes. The items always contain all properties of the updated
     * [GameServer] resource and not just the updated fields.
     *
     * The first item, that is received right after registration, corresponds to the current state of the resource
     * (without any change having occurred). The [Flow] is triggered asynchronously and the streamed items can be
     * inspected and processed concurrently. New states are waiting in the [Flow] to be fetched, so the implementation
     * does not have to be thread-safe.
     *
     * To unsubscribe from the updates, the returned [Flow] can be cancelled and the underlying stream will be closed
     * and cleaned up. It can be started to subscribe again after that, by invoking this method again and obtaining
     * a new, independent [Flow].
     *
     * Since the stream (if not terminated beforehand) runs indefinitely, it may delay the shutdown of the SDK, and it
     * should be terminated beforehand. To avoid waiting for the maximum timeout, all streams should be closed
     * beforehand. If there are still open streams, an error will be published in the [Flow], and the underlying stream
     * in gRPC will be terminated.
     *
     * @return A [Flow] of updated [GameServer] resources that returns a new element every time there was an update to
     * the underlying data. Always contains the whole dataset and not only the modified fields.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#watchgameserverfunctiongameserver">Agones Documentation</a>
     */
    fun watchGameServer(): Flow<GameServer>

    /**
     * Closes all open resources that are associated with the [Agones SDK][AgonesSdk]. After this operation, this
     * instance of the [Agones SDK][AgonesSdk] may no longer be used, as all connections are no longer usable. This
     * method is idempotent and can therefore be called any number of times without changing its behaviour. It is
     * guaranteed, that after this call all open connections and allocated resources will be closed or released.
     * Although not all implementations have such resources, the method should still always be called (for example,
     * within a Try-With_Resources block or a use method) to cleanly terminate resource usage.
     *
     * **Implementation Note**: Closing the open connections and releasing the allocated resources must happen blocking
     * so that the main thread can be safely shut down after this method was called. The difference of this method to
     * [AutoCloseable.close] is, that it is not permitted to trigger any exceptions within this method.
     */
    override fun close()

    /**
     * Returns the encapsulated [Alpha-Channel][Alpha] of the [Agones SDK][AgonesSdk]. This sub channel allows the use
     * of the less tested and isolated interfaces of the [Agones SDK][AgonesSdk]. The retrieval of the channel does not
     * trigger any communication with the SDK. The interfaces of the [Alpha-Channel][Alpha] were not yet extensively
     * tested and are deactivated by default. The endpoints can be freely used but some bugs are to be expected.
     *
     * @return The encapsulated [Alpha-Channel][Alpha] of the [Agones SDK][AgonesSdk], that can be used to access the
     * alpha endpoints.
     */
    fun alpha(): Alpha

    /**
     * Returns the encapsulated [Beta-Channel][Beta] of the [Agones SDK][AgonesSdk]. This sub channel allows the use of
     * the less tested and isolated interfaces of the [Agones SDK][AgonesSdk]. The retrieval of the channel does not
     * trigger any communication with the SDK. The interfaces of the [Beta-Channel][Beta] were already extensively
     * tested and are activated by default. The endpoints can be freely used and only very few bugs are expected.
     *
     * @return The encapsulated [Beta-Channel][Beta] of the [Agones SDK][AgonesSdk], that can be used to access the
     * beta endpoints.
     */
    fun beta(): Beta

    /**
     * The [Alpha-Channel][Alpha] provides the interfaces of the [Agones SDK][AgonesSdk] that have not yet been
     * extensively tested and have been deactivated by default. The provided endpoints can change drastically from one
     * version to the next, and they may be removed at any time. But the provided endpoints can be used without any
     * restrictions but there may be some bugs, as the feature has not been used broadly. As soon as the features have
     * been tested more and have proven to be useful to the Agones system, they will be promoted to the
     * [Beta-Channel][Beta]. The [Alpha-Channel][Alpha] can be retrieved through [alpha] from an instance of the
     * [Agones SDK][AgonesSdk].
     */
    interface Alpha {

        /**
         * Adds a player with a specific unique identifier to the player list of this instance. This will increase the
         * active player count and add the identifier to the Kubernetes resource eventually. The player will be
         * guaranteed to be part of the player list of this instance, unless this instance is already full. The returned
         * value is guaranteed to match the value set within the SDK, even if the value was not yet synchronized with
         * the Kubernetes resource.
         *
         * If the [capacity][playerCapacity] of this instance has already been reached and the player cannot be added
         * to the player list because of this reason, an [IllegalStateException] will be thrown and no result will be
         * returned for this method.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @param playerId The unique identifier of the player that should be added to the player list of this instance.
         *
         * @return Whether this player was previously missing from the player list of this instance and the capacity has
         * not yet been reached and whether the player could therefore be successfully added to the list.
         *
         * @throws NullPointerException If `null` is supplied as the player identifier and therefore no player can be
         * derived from it.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaplayerconnectplayerid">Agones Documentation</a>
         */
        suspend fun playerConnect(playerId: String): Boolean

        /**
         * Removes a player with a specific unique identifier from the player list of this instance. This will decrease
         * the active player count and remove the identifier from the Kubernetes resource eventually. The player will
         * then no longer be part of this instance. The return value is guaranteed to match the value set within the
         * SDK, even if the value was not yet synchronized with the Kubernetes resource.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @param playerId The unique identifier of the player that should be removed from the player list of this
         * instance.
         *
         * @return Whether this player previously was in the player list of this instance and therefore could be
         * successfully removed from it.
         *
         * @throws NullPointerException If `null` is supplied as the player identifier and therefore no player can be
         * derived from it.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaplayerdisconnectplayerid">Agones Documentation</a>
         */
        suspend fun playerDisconnect(playerId: String): Boolean

        /**
         * Retrieves the unique identifiers of the current players associated with (and therefore connected to) this
         * instance. The return value is guaranteed to match the value set within the SDK, even if the value was not yet
         * synchronized with the Kubernetes resource.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @return A [list][List] of the unique identifiers of the currently connected players of this instance.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetconnectedplayers">Agones Documentation</a>
         */
        suspend fun connectedPlayers(): List<String>

        /**
         * Retrieves whether a player with a specific identifier is currently connected to this instance and is
         * therefore present on the player list of this instance. The return value is guaranteed to match the value set
         * within the SDK, even if the value was not yet synchronized with the Kubernetes resource.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @param playerId The unique identifier of the player for whom to check if they are currently present in the
         * player list of this instance.
         *
         * @return Whether the player with the supplied unique identifier is currently present in the player list of
         * this instance.
         *
         * @throws NullPointerException If `null` is supplied as the player identifier and therefore no player can be
         * derived from it.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaisplayerconnectedplayerid">Agones Documentation</a>
         */
        suspend fun isPlayerConnected(playerId: String): Boolean

        /**
         * Retrieves the current amount of players that are connected to this instance simultaneously. The return value
         * is guaranteed to match the value set within the SDK, even if the value was not yet synchronized with the
         * Kubernetes resource.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @return The current amount of players that are connected to this instance simultaneously.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetplayercount">Agones Documentation</a>
         */
        suspend fun playerCount(): Long

        /**
         * Retrieves the current capacity for the amount of players that can be connected to this instance
         * simultaneously. Once this capacity is reached, no further players can be added to this instance. The return
         * value is guaranteed to match the value set within the SDK, even if this value was not yet synchronized with
         * the Kubernetes resource.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @return The current capacity for the amount of players that can be connected to this instance simultaneously.
         * The value `0` represents that no player should be allowed on this instance at all.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetplayercapacitycount">Agones Documentation</a>
         */
        suspend fun playerCapacity(): Long

        /**
         * Sets a new capacity for the amount of players that can be connected to this instance simultaneously. The
         * limit will not be enforced retroactively. That means, if there are currently more players on this instance,
         * than the new capacity would allow, no new players will be allowed to connect on this instance, but existing
         * players will not be disconnected from this instance.
         *
         * The specific player tracking APIs of the [AgonesSdk] will eventually be superseded by the arbitrary
         * [Counts and Lists](https://github.com/googleforgames/agones/pull/2946) that cover a broader use-case and
         * offer similar interfaces for tracking.
         *
         * @param capacity The new capacity for the amount of simultaneously connected players for this instance. The
         * value `0` represents that no player should be allowed on this instance at all.
         *
         * @throws IllegalArgumentException If a negative number is provided as the new player capacity.
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphasetplayercapacitycount">Agones Documentation</a>
         */
        suspend fun playerCapacity(capacity: Long)
    }

    /**
     * The [Beta-Channel][Beta] provides the interfaces of the [Agones SDK][AgonesSdk] that may have already been
     * extensively tested and have been activated by default, but they haven't been used broadly enough to promote them
     * to the Stable-Channel yet. But the provided endpoints can be used without any restrictions or preparations and
     * only very few bugs are expected. As soon as the features have been tested enough and have proven to be useful
     * to the Agones system, they will be promoted to the Stable-Channel. The [Beta-Channel][Beta] can be retrieved
     * through [beta] from an instance of the [Agones SDK][AgonesSdk].
     */
    interface Beta {
        // currently, there are no beta sdk features
    }

    companion object {
        /** The hostname of the Agones SDK sidecar from within a container of the same pod for the communication. */
        const val AGONES_SDK_HOST: String = "127.0.0.1"

        /** The prefix that will be automatically prepended to all labels and annotations for their isolation, */
        const val METADATA_KEY_PREFIX: String = "agones.dev/sdk-"

        /** The pattern that the key of labels and annotations (without its prefix) should match to be accepted. */
        internal val META_KEY_PATTERN = Pattern.compile("[a-z0-9A-Z]([a-z0-9A-Z_.-])*[a-z0-9A-Z]")

        /** The period of time that should be waited for successful shutdown after [shutdown] was invoked. */
        internal val SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(5)
    }
}
