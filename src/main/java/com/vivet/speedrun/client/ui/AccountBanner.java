package com.vivet.speedrun.client.ui;

import com.vivet.speedrun.client.Cues;
import com.vivet.speedrun.game.AccountXp;
import com.vivet.speedrun.p2p.MojangAuth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class AccountBanner extends AbstractWidget {
    public static final int HEIGHT = 50;

    private static final int PAD = 8;

    private static final int HEAD = 32;

    private static final int GAP = 10;
    private static final int RULE_GAP = 10;
    private static final int BADGE_BOX = 32;

    private static final int LEVEL_MIN = 132;

    private static final float NAME_SCALE = 1.0F;

    private static final int BAR_H = 5;

    private final Runnable onPress;

    private float hoverAmount;

    public AccountBanner(int x, int y, int width, Runnable onPress) {
        super(x, y, width, HEIGHT, Component.translatable("vivet.title.profile"));
        this.onPress = onPress;
    }

    private static String nameOf() {
        MojangAuth.Identity identity = MojangAuth.identity();
        return identity == null ? null : identity.name;
    }

    private static MojangAuth.Profile profileOf() {
        MojangAuth.Identity identity = MojangAuth.identity();
        return identity == null ? null : identity.profile;
    }

    public static String rankText(MojangAuth.Profile profile) {
        if (profile == null || !profile.placed) return "Unranked";
        return Ranks.nameOf(Ranks.displayRank(profile));
    }

    public static String pointsText(MojangAuth.Profile profile) {
        if (profile == null) return "Signed out";
        if (!profile.placed) {
            return "Placements " + profile.placementGames + "/" + profile.placementMatches;
        }
        return (profile.rating == null ? 0 : profile.rating) + " Points";
    }

    public static int widthFor(Font font) {
        String name = nameOf();
        MojangAuth.Profile profile = profileOf();

        int nameWidth = name == null
                ? font.width("Not signed in")
                : Math.round((NameLabel.tagWidthOf(font, NameLabel.of(name), name)
                        + NameLabel.runWidthOf(font, name)) * NAME_SCALE);

        int leftColumn = Math.max(nameWidth, LEVEL_MIN);

        int rightColumn = Math.max(font.width(rankText(profile)), font.width(pointsText(profile)));

        return PAD + HEAD + GAP + leftColumn + RULE_GAP + 1 + RULE_GAP
                + rightColumn + GAP + BADGE_BOX + PAD;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        float target = isHoveredOrFocused() ? 1.0F : 0.0F;
        this.hoverAmount += (target - this.hoverAmount) * Math.min(1.0F, partialTick * 0.5F);

        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        MojangAuth.Profile profile = profileOf();
        String name = nameOf();
        String rankId = profile != null && profile.placed ? Ranks.displayRank(profile) : "unranked";
        int rankColor = profile != null && profile.placed
                ? Ranks.colorOf(rankId) : Theme.TEXT_FAINT;

        panel(graphics, x, y, width, height, this.hoverAmount);

        int cursor = x + PAD;

        int framed = PlayerHead.framedSize(HEAD);
        PlayerHead.drawFramed(graphics, cursor, y + (height - framed) / 2, HEAD, rankColor);
        cursor += framed + GAP;

        int nameY = y + 12;
        int lineY = y + 30;

        int nameWidth = drawName(graphics, font, name, cursor, nameY);
        int column = Math.max(nameWidth, LEVEL_MIN);

        AccountXp.State state = AccountXp.current(null, null);
        drawLevel(graphics, font, state, cursor, lineY, column);

        cursor += column + RULE_GAP;

        Theme.fill(graphics, cursor, y + 8, cursor + 1, y + height - 8, Theme.withAlpha(0xFFFFFFFF, 0x33));
        cursor += 1 + RULE_GAP;

        String rank = rankText(profile);
        graphics.text(font, Component.literal(rank), cursor, y + 14, rankColor, true);

        Theme.fill(graphics, cursor, y + 25, cursor + font.width(rank), y + 26,
                Theme.withAlpha(rankColor, 0x66));
        graphics.text(font, Component.literal(pointsText(profile)), cursor, y + 30,
                rankColor, true);

        int badgeCx = x + width - PAD - BADGE_BOX / 2;
        RankEmblem.drawCompact(graphics, badgeCx, y + height / 2, rankId,
                profile != null && profile.placed);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              float lit) {
        Theme.roundedBox(graphics, x, y, width, height, 4,
                Theme.mix(Theme.TITLE_PLATE, Theme.TITLE_PLATE_LIT, lit));
        Theme.roundedBoxOutline(graphics, x, y, width, height, 4,
                Theme.mix(Theme.PLATE_EDGE, Theme.PLATE_EDGE_LIT, lit));
    }

    private int drawName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y) {
        if (name == null) {
            graphics.text(font, Component.translatable("vivet.title.signed_out"), x, y + 3,
                    Theme.TEXT_FAINT, true);
            return font.width(Component.translatable("vivet.title.signed_out").getString());
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(NAME_SCALE, NAME_SCALE);
        int drawn = NameLabel.drawTaggedLeft(graphics, font, name, 0, 0, Theme.TEXT);
        pose.popMatrix();

        return Math.round(drawn * NAME_SCALE);
    }

    private void drawLevel(GuiGraphicsExtractor graphics, Font font, AccountXp.State state,
                           int x, int y, int column) {
        String label = "LVL";
        String number = String.valueOf(state.level());
        int colour = Levels.colorOf(state.level());

        graphics.text(font, Component.literal(label), x, y, Theme.fade(colour, 0.62F), true);
        int numberX = x + font.width(label) + 4;
        graphics.text(font, Component.literal(number), numberX, y, colour, true);

        int barX = numberX + font.width(number) + 7;
        int barY = y + 2;
        int barW = Math.max(24, x + column - barX);

        Theme.fill(graphics, barX, barY, barX + barW, barY + BAR_H, 0xFF101116);
        Theme.fill(graphics, barX + 1, barY + 1, barX + barW - 1, barY + BAR_H - 1, 0xFF454A57);

        int filled = Math.round((barW - 2) * state.progress());
        if (filled > 0) {
            Theme.fill(graphics, barX + 1, barY + 1, barX + 1 + filled, barY + BAR_H - 1, colour);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) return;

        if (this.onPress == null) {
            Cues.nothingHappened();
            return;
        }

        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
