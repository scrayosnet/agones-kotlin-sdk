package net.justchunks.agones.client;

import agones.dev.sdk.SDKGrpc;
import agones.dev.sdk.SDKGrpc.SDKBlockingStub;
import agones.dev.sdk.SDKGrpc.SDKStub;
import agones.dev.sdk.Sdk.Duration;
import agones.dev.sdk.Sdk.Empty;
import agones.dev.sdk.Sdk.GameServer;
import agones.dev.sdk.Sdk.KeyValue;
import agones.dev.sdk.alpha.Alpha.Count;
import agones.dev.sdk.alpha.Alpha.PlayerID;
import com.google.common.base.Preconditions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.justchunks.agones.client.observer.CallbackStreamObserver;
import net.justchunks.agones.client.observer.NoopStreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Eine {@link GrpcAgonesSdk} stellt eine gRPC-Implementation des {@link AgonesSdk Agones SDKs} dar. Die Implementation
 * basiert auf den offiziellen Protobufs die mit Agones veröffentlicht werden. Jede Plattform benötigt nur genau eine
 * Implementation des Agones SDKs, muss sich jedoch nicht selbst um die Auswahl der jeweils besten Implementation
 * kümmern. Stattdessen wird die Implementation durch die Fabrikmethode vorgegeben. Alle Implementationen erfüllen die
 * Spezifikation von Agones vollständig.
 */
@SuppressWarnings({"FieldCanBeLocal", "ConstantConditions", "java:S1192"})
public final class GrpcAgonesSdk implements AgonesSdk {

    //<editor-fold desc="LOGGER">
    /** Der Logger, der für das Senden der Fehlermeldungen  in dieser Klasse verwendet werden soll. */
    @NotNull
    private static final Logger LOG = LogManager.getLogger(GrpcAgonesSdk.class);
    //</editor-fold>


    //<editor-fold desc="CONSTANTS">

    //<editor-fold desc="port">
    /** Der Port, über den die Kommunikation dem das Agones SDK über gRPC standardmäßig stattfindet. */
    @Range(from = 0, to = 65_535)
    private static final int DEFAULT_AGONES_SDK_PORT = 9357;
    /** Der Schlüssel der Umgebungsvariable, aus der der Port für das Agones SDK ausgelesen werden kann. */
    @NotNull
    private static final String AGONES_SDK_PORT_ENV_KEY = "AGONES_SDK_GRPC_PORT";
    //</editor-fold>

    //<editor-fold desc="shutdown">
    /** Die {@link java.time.Duration Dauer}, die beim Herunterfahren maximal gewartet werden soll. */
    @NotNull
    private static final java.time.Duration SHUTDOWN_GRACE_PERIOD = java.time.Duration.ofSeconds(5);
    //</editor-fold>

    //<editor-fold desc="validation">
    /** Das {@link Pattern Muster}, dem die Schlüssel für Labels und Annotationen genügen müssen. */
    @NotNull
    private static final Pattern META_KEY_PATTERN = Pattern.compile("[a-z0-9A-Z]([a-z0-9A-Z_.-])*[a-z0-9A-Z]");
    //</editor-fold>

    //</editor-fold>


    //<editor-fold desc="LOCAL FIELDS">

    //<editor-fold desc="runtime">
    /** Der {@link ScheduledExecutorService Executor-Service}, der für Callbacks und den Health-Task verwendet wird. */
    @NotNull
    private final ScheduledExecutorService executorService;
    /** Der {@link ManagedChannel Channel}, über den die Kommunikation mit der externen Schnittstelle abläuft. */
    @NotNull
    private final ManagedChannel channel;
    //</editor-fold>


    //<editor-fold desc="maintenance">
    /** Der {@link StreamObserver Stream}, in den die Pings des Health-Tasks eingeschleust werden. */
    private StreamObserver<Empty> healthTaskStream;
    //</editor-fold>

