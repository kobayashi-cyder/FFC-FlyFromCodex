package com.ffc.banc888.fly;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

final class NativeSession {
    private final SecureRandom random = new SecureRandom();
    private volatile String token = "";
    private volatile long epoch = 0L;

    NativeSession() {
        rotate();
    }

    synchronized String rotate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        token = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        epoch++;
        return token;
    }

    String token() {
        return token;
    }

    long epoch() {
        return epoch;
    }

    boolean valid(String supplied) {
        if (supplied == null || supplied.isEmpty()) return false;
        byte[] a = token.getBytes(StandardCharsets.UTF_8);
        byte[] b = supplied.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
