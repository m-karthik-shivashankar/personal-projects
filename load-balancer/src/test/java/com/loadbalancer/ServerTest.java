package com.loadbalancer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {

    @Test
    void constructor_setsFieldsCorrectly() {
        Server s = new Server("s1", "192.168.1.1", 8080, 3);

        assertEquals("s1", s.getId());
        assertEquals("192.168.1.1", s.getHost());
        assertEquals(8080, s.getPort());
        assertEquals(3, s.getWeight());
        assertTrue(s.isHealthy(), "Server should start healthy");
    }

    @Test
    void markUnhealthy_setsHealthyFalse() {
        Server s = new Server("s1", "localhost", 80, 1);
        s.markUnhealthy();

        assertFalse(s.isHealthy());
    }

    @Test
    void markHealthy_restoresHealthyState() {
        Server s = new Server("s1", "localhost", 80, 1);
        s.markUnhealthy();
        s.markHealthy();

        assertTrue(s.isHealthy());
    }

    @Test
    void constructor_rejectsNullId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server(null, "localhost", 80, 1));
    }

    @Test
    void constructor_rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server("  ", "localhost", 80, 1));
    }

    @Test
    void constructor_rejectsNullHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server("s1", null, 80, 1));
    }

    @Test
    void constructor_rejectsPortOutOfRange_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server("s1", "localhost", 0, 1));
    }

    @Test
    void constructor_rejectsPortOutOfRange_tooHigh() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server("s1", "localhost", 65536, 1));
    }

    @Test
    void constructor_acceptsBoundaryPorts() {
        assertDoesNotThrow(() -> new Server("a", "localhost", 1, 1));
        assertDoesNotThrow(() -> new Server("b", "localhost", 65535, 1));
    }

    @Test
    void constructor_rejectsNonPositiveWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new Server("s1", "localhost", 80, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Server("s1", "localhost", 80, -5));
    }

    @Test
    void equals_basedOnIdOnly() {
        Server a = new Server("same", "host-a", 80, 1);
        Server b = new Server("same", "host-b", 8080, 5);
        Server c = new Server("different", "host-a", 80, 1);

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Server a = new Server("same", "host-a", 80, 1);
        Server b = new Server("same", "host-b", 9090, 2);

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_containsKeyFields() {
        Server s = new Server("s1", "localhost", 8080, 2);
        String str = s.toString();

        assertTrue(str.contains("s1"));
        assertTrue(str.contains("localhost"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("2"));
    }
}