    //<editor-fold desc="stubs">
    /** Der asynchrone, nebenläufige Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
    @NotNull
    private final SDKStub stub;
    /** Der synchrone, blockende Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
    @NotNull
    private final SDKBlockingStub blockingStub;
    //</editor-fold>

    //<editor-fold desc="sub-sdks">
    /** Die Instanz des gekapselten {@link Alpha Alpha-Channels} dieser Instanz des {@link AgonesSdk Agones SDKs}. */
    @NotNull
    private final Alpha alphaSdk;
    /** Die Instanz des gekapselten {@link Beta Beta-Channels} dieser Instanz des {@link AgonesSdk Agones SDKs}. */
    @NotNull
    private final Beta betaSdk;
    //</editor-fold>

    //</editor-fold>


    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Erstellt eine neue Instanz der gRPC-Implementation des Agones SDKs. Dafür wird der entsprechende {@link Channel
     * Netzwerk-Channel} dynamisch zusammengebaut. Der Port wird (falls möglich) über die Umgebungsvariable {@value
     * AGONES_SDK_PORT_ENV_KEY} bezogen. Dabei werden für den {@link Channel} die zugehörigen Stubs für asynchrone und
     * synchrone Kommunikation mit der Schnittstelle instantiiert. Durch die Erstellung dieser Instanz wird noch keine
     * Aktion unternommen und entsprechend auch nicht die Kommunikation mit der externen Schnittstelle aufgenommen.
     *
     * @param executorService Der {@link ScheduledExecutorService Executor-Service}, der für das Senden der {@link
     *                        #health() Health-Pings} und das Ausführen der Callbacks verwendet werden soll.
     */
    @Contract(pure = true)
    GrpcAgonesSdk(@NotNull final ScheduledExecutorService executorService) {
        // redirect to the other constructor with automatic port resolution
        this(executorService, AGONES_SDK_HOST, getAutomaticPort());
    }

    /**
     * Erstellt eine neue Instanz der gRPC-Implementation des Agones SDKs. Dafür wird der entsprechende {@link Channel
     * Netzwerk-Channel} mit expliziten Werten zusammengebaut. Der Host und der Port werden direkt übergeben und
     * unverändert für die Erstellung des {@link Channel Channels} genutzt. Dabei werden für den {@link Channel} die
     * zugehörigen Stubs für asynchrone und synchrone Kommunikation mit der Schnittstelle instantiiert. Durch die
     * Erstellung dieser Instanz wird noch keine Aktion unternommen und entsprechend auch nicht die Kommunikation mit
     * der externen Schnittstelle aufgenommen.
     *
     * @param executorService Der {@link ScheduledExecutorService Executor-Service}, der für das Senden der {@link
     *                        #health() Health-Pings} und das Ausführen der Callbacks verwendet werden soll.
     * @param host            Der Host, unter dem der gRPC Server erreichbar ist und zu dem die Verbindung entsprechend
     *                        aufgenommen werden soll.
     * @param port            Der Port, unter dem der gRPC Server erreichbar ist und zu dem die Verbindung entsprechend
     *                        aufgenommen werden soll.
     */
    @Contract(pure = true)
    GrpcAgonesSdk(
        @NotNull final ScheduledExecutorService executorService,
        @NotNull final String host,
        @Range(from = 0, to = 65_535) final int port
    ) {
        // assign the externally generated and submitted executor service
        this.executorService = executorService;

        // assemble the address components and create the corresponding channel (sdk communication does not use TLS)
        this.channel = ManagedChannelBuilder
            .forAddress(host, port)
            .executor(executorService)
            .offloadExecutor(executorService)
            .usePlaintext()
            .build();

        // create the blocking and non-blocking stubs for the communication with agones
        this.stub = SDKGrpc.newStub(channel);
        this.blockingStub = SDKGrpc.newBlockingStub(channel);

        // create the sub-sdks for the other channels
        this.alphaSdk = new GrpcAlpha(channel);
        this.betaSdk = new GrpcBeta(channel);
    }
    //</editor-fold>


    //<editor-fold desc="implementation">
    @Override
    public void ready() {
        // call the endpoint with an empty request and ignore the response
        stub.ready(
            Empty.getDefaultInstance(),
            NoopStreamObserver.getInstance()
        );
    }

    @Override
    public void health() {
        // open a new health ping stream (which does not send any health pings yet)
        final StreamObserver<Empty> sendObserver = stub.health(NoopStreamObserver.getInstance());

        // send the actual health ping
        sendObserver.onNext(Empty.getDefaultInstance());

        // close the ping stream right away
        sendObserver.onCompleted();
    }

    @Override
    public void reserve(@Range(from = 0, to = Integer.MAX_VALUE) final long seconds) {
        // check that the seconds are within the allowed bounds
        Preconditions.checkArgument(
            seconds >= 0,
            "The supplied seconds \"%s\" are not positive!",
            seconds
        );

        // call the endpoint with the duration and ignore the response
        stub.reserve(
            Duration.newBuilder()
                .setSeconds(seconds)
                .build(),
            NoopStreamObserver.getInstance()
        );
    }

    @Override
    public void allocate() {
        // call the endpoint with an empty request and ignore the response
        stub.allocate(
            Empty.getDefaultInstance(),
            NoopStreamObserver.getInstance()
        );
    }

    @Override
    public void shutdown() {
        // call the endpoint with an empty request and ignore the response
        stub.shutdown(
            Empty.getDefaultInstance(),
            NoopStreamObserver.getInstance()
        );
    }

    @Override
    public void label(@NotNull final String key, @NotNull final String value) {
        // check that the label key is allowed within kubernetes
        Preconditions.checkArgument(
            META_KEY_PATTERN.matcher(key).matches(),
            "The supplied key \"%s\" does not match the pattern for label keys.",
            value
        );

        // call the endpoint with the mapping and ignore the response
        stub.setLabel(
            KeyValue.newBuilder()
                .setKey(key)
                .setValue(value)
                .build(),
            NoopStreamObserver.getInstance()
        );
    }

    @Override
    public void annotation(@NotNull final String key, @NotNull final String value) {
        // check that the label key is allowed within kubernetes
        Preconditions.checkArgument(
            META_KEY_PATTERN.matcher(key).matches(),
            "The supplied key \"%s\" does not match the pattern for annotation keys.",
            value
        );

        // call the endpoint with the mapping and ignore the response
        stub.setAnnotation(
            KeyValue.newBuilder()
                .setKey(key)
                .setValue(value)
                .build(),
            NoopStreamObserver.getInstance()
        );
    }

    @NotNull
    @Override
    @Contract(value = " -> new", pure = true)
    public GameServer gameServer() {
        // call the endpoint (synchronously) with an empty request and return the response
        return blockingStub.getGameServer(Empty.getDefaultInstance());
    }

    @Override
    public void watchGameServer(@NotNull final Consumer<@NotNull GameServer> callback) {
        // check that the provided callback actually exists
        Preconditions.checkNotNull(
            callback,
            "The supplied callback cannot be null!"
        );

        // call the endpoint with an empty request and use the callback to handle responses
        stub.watchGameServer(
            Empty.getDefaultInstance(),
            CallbackStreamObserver.getInstance(callback)
        );
    }

    @Override
    public void startHealthTask() {
        // check that the health task was not already started
        Preconditions.checkState(
            healthTaskStream == null,
            "The health task was already started for this SDK and cannot be started again!"
        );

        // assign a new async stream to send the health pings in
        healthTaskStream = stub.health(NoopStreamObserver.getInstance());

        // register the task to periodically send pings
        executorService.scheduleAtFixedRate(
            () -> healthTaskStream.onNext(Empty.getDefaultInstance()),
            0L,
            HEALTH_PING_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public Alpha alpha() {
        return alphaSdk;
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public Beta beta() {
        return betaSdk;
    }
    //</editor-fold>

    //<editor-fold desc="internal">
    @Override
    public void close() {
        try {
            // cancel the running health task (if any)
            if (healthTaskStream != null) {
                healthTaskStream.onCompleted();
            }

            // shutdown and wait for it to complete
            channel
                .shutdown()
                .awaitTermination(SHUTDOWN_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ex) {
            // log so we know the origin/reason for this interruption
            LOG.debug("Thread was interrupted while waiting for the shutdown of a GrpcAgonesSdk.", ex);

            // set interrupted status of this thread
            Thread.currentThread().interrupt();
        }
    }
    //</editor-fold>

    //<editor-fold desc="utility: port resolution">
    /**
     * Ermittelt automatisch den Port für die Verbindung zum gRPC-Server der externen Schnittstelle des {@link AgonesSdk
     * Agones SDKs}. Dabei wird zunächst versucht den Port über die Umgebungsvariable {@value AGONES_SDK_PORT_ENV_KEY}
     * aufzulösen. Ist diese Variable nicht gesetzt, wird zum Standard-Port für die gRPC-Schnittstelle in Agones
     * ({@value DEFAULT_AGONES_SDK_PORT}) zurückgefallen.
     *
     * @return Der automatisch ermittelte Port für die Verbindung zum gRPC-Server der externen Schnittstelle des {@link
     *     AgonesSdk Agones SDKs}.
     */
    @VisibleForTesting
    @Contract(pure = true)
    @Range(from = 0, to = 65_535)
    static int getAutomaticPort() {
        // read the environment variable for the dynamic agones port
        final String textPort = System.getenv(AGONES_SDK_PORT_ENV_KEY);

        // check that there was any value and that it is valid
        if (textPort == null) {
            // fall back to the default port as it could not be found
            return DEFAULT_AGONES_SDK_PORT;
        } else {
            // parse the number from the textual environment variable value
            try {
                return Integer.parseInt(textPort);
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException(
                    "The supplied environment variable for the port did not contain a valid number."
                );
            }
        }
    }
    //</editor-fold>


    /**
     * Die {@link GrpcAlpha GrpcAlpha-Klasse} beschreibt die gRPC-Implementation des {@link Alpha Alpha-Channels} des
     * Agones SDKs. Die Implementation kapselt die Kommunikation mit der externen Schnittstelle des Agones SKDs und
     * verwaltet eigene Stubs dafür. Dieses Sub-SDK operiert auf denselben Channel wie das eigentliche Haupt-SDK.
     */
    public static final class GrpcAlpha implements Alpha {

        //<editor-fold desc="LOCAL FIELDS">
        /** Der asynchrone, nebenläufige Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
        @NotNull
        private final agones.dev.sdk.alpha.SDKGrpc.SDKStub stub;
        /** Der synchrone, blockende Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
        @NotNull
        private final agones.dev.sdk.alpha.SDKGrpc.SDKBlockingStub blockingStub;
        //</editor-fold>


        //<editor-fold desc="CONSTRUCTORS">
        /**
         * Erstellt eine neue Instanz der gRPC-Implementation des {@link Alpha Alpha-Channels} für einen bestimmten
         * {@link Channel Netzwerk-Channel}. Dabei werden für den übergebenen {@link Channel} die zugehörigen Stubs für
         * asynchrone und synchrone Kommunikation mit der Schnittstelle instantiiert. Durch die Erstellung dieser
         * Instanz wird noch keine Aktion unternommen und entsprechend auch nicht die Kommunikation mit der externen
         * Schnittstelle aufgenommen.
         *
         * @param channel Der {@link Channel Netzwerk-Channel}, der für die Kommunikation mit der externen Schnittstelle
         *                des Agones SDKs genutzt werden soll.
         */
        @Contract(pure = true)
        public GrpcAlpha(@NotNull final Channel channel) {
            // create the blocking and non-blocking stubs for the communication with agones
            stub = agones.dev.sdk.alpha.SDKGrpc.newStub(channel);
            blockingStub = agones.dev.sdk.alpha.SDKGrpc.newBlockingStub(channel);
        }
        //</editor-fold>


        //<editor-fold desc="implementation">
        @Override
        public boolean playerConnect(@NotNull final UUID playerId) {
            // check that there was actually player ID supplied
            Preconditions.checkNotNull(
                playerId,
                "The supplied player ID cannot be null!"
            );

            // wrap in try-catch because capacity-overflow throws exception
            try {
                return blockingStub.playerConnect(
                    PlayerID.newBuilder()
                        .setPlayerID(playerId.toString())
                        .build()
                ).getBool();
            } catch (final StatusRuntimeException ex) {
                // get the status for this exception
                final Status state = ex.getStatus();

                // if the player limit is exhausted, convert the exception
                if (state.getCode().value() == 2 && state.getDescription().equals("Players are already at capacity")) {
                    throw new IllegalStateException("Player capacity is exhausted!");
                }

                // in any other case, rethrow the original exception
                throw ex;
            }
        }

        @Override
        public boolean playerDisconnect(@NotNull final UUID playerId) {
            // check that there was actually player ID supplied
            Preconditions.checkNotNull(
                playerId,
                "The supplied player ID cannot be null!"
            );

            // call the endpoint with the player ID and return the response
            return blockingStub.playerDisconnect(
                PlayerID.newBuilder()
                    .setPlayerID(playerId.toString())
                    .build()
            ).getBool();
        }

        @NotNull
        @Override
        @Unmodifiable
        @Contract(value = " -> new", pure = true)
        public List<@NotNull UUID> connectedPlayers() {
            // call the endpoint with an empty request and return the mapped response
            return blockingStub.getConnectedPlayers(
                    agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
                )
                .getListList()
                .stream()
                .map(UUID::fromString)
                .toList();
        }

        @Override
        @Contract(pure = true)
        public boolean isPlayerConnected(@NotNull final UUID playerId) {
            // check that there was actually player ID supplied
            Preconditions.checkNotNull(
                playerId,
                "The supplied player ID cannot be null!"
            );

            // call the endpoint with the player ID and return the response
            return blockingStub.isPlayerConnected(
                PlayerID.newBuilder()
                    .setPlayerID(playerId.toString())
                    .build()
            ).getBool();
        }

        @Override
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        public long playerCount() {
            // call the endpoint with an empty request and return the response
            return blockingStub.getPlayerCount(
                agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
            ).getCount();
        }

        @Override
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        public long playerCapacity() {
            // call the endpoint with an empty request and return the response
            return blockingStub.getPlayerCapacity(
                agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
            ).getCount();
        }

        @Override
        public void playerCapacity(@Range(from = 0, to = Long.MAX_VALUE) final long capacity) {
            // check that the capacity is within allowed bounds
            Preconditions.checkArgument(
                capacity >= 0,
                "The supplied capacity \"%s\" is not positive!",
                capacity
            );

            // call the endpoint with the count and ignore the response
            stub.setPlayerCapacity(
                Count.newBuilder()
                    .setCount(capacity)
                    .build(),
                NoopStreamObserver.getInstance()
            );
        }
        //</editor-fold>
    }


    /**
     * Die {@link GrpcBeta GrpcBeta-Klasse} beschreibt die gRPC-Implementation des {@link Beta Beta-Channels} des Agones
     * SDKs. Die Implementation kapselt die Kommunikation mit der externen Schnittstelle des Agones SKDs und verwaltet
     * eigene Stubs dafür. Dieses Sub-SDK operiert auf denselben Channel wie das eigentliche Haupt-SDK.
     */
    public static final class GrpcBeta implements Beta {

        //<editor-fold desc="LOCAL FIELDS">
        /** Der asynchrone, nebenläufige Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
        @NotNull
        private final agones.dev.sdk.beta.SDKGrpc.SDKStub stub;
        /** Der synchrone, blockende Stub für die Kommunikation mit der externen Schnittstelle des Agones SDKs. */
        @NotNull
        private final agones.dev.sdk.beta.SDKGrpc.SDKBlockingStub blockingStub;
        //</editor-fold>


        //<editor-fold desc="CONSTRUCTORS">
        /**
         * Erstellt eine neue Instanz der gRPC-Implementation des {@link Beta Beta-Channels} für einen bestimmten {@link
         * Channel Netzwerk-Channel}. Dabei werden für den übergebenen {@link Channel} die zugehörigen Stubs für
         * asynchrone und synchrone Kommunikation mit der Schnittstelle instantiiert. Durch die Erstellung dieser
         * Instanz wird noch keine Aktion unternommen und entsprechend auch nicht die Kommunikation mit der externen
         * Schnittstelle aufgenommen.
         *
         * @param channel Der {@link Channel Netzwerk-Channel}, der für die Kommunikation mit der externen Schnittstelle
         *                des Agones SDKs genutzt werden soll.
         */
        @Contract(pure = true)
        public GrpcBeta(@NotNull final Channel channel) {
            // create the blocking and non-blocking stubs for the communication with agones
            stub = agones.dev.sdk.beta.SDKGrpc.newStub(channel);
            blockingStub = agones.dev.sdk.beta.SDKGrpc.newBlockingStub(channel);
        }
        //</editor-fold>
    }
}
