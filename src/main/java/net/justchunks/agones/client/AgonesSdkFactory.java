package net.justchunks.agones.client;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Eine {@link AgonesSdkFactory} ist eine Fabrik für die Erstellung neuer Instanzen von {@link AgonesSdk Agones SDKs}.
 * Durch diese Fabrik können die konkreten Implementationen der SDK bezogen werden und anschließend für die jeweilige
 * Plattform verwendet werden. Die Fabrik gibt bereits Standardwerte und Präferenzen für die verschiedenen
 * Implementationen vor, sodass die robusteste und performanteste Variante gewählt wird.
 */
public final class AgonesSdkFactory {

    //<editor-fold desc="CONSTRUCTORS">
    /**
     * Ein leerer Konstruktor um die Instantiierung dieser Fabrikklasse zu verhindern. Der Konstruktor bewirkt
     * entsprechend keinerlei Aktionen und wurde nur aus technischen Gründen implementiert.
     */
    @Contract(pure = true)
    private AgonesSdkFactory() {
        // intentionally empty – this is a factory class
    }
    //</editor-fold>


    //<editor-fold desc="factory">
    /**
     * Erstellt eine neue Instanz eines {@link AgonesSdk Agones SDKs} mit der bestmöglichen Robustheit und Performance.
     * Diese Methode gibt bei jedem Aufruf eine vollständig neue Instanz zurück, die keine Verbindungen mit den zuvor
     * erstellten Instanzen hat. Insbesondere wird garantiert, dass zwei Aufrufe dieser Methode unterschiedliche Objekte
     * erzeugen. Jede Plattform benötigt nur eine einzige Instanz des {@link AgonesSdk Agones SDKs}.
     *
     * @return Eine neue Instanz eines {@link AgonesSdk Agones SDKs}, die für die Kommunikation mit der externen Agones
     *     Schnittstelle auf dieser Plattform verwendet werden kann.
     */
    @NotNull
    @Contract(value = " -> new", pure = true)
    public static AgonesSdk createNewSdk() {
        return new GrpcAgonesSdk();
    }
    //</editor-fold>
}
