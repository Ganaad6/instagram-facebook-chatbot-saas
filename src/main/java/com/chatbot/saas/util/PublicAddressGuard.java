package com.chatbot.saas.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Keeps shop-supplied URLs (the notification webhook) pointed at the public internet. Without
 * it, a shop could make the server POST to itself, the database, the cloud metadata service
 * (169.254.169.254) or anything else on the private network.
 */
public final class PublicAddressGuard {

    private PublicAddressGuard() {
    }

    /** Problem with a URL a shop wants notifications sent to, or null if it's acceptable. */
    public static String problemWith(String url, boolean allowHttp) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return "Буруу URL байна";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
            return "URL https:// -ээр эхлэх ёстой";
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            return "Буруу URL байна";
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!isPublic(address)) {
                    return "Дотоод сүлжээний хаяг руу мэдэгдэл илгээх боломжгүй";
                }
            }
        } catch (UnknownHostException e) {
            return "Хост олдсонгүй: " + uri.getHost();
        }
        return null;
    }

    /** False for loopback, private, link-local, CGNAT, multicast, unspecified and similar. */
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xFF, second = b[1] & 0xFF;
            return !(first == 0                                   // "this" network
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || (first == 192 && second == 0 && (b[2] & 0xFF) == 0) // IETF protocol assignments
                    || (first == 198 && (second == 18 || second == 19))    // benchmarking
                    || first >= 240);                                   // reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            if ((b[0] & 0xFE) == 0xFC) {
                return false; // unique local fc00::/7
            }
            boolean ipv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                ipv4Mapped &= b[i] == 0;
            }
            if (ipv4Mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
