package com.chatbot.saas.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class PublicAddressGuardTest {

    private static boolean pub(String ip) throws Exception {
        return PublicAddressGuard.isPublic(InetAddress.getByName(ip));
    }

    @Test
    void privateAndSpecialAddressesAreRejected() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "::1", "fc00::1", "fd12:3456::1",
                "fe80::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1"}) {
            assertFalse(pub(ip), ip);
        }
    }

    @Test
    void publicAddressesAreAllowed() throws Exception {
        for (String ip : new String[]{"8.8.8.8", "93.184.215.14", "2606:4700:4700::1111", "::ffff:8.8.8.8"}) {
            assertTrue(pub(ip), ip);
        }
    }

    @Test
    void urlsMustBeHttpsAndPublic() {
        assertNull(PublicAddressGuard.problemWith("https://93.184.215.14/hooks/orders", false));
        assertNotNull(PublicAddressGuard.problemWith("http://93.184.215.14/hook", false), "plain http");
        assertNull(PublicAddressGuard.problemWith("http://93.184.215.14/hook", true), "http allowed in dev");
        assertNotNull(PublicAddressGuard.problemWith("https://localhost/hook", false));
        assertNotNull(PublicAddressGuard.problemWith("https://169.254.169.254/latest/meta-data", false));
        assertNotNull(PublicAddressGuard.problemWith("https://user:pass@93.184.215.14/", false));
        assertNotNull(PublicAddressGuard.problemWith("ftp://93.184.215.14/", false));
        assertNotNull(PublicAddressGuard.problemWith("not a url", false));
    }
}
