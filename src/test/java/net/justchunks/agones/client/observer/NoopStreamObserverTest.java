package net.justchunks.agones.client.observer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoopStreamObserverTest {

    @Test
    @DisplayName("Should log waring during transmission")
    void shouldLogWarning() {
        // given
        Appender appender = mock(Appender.class);
        when(appender.getName()).thenReturn("TestAppender");
        when(appender.isStarted()).thenReturn(true);
        Logger noopLogger = (Logger) LogManager.getLogger(NoopStreamObserver.class);
        noopLogger.addAppender(appender);
        noopLogger.setLevel(Level.ALL);
        NoopStreamObserver<Void> observer = NoopStreamObserver.getInstance();
        Throwable throwable = new IllegalStateException();

        // when
        observer.onError(throwable);

        // then
        verify(appender, times(1)).append(any());
    }

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
