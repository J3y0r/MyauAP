package myau.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AuthSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "auth_session.json";

    private File sessionFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/Myau/" + FILE_NAME);
    }

    public synchronized AuthSession load() {
        try {
            File file = sessionFile();
            if (!file.exists()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String json = sb.toString().trim();
            if (StringUtils.isBlank(json)) {
                return null;
            }
            return GSON.fromJson(json, AuthSession.class);
        } catch (Exception e) {
            System.err.println("[MyauAP] Failed to load auth session: " + e.getMessage());
            return null;
        }
    }

    public synchronized void save(AuthSession session) {
        if (session == null) {
            return;
        }
        try {
            File file = sessionFile();
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(session, writer);
            }
        } catch (IOException e) {
            System.err.println("[MyauAP] Failed to save auth session: " + e.getMessage());
        }
    }

    public synchronized void clear() {
        try {
            File file = sessionFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            System.err.println("[MyauAP] Failed to clear auth session: " + e.getMessage());
        }
    }
}
