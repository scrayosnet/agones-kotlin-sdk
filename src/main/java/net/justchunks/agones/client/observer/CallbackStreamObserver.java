package net.justchunks.agones.client.observer;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Ein {@link CallbackStreamObserver} ist ein {@link StreamObserver Observer} für Streams in gRPC, der die Elemente an
 * einen {@link Consumer Callback} weiterleitet und ihn daher für jedes Element ausführt. Er kann in Fällen genutzt
 * werden, in denen nur die Schnittstelle für neue Elemente relevant ist und kein besonderer Zustand überwacht oder
 * geführt wird. So kann die Lambda-Notation genutzt werden. Fehler bei der Verarbeitung werden dennoch geloggt, um
 * Ausnahmezustände in der Kommunikation bemerken zu können.
 *
 * @param <E> Der generische Typ der Elemente, die innerhalb dieses {@link StreamObserver Observers} abgewickelt werden
 *            sollen.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class CallbackStreamObserver<E> implements StreamObserver<E> {

    //<editor-fold desc="LOGGER">
    /** Der Logger, der für das Senden der Fehlermeldungen  in dieser Klasse verwendet werden soll. */
    @NotNull
    private static final Logger LOG = LogManager.getLogger(CallbackStreamObserver.class);
    //</editor-fold>


    //<editor-fold desc="LOCAL FIELDS">
    /** Der {@link Consumer Callback}, der für die einzelnen Elemente des Streams ausgelöst werden soll. */
    @NotNull
    private final Consumer<E> callback;
    //</editor-fold>


    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Erstellt einen neuen {@link CallbackStreamObserver Callback Observer} für einen bestimmten {@link Consumer
     * Callback}. Durch die Instantiierung wird noch keine Aktion ausgelöst und es können beliebig viele Aktionen mit
     * derselben Instanz durchgeführt werden. Darüber hinaus kann derselbe {@link Consumer Callback} für mehrere
     * Instanzen des {@link CallbackStreamObserver Callback Observers} wiederverwendet werden.
     *
     * @param callback Der {@link Consumer Callback}, der für die einzelnen Elemente des Streams ausgelöst werden soll.
     */
    @Contract(pure = true)
    private CallbackStreamObserver(@NotNull final Consumer<@NotNull E> callback) {
        this.callback = callback;
    }
    //</editor-fold>


    //<editor-fold desc="implementation">
    @Override
    @Contract(pure = true)
    public void onNext(@NotNull final E value) {
        callback.accept(value);
    }

    @Override
    public void onError(@NotNull final Throwable throwable) {
        LOG.warn(
            "A callback stream observer encountered an unknown error!",
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
     * Ermittelt eine statische Instanz des {@link CallbackStreamObserver Callback Observers} mit einem bestimmten,
     * generischen Typ für die Elemente und einem bestimmten {@link Consumer Callback} für die Ausführung. Da der {@link
     * StreamObserver Observer} keinen eigenen Zustand hat, kann diese Instanz beliebig oft wiederverwendet werden. Es
     * wird empfohlen diese Methode zu verwenden, anstatt immer wieder eine neue Instanz mit demselben {@link Consumer
     * Callback} zu erstellen.
     *
     * @param callback Der {@link Consumer Callback}, der für die einzelnen Elemente des Streams ausgelöst werden soll.
     * @param <E>      Der generische Typ der Elemente, die innerhalb dieses {@link StreamObserver Observers}
     *                 abgewickelt werden sollen.
     *
     * @return Eine statische Instanz des {@link CallbackStreamObserver Callback Observers} mit dem übergebenen,
     *     generischen Typ für die Elemente und dem übergebenen {@link Consumer Callback} für die Ausführung.
     */
    @NotNull
    @Contract(pure = true)
    public static <E> CallbackStreamObserver<E> getInstance(
        @NotNull final Consumer<@NotNull E> callback
    ) {
        return new CallbackStreamObserver<>(callback);
    }
    //</editor-fold>
}
