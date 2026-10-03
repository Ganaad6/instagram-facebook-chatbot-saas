package com.chatbot.saas.util;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import java.security.MessageDigest;

public class SignatureValidator {

    public static boolean validateSignature(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            return false;
        }
        try {
            String computed = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(payload);
            return MessageDigest.isEqual(computed.getBytes(), signature.getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}
