package com.bear27570.ftc.scouting.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    // 16 字节的 AES 密钥（企业级应用通常通过环境变量注入，这里为了方便你本地测试写死）
    private static final String ALGO = "AES";
    private static final byte[] KEY = "Bear27570_Secure".getBytes(StandardCharsets.UTF_8);

    public static String encrypt(String data) {
        if (data == null || data.isEmpty()) return data;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, ALGO));
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) return encryptedData;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGO));
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容老版本的明文数据：如果解密失败（不是 Base64 或不是 AES），原样返回
            return encryptedData;
        }
    }

    // 💡 架构师留给你的后门：
    // 想在 IDEA 数据库面板里手动改密码？运行这个 main 方法，把生成的密文复制进去即可。
    public static void main(String[] args) {
        String targetPassword = "newPassword123";
        System.out.println("你的明文: " + targetPassword);
        System.out.println("请将以下密文复制到 IDEA 的 users 表中:");
        System.out.println(encrypt(targetPassword));
    }
}