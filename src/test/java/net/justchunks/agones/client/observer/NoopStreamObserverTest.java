package net.justchunks.agones.client.observer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoopStreamObserverTest {

    @Test
    @DisplayName("Should get a valid singleton instance")
    void shouldGetInstance() {
        // given
        NoopStreamObserver<Void> observer = NoopStreamObserver.getInstance();

        // then
        Assertions.assertNotNull(observer);
    }

    @Test
    @DisplayName("Instance should be a singleton")
    void instanceShouldBeSingleton() {
        // given
        NoopStreamObserver<Void> observer1 = NoopStreamObserver.getInstance();
        NoopStreamObserver<Void> observer2 = NoopStreamObserver.getInstance();

        // then
        Assertions.assertEquals(observer1, observer2);
    }
}
