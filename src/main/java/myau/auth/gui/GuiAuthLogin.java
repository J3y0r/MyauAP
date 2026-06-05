package myau.auth.gui;

import myau.auth.AuthError;
import myau.auth.AuthManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuiAuthLogin extends GuiScreen {
    private final GuiScreen previousScreen;

    private GuiTextField usernameField;
    private GuiTextField licenseKeyField;
    private GuiButton loginButton;
    private GuiButton registerButton;

    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;
    private boolean processing = false;
    private CompletableFuture<Void> pendingTask;
    private ExecutorService executor;

    public GuiAuthLogin() {
        this(null);
    }

    public GuiAuthLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int centerX = width / 2;
        int centerY = height / 2;

        usernameField = new GuiTextField(0, fontRendererObj, centerX - 100, centerY - 40, 200, 20);
        usernameField.setMaxStringLength(16);
        usernameField.setFocused(true);

        licenseKeyField = new GuiTextField(1, fontRendererObj, centerX - 100, centerY - 10, 200, 20);
        licenseKeyField.setMaxStringLength(64);

        loginButton = new GuiButton(2, centerX - 100, centerY + 20, 95, 20, "Login");
        registerButton = new GuiButton(3, centerX + 5, centerY + 20, 95, 20, "Register");

        buttonList.add(loginButton);
        buttonList.add(registerButton);

        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }

        if (button.id == 2) {
            doLogin();
        } else if (button.id == 3) {
            doRegister();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int centerX = width / 2;
        drawCenteredString(fontRendererObj, "§a§lMyauAP §7- Authentication", centerX, height / 2 - 70, 0xFFFFFF);

        usernameField.drawTextBox();
        licenseKeyField.drawTextBox();

        if (usernameField.getText().isEmpty() && !usernameField.isFocused()) {
            drawString(fontRendererObj, "§7Username", centerX - 97, height / 2 - 35, 0x808080);
        }
        if (licenseKeyField.getText().isEmpty() && !licenseKeyField.isFocused()) {
            drawString(fontRendererObj, "§7License Key (required for register)", centerX - 97, height / 2 - 5, 0x808080);
        }

        if (!statusMessage.isEmpty()) {
            drawCenteredString(fontRendererObj, statusMessage, centerX, height / 2 + 55, statusColor);
        }

        if (processing) {
            drawCenteredString(fontRendererObj, "§eProcessing...", centerX, height / 2 + 70, 0xFFFF00);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB) {
            if (usernameField.isFocused()) {
                usernameField.setFocused(false);
                licenseKeyField.setFocused(true);
            } else {
                licenseKeyField.setFocused(false);
                usernameField.setFocused(true);
            }
        } else if (keyCode == Keyboard.KEY_RETURN) {
            doLogin();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            if (AuthManager.getInstance().isAuthenticated()) {
                mc.displayGuiScreen(previousScreen);
            }
        } else {
            usernameField.textboxKeyTyped(typedChar, keyCode);
            licenseKeyField.textboxKeyTyped(typedChar, keyCode);
            updateButtons();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        usernameField.mouseClicked(mouseX, mouseY, mouseButton);
        licenseKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        updateButtons();
    }

    @Override
    public void updateScreen() {
        usernameField.updateCursorCounter();
        licenseKeyField.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void doLogin() {
        if (processing) {
            return;
        }
        final String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            setStatus("§cPlease enter a username", 0xFF5555);
            return;
        }

        processing = true;
        updateButtons();
        setStatus("§7Logging in...", 0xAAAAAA);

        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }

        pendingTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthManager.getInstance().login(username);
                    setStatus("§aLogin successful!", 0x55FF55);
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            mc.displayGuiScreen(previousScreen);
                        }
                    });
                } catch (final AuthError e) {
                    setStatus("§c" + e.getMessage(), 0xFF5555);
                } catch (final Exception e) {
                    setStatus("§cNetwork error: " + e.getMessage(), 0xFF5555);
                } finally {
                    processing = false;
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            updateButtons();
                        }
                    });
                }
            }
        }, executor);
    }

    private void doRegister() {
        if (processing) {
            return;
        }
        final String username = usernameField.getText().trim();
        final String licenseKey = licenseKeyField.getText().trim();
        if (username.isEmpty()) {
            setStatus("§cPlease enter a username", 0xFF5555);
            return;
        }
        if (licenseKey.isEmpty()) {
            setStatus("§cLicense key is required for registration", 0xFF5555);
            return;
        }

        processing = true;
        updateButtons();
        setStatus("§7Registering...", 0xAAAAAA);

        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }

        pendingTask = CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthManager.getInstance().register(username, licenseKey);
                    setStatus("§aRegistration successful!", 0x55FF55);
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            mc.displayGuiScreen(previousScreen);
                        }
                    });
                } catch (final AuthError e) {
                    setStatus("§c" + e.getMessage(), 0xFF5555);
                } catch (final Exception e) {
                    setStatus("§cNetwork error: " + e.getMessage(), 0xFF5555);
                } finally {
                    processing = false;
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            updateButtons();
                        }
                    });
                }
            }
        }, executor);
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void updateButtons() {
        boolean hasUsername = usernameField != null && !usernameField.getText().trim().isEmpty();
        boolean hasKey = licenseKeyField != null && !licenseKeyField.getText().trim().isEmpty();
        loginButton.enabled = hasUsername && !processing;
        registerButton.enabled = hasUsername && hasKey && !processing;
    }
}
