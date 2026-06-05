package myau.auth;

import myau.event.EventTarget;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuthManager {
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    private static final AuthManager INSTANCE = new AuthManager();

    private final Minecraft mc = Minecraft.getMinecraft();
    private final HwidProvider hwidProvider = new HwidProvider();
    private final AuthSessionStore sessionStore = new AuthSessionStore();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Auth-IO");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Auth-Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean validatingSession = new AtomicBoolean(false);
    private final AtomicBoolean firstTickDone = new AtomicBoolean(false);

    private volatile AuthSession session;
    private volatile AuthHttpClient authHttpClient;
    private volatile boolean authenticated;
    private volatile boolean inWorld;
    private volatile ScheduledFuture<?> heartbeatTask;

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    public void initializeIfNeeded() {
        if (!initialized.compareAndSet(false, true)) {
            ensureHttpClientUpToDate();
            return;
        }

        ensureHttpClientUpToDate();
        AuthSession loaded = sessionStore.load();
        if (loaded == null) {
            loaded = new AuthSession();
        }
        if (StringUtils.isBlank(loaded.getHwidRaw())) {
            loaded.setHwidRaw(hwidProvider.getHwidRaw());
        }
        session = loaded;

        if (loaded.hasToken()) {
            validateSessionAsync();
        } else {
            authenticated = false;
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean hasSavedToken() {
        AuthSession current = session;
        return current != null && current.hasToken();
    }

    public String getCurrentUsername() {
        AuthSession current = session;
        return current == null ? null : current.getUsername();
    }

    public String getHwidRaw() {
        initializeIfNeeded();
        AuthSession current = session;
        if (current != null && StringUtils.isNotBlank(current.getHwidRaw())) {
            return current.getHwidRaw();
        }
        return hwidProvider.getHwidRaw();
    }

    public AuthSession login(String usernameInput) throws AuthError, java.io.IOException {
        initializeIfNeeded();
        ensureHttpClientUpToDate();
        String username = normalizeAndValidateUsername(usernameInput);
        String hwidRaw = getHwidRaw();
        String token = authHttpClient.login(username, hwidRaw);
        return buildAndStoreSession(username, token, hwidRaw);
    }

    public AuthSession register(String usernameInput, String licenseKey) throws AuthError, java.io.IOException {
        initializeIfNeeded();
        ensureHttpClientUpToDate();
        String username = normalizeAndValidateUsername(usernameInput);
        if (StringUtils.isBlank(licenseKey)) {
            throw new AuthError("INVALID_LICENSE_KEY", "License key is required", null, 0);
        }
        String hwidRaw = getHwidRaw();
        String token = authHttpClient.register(username, hwidRaw, licenseKey.trim());
        return buildAndStoreSession(username, token, hwidRaw);
    }

    public void logout() {
        AuthSession current = session;
        if (current == null) {
            current = new AuthSession();
        }
        current.setAccessToken(null);
        current.setUsername(null);
        current.setHwidRaw(getHwidRaw());
        session = current;
        sessionStore.save(current);
        authenticated = false;
        stopRealtimeServices();
    }

    public void validateSessionAsync() {
        initializeIfNeeded();
        AuthSession current = session;
        if (current == null || !current.hasToken() || !validatingSession.compareAndSet(false, true)) {
            return;
        }

        ioExecutor.execute(() -> {
            try {
                ensureHttpClientUpToDate();
                Map<String, Object> me = authHttpClient.getMe(current);
                String username = stringValue(me.get("username"), current.getUsername());
                current.setUsername(username);
                current.setHwidRaw(getHwidRaw());
                sessionStore.save(current);
                session = current;
                authenticated = true;
                if (inWorld) {
                    startRealtimeServices();
                }
            } catch (AuthError e) {
                handleAuthError(e);
            } catch (Exception e) {
                System.err.println("[MyauAP] Session validation failed: " + e.getMessage());
            } finally {
                validatingSession.set(false);
            }
        });
    }

    public String getStatusLine() {
        String user = getCurrentUsername();
        return "auth=" + authenticated + ", user=" + (user == null ? "none" : user);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!firstTickDone.compareAndSet(false, true)) {
            initializeIfNeeded();
            if (!authenticated && !hasSavedToken()) {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (mc.currentScreen == null) {
                            mc.displayGuiScreen(new myau.auth.gui.GuiAuthLogin());
                        }
                    }
                });
            }
            // Fall through to check world state
        }

        // Detect world state changes (LoadWorldEvent fires at HEAD where mc.theWorld is stale)
        boolean currentlyInWorld = mc.theWorld != null && mc.thePlayer != null;
        if (currentlyInWorld != inWorld) {
            inWorld = currentlyInWorld;
            if (currentlyInWorld) {
                if (authenticated) {
                    startRealtimeServices();
                }
            } else {
                stopRealtimeServices();
            }
        }
    }

    private synchronized void startRealtimeServices() {
        if (!authenticated || session == null || !session.hasToken()) {
            return;
        }
        ensureHttpClientUpToDate();
        startHeartbeatTask();
    }

    private synchronized void stopRealtimeServices() {
        ScheduledFuture<?> heartbeat = heartbeatTask;
        heartbeatTask = null;
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
    }

    private void startHeartbeatTask() {
        ScheduledFuture<?> heartbeat = heartbeatTask;
        if (heartbeat != null && !heartbeat.isCancelled()) {
            return;
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        ioExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                sendPresenceHeartbeatSafe();
                            }
                        });
                    }
                },
                1L,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void sendPresenceHeartbeatSafe() {
        try {
            if (!authenticated || !inWorld || session == null || mc.thePlayer == null) {
                return;
            }
            String gameName = mc.thePlayer.getName();
            String username = session.getUsername();
            if (StringUtils.isBlank(gameName) || StringUtils.isBlank(username)) {
                return;
            }
            authHttpClient.sendPresenceHeartbeat(session, gameName, username);
        } catch (AuthError e) {
            handleAuthError(e);
        } catch (Exception e) {
            // silent
        }
    }

    private AuthSession buildAndStoreSession(String fallbackUsername, String accessToken, String hwidRaw) throws java.io.IOException, AuthError {
        AuthSession newSession = new AuthSession();
        newSession.setAccessToken(accessToken);
        newSession.setHwidRaw(hwidRaw);

        Map<String, Object> me = authHttpClient.getMe(newSession);
        String username = stringValue(me.get("username"), fallbackUsername);
        newSession.setUsername(username);

        session = newSession;
        sessionStore.save(newSession);
        authenticated = true;

        if (inWorld) {
            startRealtimeServices();
        }
        enqueueChat("§aAuth success as §f" + username);
        return newSession;
    }

    private void handleAuthError(AuthError error) {
        if (error == null) {
            return;
        }
        if (StringUtils.isNotBlank(error.getRequestId())) {
            System.err.println("[MyauAP] Auth error " + error.getCode() + " requestId=" + error.getRequestId() + " message=" + error.getMessage());
        } else {
            System.err.println("[MyauAP] Auth error " + error.getCode() + " message=" + error.getMessage());
        }

        int status = error.getStatus();
        if (status == 401 || status == 403 || "CLIENT_NOT_AUTHENTICATED".equals(error.getCode())) {
            AuthSession current = session;
            if (current == null) {
                current = new AuthSession();
            }
            current.setAccessToken(null);
            current.setUsername(null);
            current.setHwidRaw(getHwidRaw());
            session = current;
            sessionStore.save(current);
            authenticated = false;
            stopRealtimeServices();
            enqueueChat("§cAuth session expired. Please login again.");
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (mc.currentScreen == null) {
                        mc.displayGuiScreen(new myau.auth.gui.GuiAuthLogin());
                    }
                }
            });
        }
    }

    private void enqueueChat(final String message) {
        try {
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(message));
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void ensureHttpClientUpToDate() {
        if (authHttpClient == null) {
            authHttpClient = new AuthHttpClient();
        }
    }

    private static String normalizeAndValidateUsername(String usernameInput) throws AuthError {
        if (usernameInput == null) {
            throw new AuthError("INVALID_USERNAME", "Username cannot be empty", null, 0);
        }
        String normalized = usernameInput.trim().toLowerCase();
        if (!normalized.matches("^[a-z0-9_]{3,16}$")) {
            throw new AuthError("INVALID_USERNAME", "Username must match ^[a-z0-9_]{3,16}$", null, 0);
        }
        return normalized;
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String str = String.valueOf(value);
        return str.trim().isEmpty() ? fallback : str;
    }
}
