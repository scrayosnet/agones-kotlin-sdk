package net.justchunks.agones.client.observer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CallbackStreamObserverTest {

    @Test
    @DisplayName("Should get a valid singleton instance")
    void shouldGetInstance() {
        // given
        CallbackStreamObserver<Void> observer = CallbackStreamObserver.getInstance(
            ignored -> {
            }
        );

        // then
        Assertions.assertNotNull(observer);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should trigger the callback for next element")
    void shouldTriggerCallback() {
        // given
        Consumer<String> consumer = (Consumer<String>) mock(Consumer.class);
        CallbackStreamObserver<String> observer = CallbackStreamObserver.getInstance(consumer);

        // when
        observer.onNext("test");

        // then
        verify(consumer, Mockito.times(1)).accept("test");
    }
}
