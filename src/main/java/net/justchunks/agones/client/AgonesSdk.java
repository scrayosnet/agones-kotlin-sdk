package net.justchunks.agones.client;

import agones.dev.sdk.Sdk.GameServer;
import org.intellij.lang.annotations.Pattern;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Das {@link AgonesSdk Agones SDK} stellt die technische Schnittstelle dar, mit der dieser Server Agones seinen
 * aktuellen Zustand mitteilen kann. Mit diesem SDK können keine neuen Ressourcen in Agones angefragt werden.
 * Stattdessen dienen die Methoden dazu, den Zustand der eigenen GameServer-Ressource zu bearbeiten und abzufragen. Das
 * SDK fokussiert sich also auf die eigene Instanz und ignoriert dabei den Rest des Clusters vollständig. Für Anfragen
 * an Agones bzw. das Cluster sollte stattdessen das Kubernetes API verwendet werden.
 *
 * <p>Agones unterteilt seine Schnittstellen in drei unterschiedliche Channels um deren Verwendung und Reife zu
 * signalisieren bzw. sich die Möglichkeit einzuräumen, diese Schnittstellen noch aktiv zu verändern oder bei Problemen
 * vollständig zu entfernen. Die verfügbaren Channels sind:
 * <ul>
 * <li>
 *     <b>Stable</b>: Die Endpunkte wurden sehr umfangreich getestet wurden und die allgemeine Verwendung wird
 *     ausdrücklich empfohlen bzw. wird benötigt. Es wird garantiert, dass sie für viele Versionen in ihrer aktuellen
 *     Form erhalten bleiben. Diese Endpunkte sind direkt über dieses Interface verfügbar und werden nicht besonders
 *     gekapselt.
 * </li>
 * <li>
 *     <b>Beta</b>: Die Endpunkte wurden bereits in einem gewissen Umfang getestet und von der allgemeinen Verwendung
 *     wird nicht abgeraten. Sie sind standardmäßig aktiviert und es wird garantiert, dass es keine größeren Änderungen
 *     mehr geben wird. Diese Endpunkte sind über den Aufruf von {@link #beta()} in einer gekapselten Schnittstelle
 *     verfügbar.
 * </li>
 * <li>
 *     <b>Alpha</b>: Die Endpunkte wurden nur sehr oberflächlich oder selten getestet und die allgemeine Verwendung wird
 *     nicht empfohlen. Sie sind standardmäßig deaktiviert und es kann jederzeit zu größeren Änderungen kommen oder die
 *     Endpunkte fallen vollständig weg. Diese Endpunkte sind über den Aufruf von {@link #alpha()} in einer gekapselten
 *     Schnittstelle verfügbar.
 * </li>
 * </ul>
 *
 * <p>Die Schnittstellen, die den Zustand verändern, garantieren nicht, dass die Ressource in Kubernetes auch
 * tatsächlich unmittelbar in den gewünschten Zustand wechselt. Wenn die Instanz beispielsweise durch eine andere
 * Komponente in den Zustand {@code Shutdown} versetzt wird, werden die Zustandsänderungen still verworfen. Da wir aber
 * ohnehin wenig externe Änderungen vornehmen, muss dieser Umstand nicht besonders beachtet werden.
 *
 * <p>Alle Schnittstellen ohne Rückgabetyp werden asynchron ausgeführt. Lediglich die Schnittstellen, die eine Rückgabe
 * liefern, die direkt verarbeitet werden muss, werden synchron (blocking) ausgeführt. Schnittstellen, die mit
 * {@link Consumer Callbacks} arbeiten, werden ebenfalls asynchron ausgeführt und erhalten ihre Rückgabe asynchron über
 * den entsprechenden {@link Consumer Callback}.
 *
 * <p>Die Signaturen der Endpunkte des SDKs wurden teilweise geringfügig auf unsere Struktur angepasst, entsprechen aber
 * im Allgemeinen den offiziellen Schnittstellen. Das SDK wird immer kompatibel zu den offiziellen Empfehlungen gehalten
 * und sollte möglichst direkt verwendet werden, da die einzelnen Schritte atomar aufgebaut sind. Es wird garantiert,
 * dass die Ressourcen, auf die die SDKs operieren, an keiner anderen Stelle modifiziert werden.
 *
 * @see <a href="https://agones.dev/site/docs/guides/client-sdks/">Agones Client SDK Dokumentation</a>
 */
public interface AgonesSdk {

    //<editor-fold desc="CONSTANTS">

    //<editor-fold desc="channel">
    /** Der Host, unter dem das Agones SDK aus Sicht dieser Instanz für die Kommunikation erreicht werden kann. */
    @NonNls
    @NotNull
    String AGONES_SDK_HOST = "127.0.0.1";
    //</editor-fold>

    //<editor-fold desc="health">
    /** Das Intervall, in dem periodisch an Agones Health-Pings für den Status gesendet werden sollen. */
    @NotNull
    Duration HEALTH_PING_INTERVAL = Duration.ofSeconds(5);
    //</editor-fold>

    //</editor-fold>


    //<editor-fold desc="lifecycle management">

    /**
     * Teilt Agones mit, dass diese Instanz nun bereit ist Spieler-Verbindungen anzunehmen. Sobald dieser Zustand
     * gesetzt wurde, wird die verbundene GameServer-Ressource auf {@code Ready} gesetzt und die Adresse sowie die Ports
     * für den Zugriff auf diese Instanz werden generiert und freigegeben. Zuvor muss also die Initialisierung dieser
     * Instanz vollständig abgeschlossen worden sein.
     *
     * <p>Agones bevorzugt laut Dokumentation, dass die Instanzen nach einem Spiel {@link #shutdown() heruntergefahren}
     * und gelöscht werden. Sollte eine Instanz allerdings (nach der {@link #allocate() Nutzung}) wiederverwendet
     * werden, so kann ein Server mit dieser Schnittstelle auch jederzeit wieder in den anfänglichen Zustand
     * zurückversetzt werden, um sich erneut als Bereit zu markieren.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#ready">Agones Dokumentation</a>
     */
    void ready();

    /**
     * Teilt Agones mit, dass diese Instanz nach wie vor läuft und valide ist. Diese Mitteilung muss periodisch gesendet
     * werden, da diese Instanz sonst als {@code Unhealthy} gekennzeichnet und so nach einiger Zeit entfernt wird. Das
     * Intervall wird in der Konfiguration für Agones festgelegt und toleriert kurze Verzögerungen. Diese Meldungen sind
     * unabhängig vom gewöhnlichen Lifecycle und sollten daher von Anfang an versendet werden.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#health">Agones Dokumentation</a>
     */
    void health();

    /**
     * Teilt Agones mit, dass diese Instanz für eine bestimmte Zeit in den Zustand {@code Reserved} versetzt werden
     * soll. Dadurch kann diese Instanz innerhalb des Zeitraums nicht gelöscht werden, löst aber auch keine Skalierung
     * innerhalb der Fleet aus. Anschließend wird diese Instanz wieder in den Zustand {@code Ready} zurückversetzt.
     * Während sich diese Instanz im Zustand {@code Reserved} befindet, kann sie weder durch Fleet Updates, noch durch
     * Skalierungen gelöscht werden und kann auch nicht mit einer {@code GameServerAllocation} zugewiesen werden.
     *
     * <p>Der Aufruf anderer Schnittstellen, die den Zustand verändern ({@link #ready()}, {@link #allocate()})
     * deaktivieren das Zurücksetzen des Zustands auf {@link #ready()} nach dem Ablauf der Sekunden.
     *
     * @param seconds Die Dauer in Sekunden, für die diese Instanz in den Zustand {@code Reserved} versetzt werden soll,
     *                bevor sie zu {@code Ready} zurückfällt. Der Wert {@code 0} steht für eine unbegrenzte Dauer.
     *
     * @throws IllegalArgumentException Falls die übergebenen Sekunden kleiner als {@code 0} und damit negativ sind, da
     *                                  hierdurch die Zeit vorgegeben wird, nach der der Status wieder auf den vorigen
     *                                  Zustand zurückgesetzt wird.
     * @apiNote Diese Methode kann zum Beispiel verwendet werden, um die Registrierung in einem externen System wie
     *     einem Matchmaker zu ermöglichen, der voraussetzt, dass die Instanzen sich für eine bestimmte Zeit für
     *     Spiel-Sessionen als bereit markieren. Sobald die Spiel-Session gestartet hat, würde die Implementation dann
     *     {@link #allocate()} aufrufen, um mitzuteilen, dass die Instanz nun Spieler hält.
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#reserveseconds">Agones Dokumentation</a>
     */
    void reserve(@Range(from = 0, to = Long.MAX_VALUE) long seconds);

    /**
     * Teilt Agones mit, dass diese Instanz beansprucht wurde und daher aktuell nicht für {@code GameServerAllocations}
     * genutzt werden kann. Es wird bevorzugt, dass die Instanzen über {@code GameServerAllocations} zugewiesen werden.
     * Aber da dies (im Zusammenspiel mit externen Systemen) nicht immer möglich ist, kann diese Schnittstelle in diesen
     * Fällen verwendet werden, um die Instanz manuell zuzuweisen.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#allocate">Agones Dokumentation</a>
     */
    void allocate();

    /**
     * Teilt Agones mit, dass diese GameServer Instanz heruntergefahren werden kann. Der Zustand wird unmittelbar auf
     * {@code Shutdown} gesetzt und der Pod dieser Instanz wird gestoppt und gelöscht. Die eigentliche Instanz stößt das
     * Herunterfahren also nur durch diesen Aufruf an. Und diese Instanz wird anschließend dadurch heruntergefahren,
     * dass der Pod bei der Terminierung ein {@code SIGTERM} Signal auslöst. Falls die Instanz zusätzlich zu diesem
     * Aufruf auch schon das Herunterfahren beginnt, kann es zu unerwarteten Restarts kommen, bis der Pod tatsächlich
     * terminiert wurde.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#shutdown">Agones Dokumentation</a>
     */
    void shutdown();
    //</editor-fold>

    //<editor-fold desc="metadata management">

    /**
     * Fügt der Ressource dieser Instanz innerhalb von Kubernetes ein neues Label mit einem bestimmten Schlüssel und
     * einem bestimmten Wert hinzu. Dem Schlüssel wird immer das Präfix {@code agones.dev/sdk-} vorangestellt um eine
     * bessere Isolation und Sicherheit gewährleisten zu können. Dadurch können weitere Metadaten über diese Instanz
     * veröffentlicht werden, die an anderer Stelle ausgelesen werden können und es ist möglich über das
     * Schlüssel-Wert-Paar die GameServer Instanzen näher zu filtern.
     *
     * @param key   Der Schlüssel des Labels, das dieser Instanz neu zugewiesen werden soll.
     * @param value Der Wert des Labels, das dieser Instanz neu zugewiesen werden soll.
     *
     * @throws IllegalArgumentException Falls der Schlüssel nicht dem vorgegebenen Muster für Label-Namen entspricht und
     *                                  daher nicht für die GameServer-Ressource innerhalb von Kubernetes übernommen
     *                                  werden kann.
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#setlabelkey-value">Agones Dokumentation</a>
     */
    void label(
        @NotNull @NonNls @Subst("key") @Pattern("[a-z0-9A-Z]([a-z0-9A-Z_.-])*[a-z0-9A-Z]") String key,
        @NotNull String value
    );

    /**
     * Fügt der Ressource dieser Instanz innerhalb von Kubernetes eine neue Annotation mit einem bestimmten Schlüssel
     * und einem bestimmten Wert hinzu. Dem Schlüssel wird immer das Präfix {@code agones.dev/sdk-} vorangestellt um
     * eine bessere Isolation und Sicherheit gewährleisten zu können. Dadurch können weitere Metadaten über diese
     * Instanz veröffentlicht werden, die an anderer Stelle ausgelesen werden können.
     *
     * @param key   Der Schlüssel der Annotation, die dieser Instanz neu zugewiesen werden soll.
     * @param value Der Wert der Annotation, die dieser Instanz neu zugewiesen werden soll.
     *
     * @throws IllegalArgumentException Falls der Schlüssel nicht dem vorgegebenen Muster für Annotation-Namen
     *                                  entspricht und daher nicht für die GameServer-Ressource innerhalb von Kubernetes
     *                                  übernommen werden kann.
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#setannotationkey-value">Agones Dokumentation</a>
     */
    void annotation(
        @NotNull @NonNls @Subst("key") @Pattern("[a-z0-9A-Z]([a-z0-9A-Z_.-])*[a-z0-9A-Z]") String key,
        @NotNull String value
    );
    //</editor-fold>

    //<editor-fold desc="configuration retrieval">

    /**
     * Ermittelt einen neuen, unveränderbaren {@link GameServer Informationssatz}, der die Daten der Ressource innerhalb
     * von Kubernetes enthält. In den Daten sind unter anderem die Meta-Informationen, der aktuelle Status, die
     * Zuweisung sowie die Konfiguration enthalten. Die Rückgabe entspricht garantiert immer dem, was durch diese SDK
     * gesetzt wurde, auch wenn der Wert noch nicht in der Kubernetes Ressource aktualisiert wurde.
     *
     * @return Ein neuer, unveränderbarer {@link GameServer Informationssatz}, der die Daten der Ressource innerhalb von
     *     Kubernetes enthält.
     *
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#gameserver">Agones Dokumentation</a>
     */
    @NotNull
    @Contract(value = " -> new", pure = true)
    GameServer gameServer();

    /**
     * Registriert einen neuen {@link Consumer Callback} für die Verarbeitung von Änderung an der Ressource innerhalb
     * von Kubernetes. Der Callback erhält also immer dann ein neues Element, wenn es eine Änderung an der Ressource
     * gab. Es werden sowohl die Änderungen weitergeleitet, die direkt durch dieses SDK ausgelöst wurden, als auch
     * externe Änderungen, die direkt an der Ressource vorgenommen wurden.
     *
     * <p>Das erste Element wird direkt nach der Registrierung weitergeleitet und entspricht dem aktuellen Zustand der
     * Ressource (ohne das eine Änderung erfolgt ist). Der {@link Consumer Callback} wird asynchron ausgelöst und er
     * bleibt so lange registriert, wie es diese Instanz gibt. Es ist also nicht möglich den Stream zu deaktivieren.
     *
     * @param callback Der {@link Consumer Callback}, der die fortlaufenden Änderungen an der Ressource innerhalb von
     *                 Kubernetes verarbeitet.
     *
     * @throws NullPointerException Falls für den {@link Consumer Callback} {@code null} übergeben wird. Da die ganze
     *                              Funktionsweise dieser Methode auf dem Callback basiert, ist ein Aufruf ohne Callback
     *                              nicht im Sinne dieser Methode.
     * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#watchgameserverfunctiongameserver">Agones
     *     Dokumentation</a>
     */
    void watchGameServer(@NotNull Consumer<@NotNull GameServer> callback);
    //</editor-fold>

    //<editor-fold desc="maintenance">

    /**
     * Startet den Agones Health Task, der periodisch {@link #health() Health-Pings} an das {@link AgonesSdk Agones SDK}
     * sendet und diese Instanz damit valide hält. Dieser Task sollte so früh wie möglich gestartet werden und er läuft
     * bis diese Instanz heruntergefahren wird. Das Melden der {@link #health() Health-Pings} bewirken keine
     * Zustandsveränderung wie beispielweise {@link #ready()}. Falls mehr Kontrolle über die {@link #health()
     * Health-Pings} gewünscht wird, können sie stattdessen auch regelmäßig manuell ausgelöst und verwaltet werden.
     *
     * @throws IllegalStateException Falls der Task für die regelmäßigen {@link #health() Health-Pings} innerhalb dieses
     *                               {@link AgonesSdk Agones SDKs} bereits aktiviert wurde und daher nicht erneut
     *                               aktiviert werden kann.
     */
    void startHealthTask();
    //</editor-fold>

    //<editor-fold desc="release channels">

    /**
     * Gibt den gekapselten {@link Alpha Alpha-Channel} des {@link AgonesSdk Agones SDKs} zurück. Durch diesen
     * Sub-Channel können die weniger getesteten und isolierten Schnittstellen verwendet werden. Das Beziehen des
     * Channels löst noch keine Kommunikation mit Agones aus. Die Schnittstellen des {@link Alpha Alpha-Channels} wurden
     * noch nicht großflächig getestet und sind standardmäßig deaktiviert. Die Endpunkte können frei genutzt werden, es
     * können dabei jedoch noch einige Bugs auftreten.
     *
     * @return Der gekapselte {@link Alpha Alpha-Channel} des {@link AgonesSdk Agones SDKs}, über den die
     *     Alpha-Schnittstellen verwendet werden können.
     */
    @NotNull
    @Contract(pure = true)
    Alpha alpha();

    /**
     * Gibt den gekapselten {@link Beta Beta-Channel} des {@link AgonesSdk Agones SDKs} zurück. Durch diesen Sub-Channel
     * können die weniger getesteten und isolierten Schnittstellen verwendet werden. Das Beziehen des Channels löst noch
     * keine Kommunikation mit Agones aus. Die Schnittstellen des {@link Beta Beta-Channels} wurden bereits ausführlich
     * getestet und sind standardmäßig aktiviert. Die Endpunkte können frei genutzt werden und es sind insgesamt wenig
     * Bugs zu erwarten.
     *
     * @return Der gekapselte {@link Beta Beta-Channel} des {@link AgonesSdk Agones SDKs}, über den die
     *     Beta-Schnittstellen verwendet werden können.
     */
    @NotNull
    @Contract(pure = true)
    Beta beta();
    //</editor-fold>


    /**
     * Der {@link Alpha Alpha-Channel} stellt Schnittstellen des {@link AgonesSdk Agones SDK} bereit, die bislang noch
     * nicht großflächig getestet wurde und deren Methoden sich von einem Update zum nächsten noch drastisch verändern
     * oder wegfallen könnten. Die genutzten Endpoints können aber dennoch frei genutzt werden aber es ist möglich, dass
     * noch einige Bugs auftreten können. Sobald die Features mehr getestet wurden, steigen sie in den {@link Beta
     * Beta-Channel} auf. Der {@link Alpha Alpha-Channel} kann über die Schnittstelle {@link #alpha()} von dem {@link
     * AgonesSdk Agones SDK} bezogen werden.
     */
    interface Alpha {

        //<editor-fold desc="player tracking: lifecycle">

        /**
         * Meldet einen Spieler mit einer bestimmten {@link UUID einzigartigen ID} für die Spielerliste dieser Instanz
         * an. Dabei wird die aktive Spielerzahl erhört und die {@link UUID einzigartige ID} des Spielers wird
         * hinzugefügt. Der Spieler ist anschließend garantiert Teil der Spielerliste dieser Instanz, falls sie zuvor
         * nicht schon voll war. Die Rückgabe entspricht garantiert immer dem, was durch diese SDK gesetzt wurde, auch
         * wenn der Wert noch nicht in der Kubernetes Ressource aktualisiert wurde.
         *
         * @param playerId Die {@link UUID einzigartige ID} des Spielers, der als aktueller Spieler dieser Instanz
         *                 hinzugefügt werden soll.
         *
         * @return Ob dieser Spieler zuvor noch nicht in Spielerliste dieser Instanz war und daher erfolgreich
         *     hinzugefügt werden konnte und so von nun an in der {@link #connectedPlayers() Spielerliste} und {@link
         *     #playerCount() Spielerzahl} berücksichtigt wird.
         *
         * @throws NullPointerException  Falls für die {@link UUID einzigartige ID} {@code null} übergeben wird und
         *                               entsprechend kein Spieler davon abgeleitet werden kann.
         * @throws IllegalStateException Falls die {@link #playerCapacity() Spieler-Kapazität} dieser Instanz bereits
         *                               erreicht wurde und der Spieler daher nicht hinzugefügt werden kann, bis andere
         *                               Spieler die Instanz verlassen oder die Kapazität erhöht wird.
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaplayerconnectplayerid">Agones
         *     Dokumentation</a>
         */
        boolean playerConnect(@NotNull UUID playerId);

        /**
         * Meldet einen Spieler mit einer bestimmten {@link UUID einzigartigen ID} von der Spielerliste dieser Instanz
         * ab. Dabei wird die aktive Spielerzahl verringert und die {@link UUID einzigartige ID} des Spielers wird
         * entfernt. Der Spieler ist anschließend garantiert nicht mehr Teil der Spielerliste dieser Instanz. Die
         * Rückgabe entspricht garantiert immer dem, was durch diese SDK gesetzt wurde, auch wenn der Wert noch nicht in
         * der Kubernetes Ressource aktualisiert wurde.
         *
         * @param playerId Die {@link UUID einzigartige ID} des Spielers, der als aktueller Spieler dieser Instanz
         *                 entfernt werden soll.
         *
         * @return Ob dieser Spieler zuvor in der Spielerliste dieser Instanz war und daher erfolgreich entfernt werden
         *     konnte und so von nun an nicht mehr in der {@link #connectedPlayers() Spielerliste} und {@link
         *     #playerCount() Spielerzahl} berücksichtigt wird.
         *
         * @throws NullPointerException Falls für die {@link UUID einzigartige ID} {@code null} übergeben wird und
         *                              entsprechend kein Spieler davon abgeleitet werden kann.
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaplayerdisconnectplayerid">Agones
         *     Dokumentation</a>
         */
        boolean playerDisconnect(@NotNull UUID playerId);
        //</editor-fold>

        //<editor-fold desc="player tracking: registry">

        /**
         * Ermittelt die {@link UUID einzigartigen IDs} der aktuellen Spieler, die dieser Instanz zugeordnet werden. Es
         * werden nur die Spieler zurückgegeben, die auch tatsächlich an dem Spiel teilnehmen. Zuschauer werden also
         * ignoriert. Die Rückgabe entspricht garantiert immer dem, was durch diese SDK gesetzt wurde, auch wenn der
         * Wert noch nicht in der Kubernetes Ressource aktualisiert wurde.
         *
         * @return Eine neue, unveränderbare {@link List Liste} der {@link UUID einzigartigen IDs} der Spieler, die
         *     aktuell dieser Instanz zugeordnet sind.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetconnectedplayers">Agones
         *     Dokumentation</a>
         */
        @NotNull
        @Unmodifiable
        @Contract(value = " -> new", pure = true)
        List<@NotNull UUID> connectedPlayers();

        /**
         * Ermittelt, ob sich ein Spieler mit einer bestimmten {@link UUID einzigartigen ID} aktuell in der Spielerliste
         * dieser Instanz befindet. Es befinden sich nur die echten Spieler in dieser Liste, Zuschauer werden
         * ausgeschlossen. Die Rückgabe entspricht garantiert immer dem, was durch diese SDK gesetzt wurde, auch wenn
         * der Wert noch nicht in der Kubernetes Ressource aktualisiert wurde.
         *
         * @param playerId Die {@link UUID einzigartige ID} des Spielers, für den geprüft werden soll, ob er sich
         *                 aktuell in der Spielerliste dieser Instanz befindet.
         *
         * @return Ob der übergebene Spieler sich aktuell in der Spielerliste dieser Instanz befindet.
         *
         * @throws NullPointerException Falls für die {@link UUID einzigartige ID} {@code null} übergeben wird und
         *                              entsprechend kein Spieler davon abgeleitet werden kann.
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphaisplayerconnectedplayerid">Agones
         *     Dokumentation</a>
         */
        @Contract(pure = true)
        boolean isPlayerConnected(@NotNull UUID playerId);
        //</editor-fold>

        //<editor-fold desc="player tracking: stats">

        /**
         * Ermittelt die aktuelle Anzahl der sich gleichzeitig auf dieser Instanz befindlichen Spieler. Es werden nur
         * die tatsächlich an dem Spiel teilnehmenden Spieler gezählt und keine Beobachter. Die Rückgabe entspricht
         * garantiert immer dem, was durch diese SDK gesetzt wurde, auch wenn der Wert noch nicht in der Kubernetes
         * Ressource aktualisiert wurde.
         *
         * @return Die aktuelle Anzahl der sich gleichzeitig auf dieser Instanz befindlichen Spieler.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetplayercount">Agones Dokumentation</a>
         */
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        long playerCount();

        /**
         * Ermittelt die aktuell geltende Kapazität für die Anzahl gleichzeitiger Spieler auf dieser Instanz. Über diese
         * Kapazität hinaus können keine Spieler für diese Instanz eingetragen werden. Die Rückgabe entspricht
         * garantiert immer dem, was durch dieses SDK gesetzt wurde, auch wenn der Wert noch nicht in der Kubernetes
         * Ressource aktualisiert wurde.
         *
         * @return Die aktuelle Kapazität, die für die gleichzeitigen Spieler auf dieser Instanz gilt. Der Wert {@code
         *     0} steht für unbegrenzt viele Spieler.
         *
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphagetplayercapacity">Agones
         *     Dokumentation</a>
         */
        @Contract(pure = true)
        @Range(from = 0, to = Long.MAX_VALUE)
        long playerCapacity();

        /**
         * Setzt eine neue Kapazität für die Anzahl gleichzeitiger Spieler auf dieser Instanz. Das Limit wird nicht
         * rückwirkend erzwungen. Wenn sich also aktuell mehr Spieler auf dieser Instanz befinden, als die neue
         * Kapazität zulässt, werden zwar keine neuen Spieler auf diese Instanz gelassen, bestehende Spieler werden aber
         * nicht von der Instanz entfernt.
         *
         * @param capacity Die neue Kapazität, die für die gleichzeitigen Spieler auf dieser Instanz gelten soll. Der
         *                 Wert {@code 0} hebt die Beschränkung vollständig auf und steht für unbegrenzt viele Spieler.
         *
         * @throws IllegalArgumentException Falls für die Spieler-Kapazität eine negative Zahl angegeben wird.
         * @see <a href="https://agones.dev/site/docs/guides/client-sdks/#alphasetplayercapacitycount">Agones
         *     Dokumentation</a>
         */
        void playerCapacity(@Range(from = 0, to = Long.MAX_VALUE) long capacity);
        //</editor-fold>
    }

    /**
     * Der {@link Beta Beta-Channel} stellt Schnittstellen des {@link AgonesSdk Agones SDK} bereit, die zwar schon
     * ausführlich getestet und standardmäßig aktiviert wurden, aber noch nicht derart breit genutzt wurden, wie es bei
     * den Features des Stable-Channels der Fall ist. Die genutzten Endpoints können aber dennoch frei genutzt werden
     * und es sind insgesamt wenig Bugs zu erwarten. Sobald die Features mehr getestet wurden, steigen sie in den
     * Stable-Channel auf. Der {@link Beta Beta-Channel} kann über die Schnittstelle {@link #beta()} von dem {@link
     * AgonesSdk Agones SDK} bezogen werden.
     */
    interface Beta {
        // currently, there are no beta sdk features
    }
}
