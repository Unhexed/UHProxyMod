package dev.kris.proxymod.screen;

import dev.kris.proxymod.ProxyMod;
import dev.kris.proxymod.config.ProxySettings;
import dev.kris.proxymod.config.ProxyType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;

public final class ProxySettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private ProxyType type;
    private boolean showPassword;
    private EditBox host;
    private EditBox port;
    private EditBox username;
    private EditBox password;
    private EditBox timeout;
    private String error;
    private int left;
    private int top;
    private int formWidth;

    public ProxySettingsScreen(Screen parent) {
        super(Component.literal("Proxy settings"));
        this.parent = parent;
        ProxySettings settings = ProxyMod.config().settings();
        enabled = settings.enabled();
        type = settings.type();
        error = ProxyMod.config().loadError();
    }

    @Override
    protected void init() {
        ProxySettings settings = ProxyMod.config().settings();
        String hostText = host == null ? settings.host() : host.getValue();
        String portText = port == null ? Integer.toString(settings.port()) : port.getValue();
        String userText = username == null ? settings.username() : username.getValue();
        String passwordText = password == null ? settings.password() : password.getValue();
        String timeoutText = timeout == null ? Integer.toString(settings.timeoutSeconds()) : timeout.getValue();

        formWidth = Math.min(330, width - 24);
        left = (width - formWidth) / 2;
        top = Math.max(6, (height - 234) / 2);
        int half = (formWidth - 8) / 2;
        int portWidth = 66;

        addRenderableWidget(Button.builder(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
            error = null;
        }).bounds(left, top + 22, half, 20).tooltip(Tooltip.create(Component.literal(
                "Routes multiplayer and pings through your proxy. Changes apply after Save."
        ))).build());
        addRenderableWidget(Button.builder(typeLabel(), button -> {
            type = type == ProxyType.SOCKS5 ? ProxyType.HTTP : ProxyType.SOCKS5;
            button.setMessage(typeLabel());
            error = null;
        }).bounds(left + half + 8, top + 22, half, 20).build());

        host = field(left, top + 60, formWidth - portWidth - 8, "Proxy host", hostText, 253);
        host.setHint(Component.literal("127.0.0.1"));
        port = field(left + formWidth - portWidth, top + 60, portWidth, "Port", portText, 5);
        username = field(left, top + 98, half, "Username (optional)", userText, 255);
        password = field(left + half + 8, top + 98, half, "Password", passwordText, 255);
        password.addFormatter((value, offset) -> FormattedCharSequence.forward(
                showPassword ? value : "*".repeat(value.length()), Style.EMPTY));
        timeout = field(left, top + 136, 66, "Timeout in seconds", timeoutText, 2);
        addRenderableWidget(Button.builder(passwordLabel(), button -> {
            showPassword = !showPassword;
            button.setMessage(passwordLabel());
        }).bounds(left + half + 8, top + 136, half, 20).build());

        int buttonY = top + 208;
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left, buttonY, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + half + 8, buttonY, half, 20).build());
        setInitialFocus(host);
    }

    private EditBox field(int x, int y, int fieldWidth, String label, String value, int maximumLength) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 20, Component.literal(label));
        field.setMaxLength(maximumLength);
        field.setValue(value);
        field.setResponder(ignored -> error = null);
        return addRenderableWidget(field);
    }

    private Component enabledLabel() {
        return Component.literal("Proxy: " + (enabled ? "ON" : "OFF"));
    }

    private Component typeLabel() {
        return Component.literal("Type: " + type.displayName());
    }

    private Component passwordLabel() {
        return Component.literal(showPassword ? "Hide password" : "Show password");
    }

    private void save() {
        try {
            int proxyPort;
            int timeoutSeconds;
            try {
                proxyPort = Integer.parseInt(port.getValue());
                timeoutSeconds = Integer.parseInt(timeout.getValue());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Enter a valid port and timeout.");
            }
            ProxySettings updated = new ProxySettings(enabled, type, host.getValue(), proxyPort,
                    username.getValue(), password.getValue(), timeoutSeconds);
            ProxyMod.config().save(updated);
            onClose();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        } catch (IOException exception) {
            error = "Could not save config/proxy-mod.json. Check file permissions.";
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 3, 0xFFFFFFFF);
        graphics.text(font, "Proxy host", left, top + 49, 0xFFCCCCCC);
        graphics.text(font, "Port", left + formWidth - 66, top + 49, 0xFFCCCCCC);
        graphics.text(font, "Username (optional)", left, top + 87, 0xFFCCCCCC);
        graphics.text(font, "Password", left + (formWidth - 8) / 2 + 8, top + 87, 0xFFCCCCCC);
        graphics.text(font, "Timeout (seconds)", left, top + 125, 0xFFCCCCCC);
        graphics.centeredText(font, "Passwords are saved in plain text.", width / 2, top + 164, 0xFFAAAAAA);
        if (error != null) {
            int line = 0;
            for (FormattedCharSequence part : font.split(Component.literal(error), formWidth)) {
                graphics.text(font, part, left, top + 179 + line * 10, 0xFFFF7777);
                if (++line == 2) break;
            }
        } else {
            graphics.centeredText(font, "Changes apply to new connections.", width / 2, top + 179, 0xFFAAAAAA);
            graphics.centeredText(font, "Remote DNS; use the server's actual host and port.",
                    width / 2, top + 191, 0xFFAAAAAA);
        }
    }
}
