package net.justchunks.agones.client.observer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoopStreamOberserverTest {

    @Test
    void shouldGetInstance() {
        Assertions.assertNotNull(NoopStreamOberserver.getInstance());
    }
}
