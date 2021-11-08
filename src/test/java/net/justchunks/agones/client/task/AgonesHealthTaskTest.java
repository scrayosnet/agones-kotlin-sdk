package net.justchunks.agones.client.task;

import net.justchunks.agones.client.AgonesSdk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgonesHealthTaskTest {

    @Test
    @DisplayName("Should run health endpoint")
    void shouldRunHealthEndpoint() {
        // given
        AgonesSdk sdk = mock(AgonesSdk.class);
        AgonesHealthTask healthTask = new AgonesHealthTask(sdk);

        // when
        healthTask.run();

        // then
        verify(sdk, times(1)).health();
    }
}
