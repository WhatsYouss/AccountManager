package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuiTokenLogin extends GuiScreen {
    private final GuiScreen previousScreen;

    private GuiTextField tokenField;
    private GuiButton loginButton;
    private GuiButton cancelButton;
    private String status = "§7Enter your Minecraft Access Token§r";
    private ExecutorService executor;
    private CompletableFuture<Void> task;

    public GuiTokenLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.buttonList.clear();
        this.buttonList.add(loginButton = new GuiButton(
                0, width / 2 - 100, height / 2 + 30, 200, 20, "Login"
        ));
        this.buttonList.add(cancelButton = new GuiButton(
                1, width / 2 - 100, height / 2 + 55, 200, 20, "Cancel"
        ));

        this.tokenField = new GuiTextField(2, this.fontRendererObj,
                width / 2 - 100, height / 2, 200, 20);
        this.tokenField.setMaxStringLength(1000);
        this.tokenField.setFocused(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            executor.shutdownNow();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled) {
            switch (button.id) {
                case 0: // Login
                    String token = tokenField.getText().trim();
                    if (!token.isEmpty()) {
                        loginWithToken(token);
                    }
                    break;
                case 1: // Cancel
                    mc.displayGuiScreen(previousScreen);
                    break;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            actionPerformed(cancelButton);
            return;
        }

        this.tokenField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_RETURN) {
            actionPerformed(loginButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.tokenField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        this.tokenField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "§fLogin with Access Token",
                width / 2, height / 2 - 30, 0xFFFFFF);
        drawCenteredString(fontRendererObj, status,
                width / 2, height / 2 - 15, 0xAAAAAA);

        this.tokenField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void loginWithToken(String token) {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }

        status = "§7Authenticating...§r";
        loginButton.enabled = false;

        task = MicrosoftAuth.login(token, executor)
                .thenAcceptAsync(session -> {
                    Account acc = new Account("", token, session.getUsername());
                    AccountManager.accounts.add(acc);
                    AccountManager.save();
                    SessionManager.set(session);

                    mc.addScheduledTask(() -> {
                        mc.displayGuiScreen(new GuiAccountManager(
                                previousScreen,
                                new Notification(
                                        TextFormatting.translate(String.format(
                                                "§aSuccessful login! (%s)§r",
                                                session.getUsername()
                                        )),
                                        5000L
                                )
                        ));
                    });
                }, executor)
                .exceptionally(error -> {
                    mc.addScheduledTask(() -> {
                        status = "§c" + error.getCause().getMessage();
                        loginButton.enabled = true;
                    });
                    return null;
                });
    }
}