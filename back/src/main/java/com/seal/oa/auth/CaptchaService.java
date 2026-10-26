package com.seal.oa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaValue> captchaStore = new ConcurrentHashMap<>();
    private final boolean includeCode;
    private final long ttlSeconds;

    public CaptchaService(
            @Value("${app.captcha.include-code:true}") boolean includeCode,
            @Value("${app.captcha.ttl-seconds:120}") long ttlSeconds
    ) {
        this.includeCode = includeCode;
        this.ttlSeconds = ttlSeconds;
    }

    public Map<String, Object> create() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode(4);
        captchaStore.put(uuid, new CaptchaValue(code, Instant.now().plusSeconds(ttlSeconds)));
        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("uuid", uuid);
        result.put("img", render(code));
        if (includeCode) {
            result.put("debugCode", code);
        }
        return result;
    }

    public boolean validate(String uuid, String submittedCode) {
        CaptchaValue value = captchaStore.remove(uuid);
        return value != null
                && value.expiresAt().isAfter(Instant.now())
                && value.code().equalsIgnoreCase(submittedCode == null ? "" : submittedCode.trim());
    }

    private String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    private String render(String code) {
        int width = 160;
        int height = 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(246, 249, 252));
        graphics.fillRect(0, 0, width, height);

        graphics.setStroke(new BasicStroke(2));
        for (int line = 0; line < 4; line++) {
            graphics.setColor(new Color(90 + random.nextInt(100), 130 + random.nextInt(90), 180 + random.nextInt(60)));
            graphics.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        for (int index = 0; index < code.length(); index++) {
            int x = 18 + index * 34 + random.nextInt(5);
            int y = 43 + random.nextInt(7);
            graphics.setColor(new Color(20 + random.nextInt(80), 50 + random.nextInt(80), 100 + random.nextInt(100)));
            graphics.drawString(String.valueOf(code.charAt(index)), x, y);
        }
        graphics.dispose();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate captcha image", exception);
        }
    }

    private record CaptchaValue(String code, Instant expiresAt) {
    }
}

