package com.railreserve.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void healthEndpointReportsServiceAndStatus() {
        var response = new HealthController().health();
        assertEquals("auth-service", response.get("service"));
        assertEquals("UP", response.get("status"));
    }
}
