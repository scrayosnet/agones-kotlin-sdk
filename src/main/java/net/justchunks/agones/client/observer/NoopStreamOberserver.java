package net.justchunks.agones.client.observer;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Ein {@link NoopStreamOberserver} ist ein {@link StreamObserver Observer} für Streams in gRPC, der die Elemente still
 * verwirft und daher keinerlei Aktion ausführt. Er kann in Fällen genutzt werden, in denen es entweder keine richtige
 * Rückgabe gibt (aber das Protokoll dennoch einen solchen {@link StreamObserver Observer} erfordert) oder in denen die
 * Rückgabe für den speziellen Aufruf irrelevant ist und daher nicht weiter beachtet werden soll. Fehler bei der
 * Verarbeitung werden dennoch geloggt, um Ausnahmezustände in der Kommunikation bemerken zu können.
 *
 * @param <ElementType> Der generische Typ der Elemente, die innerhalb dieses {@link StreamObserver Observers}
 *                      abgewickelt werden sollen.
 */
public final class NoopStreamOberserver<ElementType> implements StreamObserver<ElementType> {

    //<editor-fold desc="LOGGER">
    /** Der Logger, der für das Senden der Fehlermeldungen  in dieser Klasse verwendet werden soll. */
    @NotNull
    private static final Logger LOG = LogManager.getLogger(NoopStreamOberserver.class);
    //</editor-fold>


    //<editor-fold desc="CONSTANTS">
    /** Eine statische Instanz des {@link NoopStreamOberserver No-Op Observers} für die generische Verwendung. */
    @NotNull
    private static final NoopStreamOberserver<?> INSTANCE = new NoopStreamOberserver<>();
    //</editor-fold>


    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Erstellt einen neuen {@link NoopStreamOberserver No-Op Observer} der für jeden generischen Typ genutzt werden
     * kann, da er keine Aktionen beinhaltet. Durch die Instantiierung wird so ebenfalls keine Aktion ausgelöst und es
     * können beliebig viele Aufrufe mit derselben Instanz durchgeführt werden. Dieser Konstruktor existiert nur, um die
     * Instantiierung außerhalb der Factory-Methode zu verhindern.
     */
    @Contract(pure = true)
    private NoopStreamOberserver() {
        // do nothing – this constructor only exists to limit to singleton usage
    }
    //</editor-fold>


    //<editor-fold desc="implementation">
    @Override
    @Contract(pure = true)
    public void onNext(@NotNull final ElementType value) {
        // intentionally empty – this observer does nothing
    }

    @Override
    public void onError(@NotNull final Throwable throwable) {
        LOG.warn(
            "A no-op stream observer encountered an unknown error!",
            throwable
        );
    }

    @Override
    @Contract(pure = true)
    public void onCompleted() {
        // intentionally empty – this observer does nothing
    }
    //</editor-fold>

    //<editor-fold desc="utility">
    /**
     * Ermittelt eine statische Instanz des {@link NoopStreamOberserver No-Op Observers} mit einem bestimmten,
     * generischen Typ für die Elemente. Da der {@link StreamObserver Observer} keinen eigenen Zustand hat, kann diese
     * Instanz beliebig oft wiederverwendet werden. Es wird empfohlen diese Methode zu verwenden, anstatt immer wieder
     * eine neue Instanz zu erstellen.
     *
     * @param <ElementType> Der generische Typ der Elemente, die innerhalb dieses {@link StreamObserver Observers}
     *                      abgewickelt werden sollen.
     *
     * @return Eine statische Instanz des {@link NoopStreamOberserver No-Op Observers} mit dem übergebenen, generischen
     *     Typ für die Elemente.
     */
    @NotNull
    @Contract(pure = true)
    @SuppressWarnings("unchecked")
    public static <ElementType> NoopStreamOberserver<ElementType> getInstance() {
        return (NoopStreamOberserver<ElementType>) INSTANCE;
    }
    //</editor-fold>
}
