package com.chris64233.cc.courtcalendar.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 幂等内容指纹：对规范化后的请求体计算 SHA-256。
 */
@Component
public class RequestHasher {

    public String sha256(String canonicalContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论上不会缺失
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
