package com.vivet.speedrun.client.screen;

import com.vivet.speedrun.client.ui.FlatButton;
import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.update.ReleaseManifest;
import com.vivet.speedrun.update.UpdateChecker;
import com.vivet.speedrun.update.UpdateInstaller;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public final class UpdateScreen extends VivetScreen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 168;

    private static boolean answered;

    private final Supplier<Screen> onward;
    private final ReleaseManifest release;

    private int left;
    private int top;

    private volatile String status = "";
    private volatile int statusColor = Theme.TEXT_MUTED;
    private volatile boolean done;

    private UpdateScreen(Supplier<Screen> onward, ReleaseManifest release) {
        super(Component.translatable("vivet.update.title"));
        this.onward = onward;
        this.release = release;
    }

    public static boolean waiting() {
        return !answered && UpdateChecker.available() != null;
    }

    public static Screen inFrontOf(Supplier<Screen> onward) {
        ReleaseManifest release = UpdateChecker.available();
        if (answered || release == null) return onward.get();

        answered = true;
        return new UpdateScreen(onward, release);
    }

    @Override
    protected void init() {
        super.init();

        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;

        int buttonY = this.top + HEIGHT - 32;
        int half = (WIDTH - 24 - 8) / 2;

        if (this.done) {
            addRenderableWidget(new FlatButton(this.left + 12, buttonY, half, 20,
                    Component.translatable("vivet.update.on_exit"),
                    this::onClose,
                    FlatButton.Style.GHOST));

            boolean canRestart = UpdateInstaller.canRestart();
            addRenderableWidget(new FlatButton(this.left + 12 + half + 8, buttonY, half, 20,
                    Component.translatable(canRestart
                            ? "vivet.update.restart_now" : "vivet.update.quit_now"),
                    canRestart ? this::restart : this::quit,
                    FlatButton.Style.PRIMARY));
            return;
        }

        addRenderableWidget(new FlatButton(this.left + 12, buttonY, half, 20,
                Component.translatable("vivet.update.later"),
                this::onClose,
                FlatButton.Style.GHOST));

        addRenderableWidget(new FlatButton(this.left + 12 + half + 8, buttonY, half, 20,
                Component.translatable("vivet.update.install"),
                this::install,
                FlatButton.Style.PRIMARY));
    }

    private void restart() {
        UpdateInstaller.restartNow(this::quit);
    }

    private void quit() {
        this.minecraft.stop();
    }

    private void install() {
        if (UpdateInstaller.isBusy() || this.done) return;

        if (UpdateInstaller.isStaged()) {
            this.status = Component.translatable(UpdateInstaller.canRestart()
                    ? "vivet.update.ready_restart" : "vivet.update.ready_quit").getString();
            this.statusColor = Theme.SUCCESS;
            this.done = true;
            rebuild();
            return;
        }

        this.status = Component.translatable("vivet.update.downloading").getString();
        this.statusColor = Theme.TEXT_MUTED;
        rebuildWithoutButtons();

        UpdateInstaller.install(this.release,
                size -> {
                    this.status = Component.translatable(UpdateInstaller.canRestart()
                            ? "vivet.update.ready_restart"
                            : "vivet.update.ready_quit").getString();
                    this.statusColor = Theme.SUCCESS;
                    this.done = true;

                    this.minecraft.execute(this::rebuild);
                },
                reason -> {
                    this.status = reason;
                    this.statusColor = Theme.DANGER;

                    this.minecraft.execute(this::rebuild);
                });
    }

    private void rebuildWithoutButtons() {
        clearWidgets();
        addRenderableWidget(new FlatButton(this.left + 12, this.top + HEIGHT - 32, WIDTH - 24, 20,
                Component.translatable("vivet.update.close"),
                this::onClose,
                FlatButton.Style.GHOST));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void drawScaled(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              float partialTick) {
        Theme.fill(graphics, 0, 0, this.width, this.height, Theme.SCRIM);
        Theme.panel(graphics, this.left, this.top, WIDTH, HEIGHT);

        int x = this.left + 16;
        int y = this.top + 18;

        graphics.text(this.font, Component.translatable("vivet.update.title"), x, y, Theme.TEXT);
        y += 16;

        graphics.text(this.font,
                Component.translatable("vivet.update.version",
                        com.vivet.speedrun.VivetSpeedrun.shownVersion(this.release.version)),
                x, y, Theme.ACCENT);
        y += 18;

        String notes = this.release.notes == null || this.release.notes.isBlank()
                ? Component.translatable("vivet.update.no_notes").getString()
                : this.release.notes;

        for (var line : this.font.split(Component.literal(notes), WIDTH - 32)) {
            graphics.text(this.font, line, x, y, Theme.TEXT_MUTED);
            y += 11;
        }

        if (!this.status.isEmpty()) {
            graphics.text(this.font, Component.literal(this.status),
                    x, this.top + HEIGHT - 50, this.statusColor);
        }

        drawWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.onward.get());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !UpdateInstaller.isBusy();
    }
}
