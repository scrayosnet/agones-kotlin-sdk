package net.justchunks.agones.client.task;

import net.justchunks.agones.client.AgonesSdk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Ein {@link AgonesHealthTask Agones Health Task} ist ein {@link Runnable}, das periodisch ausgeführt werden muss und
 * dem Agones SDK meldet, dass dieser Dienst nach wie vor valide ist und läuft. Dieser Task dient also dazu abgestürzte
 * Server zu identifizieren und die sich darauf befindlichen Spieler zu migrieren. Er sollte direkt nach dem Start der
 * Instanz eingebunden werden, auch wenn diese Instanz zu diesem Zeitpunkt noch nicht {@link AgonesSdk#ready() bereit}
 * für Nutzer ist. Der Ping gibt keine Auskunft darüber, wie die Instanz läuft, sondern nur darüber, dass die Instanz
 * läuft und sich nicht in einem Ausnahmezustand befindet.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class AgonesHealthTask implements Runnable {

    //<editor-fold desc="LOCAL FIELDS">
    /** Die {@link AgonesSdk Agones SDK}, die für das periodische Senden der Health-Pings verwendet werden soll. */
    @NotNull
    private final AgonesSdk sdk;
    //</editor-fold>


    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Erstellt einen neuen {@link AgonesHealthTask} mit einer bestimmten Referenz zu dem {@link AgonesSdk Agones SDK}.
     * Durch die Erstellung wird noch kein Task registriert und entsprechend werden auch keine Health-Pings versendet.
     * Die Instanz hat keinen Zustand und kann daher beliebig wiederverwendet werden.
     *
     * @param sdk Die {@link AgonesSdk Agones SDK}, die für das periodische Senden der Health-Pings verwendet werden
     *            soll.
     */
    @Contract(pure = true)
    public AgonesHealthTask(@NotNull final AgonesSdk sdk) {
        this.sdk = sdk;
    }
    //</editor-fold>


    //<editor-fold desc="implementation">
    @Override
    public void run() {
        // trigger the health ping for this iteration
        sdk.health();
    }
    //</editor-fold>
}
