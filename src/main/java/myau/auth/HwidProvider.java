package myau.auth;

import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HwidProvider {
    private static final String DEVICE_ID_FILE = "device-id.txt";
    private volatile String cachedHwid;

    public String getHwidRaw() {
        String current = cachedHwid;
        if (StringUtils.isNotBlank(current)) {
            return current;
        }
        synchronized (this) {
            if (StringUtils.isNotBlank(cachedHwid)) {
                return cachedHwid;
            }
            cachedHwid = computeHwid();
            return cachedHwid;
        }
    }

    private String computeHwid() {
        List<String> components = new ArrayList<>();
        addIfPresent(components, System.getenv("PROCESSOR_IDENTIFIER"));
        addIfPresent(components, System.getenv("COMPUTERNAME"));
        addIfPresent(components, System.getenv("PROCESSOR_ARCHITECTURE"));
        addIfPresent(components, System.getProperty("os.name"));
        addIfPresent(components, System.getProperty("os.arch"));
        addIfPresent(components, System.getProperty("os.version"));

        if (components.isEmpty()) {
            components.add("unknown");
        }

        addIfPresent(components, getOrCreatePersistentDeviceId());
        String raw = join(components, "|").toLowerCase(Locale.ROOT);
        return sha256(raw);
    }

    private String getOrCreatePersistentDeviceId() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, "config/Myau/" + DEVICE_ID_FILE);
        try {
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            if (file.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                String existing = sb.toString().trim();
                if (StringUtils.isNotBlank(existing)) {
                    return existing;
                }
            }
            String created = UUID.randomUUID().toString();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(created);
            }
            return created;
        } catch (IOException e) {
            System.err.println("[MyauAP] Failed to read device id file: " + e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    private static void addIfPresent(List<String> list, String value) {
        if (StringUtils.isNotBlank(value)) {
            list.add(value.trim());
        }
    }

    private static String join(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
