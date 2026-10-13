package com.sumirelabs.kita.app;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GatewayPolicyTest {
    @Test void privilegedMemberIntentIsOnlyRequestedWhenTheApplicationAllowsIt() {
        assertFalse(GatewayPolicy.members(0)); assertFalse(GatewayPolicy.members(1 << 18));
        assertTrue(GatewayPolicy.members(1 << 14)); assertTrue(GatewayPolicy.members(1 << 15));
        assertNotNull(GatewayPolicy.store());
    }
}