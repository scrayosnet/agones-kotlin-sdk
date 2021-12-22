package net.justchunks.agones.client;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Eine {@link AgonesSdkFactory} ist eine Fabrik für die Erstellung neuer Instanzen von {@link AgonesSdk Agones SDKs}.
 * Durch diese Fabrik können die konkreten Implementationen der SDK bezogen werden und anschließend für die jeweilige
 * Plattform verwendet werden. Die Fabrik gibt bereits Standardwerte und Präferenzen für die verschiedenen
 * Implementationen vor, sodass die robusteste und performanteste Variante gewählt wird.
 */
public final class AgonesSdkFactory {

    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Ein privater Konstruktor um die Instantiierung dieser Fabrikklasse zu verhindern. Der Konstruktor löst in jedem
     * Fall eine {@link UnsupportedOperationException} aus und wurde nur implementiert, um eine Instantiierung dieser
     * Klasse unmöglich zu machen.
     */
    @Contract(value = " -> fail", pure = true)
    private AgonesSdkFactory() {
        // always throw an exception as this class should not be instantiated
        throw new UnsupportedOperationException(
            "This is a utility class and therefore cannot be instantiated."
        );
    }
    //</editor-fold>


    //<editor-fold desc="factory">
    /**
     * Erstellt eine neue Instanz eines {@link AgonesSdk Agones SDKs} mit der bestmöglichen Robustheit und Performance.
     * Diese Methode gibt bei jedem Aufruf eine vollständig neue Instanz zurück, die keine Verbindungen mit den zuvor
     * erstellten Instanzen hat. Insbesondere wird garantiert, dass zwei Aufrufe dieser Methode unterschiedliche Objekte
     * erzeugen. Jede Plattform benötigt nur eine einzige Instanz des {@link AgonesSdk Agones SDKs}.
     *
     * <p>Der übergebene {@link ScheduledExecutorService Executor-Service} wird von dem {@link AgonesSdk Agones SDK}
     * nicht verwaltet, sondern nur genutzt. Das bedeutet, dass dieser {@link ScheduledExecutorService Executor-Service}
     * an anderer Stelle gestoppt werden muss, sollte dies gewünscht sein. Der Service wird für die periodischen {@link
     * AgonesSdk#health() Health-Pings} und die Ausführung von Callbacks innerhalb der Kommunikation mit der externen
     * Schnittstelle verwendet.
     *
     * @param executorService Der {@link ScheduledExecutorService Executor-Service}, der für die Ausführung der
     *                        Callbacks und das Versenden der {@link AgonesSdk#health() Health-Pings} dieses {@link
     *                        AgonesSdk Agones SDKs} verwendet werden soll.
     *
     * @return Eine neue Instanz eines {@link AgonesSdk Agones SDKs}, die für die Kommunikation mit der externen Agones
     *     Schnittstelle auf dieser Plattform verwendet werden kann.
     */
    @NotNull
    @Contract(value = "_ -> new", pure = true)
    public static AgonesSdk createNewSdk(@NotNull final ScheduledExecutorService executorService) {
        return new GrpcAgonesSdk(executorService);
    }
    //</editor-fold>
}
