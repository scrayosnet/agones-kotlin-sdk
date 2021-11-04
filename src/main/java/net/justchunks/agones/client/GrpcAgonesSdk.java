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
import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import net.justchunks.agones.client.observer.CallbackStreamOberserver;
import net.justchunks.agones.client.observer.NoopStreamOberserver;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Eine {@link GrpcAgonesSdk} stellt eine gRPC-Implementation des {@link AgonesSdk Agones SDKs} dar. Die Implementation
 * basiert auf den offiziellen Protobufs die mit Agones veröffentlicht werden. Jede Plattform benötigt nur genau eine
 * Implementation des Agones SDKs
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "FieldCanBeLocal"})
public final class GrpcAgonesSdk implements AgonesSdk {

    //<editor-fold desc="CONSTANTS">
    /** Der Port, über den die Kommunikation dem das Agones SDK über gRPC standardmäßig stattfindet. */
    private static final int DEFAULT_AGONES_SDK_PORT = 9357;
    /** Der Schlüssel der Umgebungsvariable, aus der der Port für das Agones SDK ausgelesen werden kann. */
    private static final String AGONES_SDK_PORT_ENV_KEY = "AGONES_SDK_GRPC_PORT";
    //</editor-fold>


    //<editor-fold desc="LOCAL FIELDS">

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
     */
    @Contract(pure = true)
    public GrpcAgonesSdk() {
        // declare the dynamic port that will be retrieved
        final int port;

        // read the environment variable for the dynamic agones port
        final String textPort = System.getenv(AGONES_SDK_PORT_ENV_KEY);

        // check that there was any value and that it is valid
        if (textPort == null) {
            // fall back to the default port as it could not be found
            port = DEFAULT_AGONES_SDK_PORT;
        } else {
            // parse the number from the textual environment variable value
            try {
                port = Integer.parseInt(textPort);
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException(
                    "The supplied environment variable for the port did not contain a valid number."
                );
            }
        }

        // assemble the address components and create the corresponding channel
        final Channel channel = ManagedChannelBuilder
            .forAddress(AGONES_SDK_HOST, port)
            .build();

        // create the blocking and non-blocking stubs for the communication with agones
        stub = SDKGrpc.newStub(channel);
        blockingStub = SDKGrpc.newBlockingStub(channel);

        // create the sub-sdks for the other channels
        alphaSdk = new GrpcAlpha(channel);
        betaSdk = new GrpcBeta(channel);
    }
    //</editor-fold>


    //<editor-fold desc="implementation">
    @Override
    public void ready() {
        stub.ready(
            Empty.getDefaultInstance(),
            NoopStreamOberserver.getInstance()
        );
    }

    @Override
    public void health() {
        stub.health(NoopStreamOberserver.getInstance());
    }

    @Override
    public void reserve(@Range(from = 0, to = Integer.MAX_VALUE) final long seconds) {
        stub.reserve(
            Duration.newBuilder().setSeconds(seconds).build(),
            NoopStreamOberserver.getInstance()
        );
    }

    @Override
    public void allocate() {
        stub.allocate(
            Empty.getDefaultInstance(),
            NoopStreamOberserver.getInstance()
        );
    }

    @Override
    public void shutdown() {
        stub.shutdown(
            Empty.getDefaultInstance(),
            NoopStreamOberserver.getInstance()
        );
    }

    @Override
    public void label(@NotNull final String key, @NotNull final String value) {
        stub.setLabel(
            KeyValue.newBuilder().setKey(key).setValue(value).build(),
            NoopStreamOberserver.getInstance()
        );
    }

    @Override
    public void annotation(@NotNull final String key, @NotNull final String value) {
        stub.setAnnotation(
            KeyValue.newBuilder().setKey(key).setValue(value).build(),
            NoopStreamOberserver.getInstance()
        );
    }

    @NotNull
    @Override
    @Contract(value = " -> new", pure = true)
    public GameServer gameServer() {
        return blockingStub.getGameServer(Empty.getDefaultInstance());
    }

    @Override
    public void watchGameServer(@NotNull final Consumer<@NotNull GameServer> callback) {
        stub.watchGameServer(
            Empty.getDefaultInstance(),
            CallbackStreamOberserver.getInstance(callback)
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
            stub = agones.dev.sdk.alpha.SDKGrpc.newStub(channel);
            blockingStub = agones.dev.sdk.alpha.SDKGrpc.newBlockingStub(channel);
        }
        //</editor-fold>


        //<editor-fold desc="implementation">
        @Override
        public boolean playerConnect(@NotNull final UUID playerId) {
            return blockingStub.playerConnect(
                PlayerID.newBuilder().setPlayerID(playerId.toString()).build()
            ).getBool();
        }

        @Override
        public boolean playerDisconnect(@NotNull final UUID playerId) {
            return blockingStub.playerDisconnect(
                PlayerID.newBuilder().setPlayerID(playerId.toString()).build()
            ).getBool();
        }

        @NotNull
        @Override
        @Unmodifiable
        @Contract(value = " -> new", pure = true)
        public List<@NotNull UUID> connectedPlayers() {
            return blockingStub.getConnectedPlayers(
                    agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
                )
                .getListList()
                .asByteStringList()
                .stream()
                .map(ByteString::toStringUtf8)
                .map(UUID::fromString)
                .toList();
        }

        @Override
        @Contract(pure = true)
        public boolean isPlayerConnected(@NotNull final UUID playerId) {
            return blockingStub.isPlayerConnected(
                PlayerID.newBuilder().setPlayerID(playerId.toString()).build()
            ).getBool();
        }

        @Override
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        public long playerCount() {
            return blockingStub.getPlayerCount(
                agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
            ).getCount();
        }

        @Override
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        public long playerCapacity() {
            return blockingStub.getPlayerCapacity(
                agones.dev.sdk.alpha.Alpha.Empty.getDefaultInstance()
            ).getCount();
        }

        @Override
        public void playerCapacity(@Range(from = 0, to = Long.MAX_VALUE) final long capacity) {
            stub.setPlayerCapacity(
                Count.newBuilder().setCount(capacity).build(),
                NoopStreamOberserver.getInstance()
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
            stub = agones.dev.sdk.beta.SDKGrpc.newStub(channel);
            blockingStub = agones.dev.sdk.beta.SDKGrpc.newBlockingStub(channel);
        }
        //</editor-fold>
    }
}
