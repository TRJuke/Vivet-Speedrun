package com.vivet.speedrun.client.hud;

import com.vivet.speedrun.client.ui.Theme;
import com.vivet.speedrun.client.ui.UiScale;
import com.vivet.speedrun.game.Difficulty;
import com.vivet.speedrun.game.ItemTiers;
import com.vivet.speedrun.game.Target;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class ItemRoll {
    private static final long SPIN_MS = 2700L;

    private static final long HOLD_STILL_MAX_MS = com.vivet.speedrun.game.RunManager.CLOCK_HOLD_LIMIT_MS;

    private static final long SETTLE_MS = 260L;

    private static final long COLLAPSE_MS = 220L;

    private static final int TRAVEL = 26;

    private static final int TAIL = 8;

    private static final int VISIBLE_SIDES = 4;

    private static final float FALLOFF = 0.75F;

    private static final int ITEM_SIZE = 16;
    private static final float CENTRE_SCALE = 4.5F;

    private static final float SPREAD = 78.0F;

    private static final float FULL_WIDTH =
            2.0F * (SPREAD * (1.0F - (float) Math.pow(FALLOFF, VISIBLE_SIDES)) / (1.0F - FALLOFF)
                    + ITEM_SIZE * CENTRE_SCALE * (float) Math.pow(FALLOFF, VISIBLE_SIDES) / 2.0F);

    private static final float WIDTH_BUDGET = 0.85F;

    private static final Random RANDOM = new Random();

    private static List<ItemStack> reel = List.of();
    private static ItemStack winner = ItemStack.EMPTY;

    private static long startedAt;
    private static boolean playing;

    private static int lastTickedSlot = -1;

    private static long landedAt;

    private static boolean concealing;

    private static boolean holdStill;
    private static long holdStillSince;

    private static Runnable onRevealed = () -> { };

    private ItemRoll() {
    }

    public static void conceal() {
        concealing = true;
    }

    public static void holdStill() {
        holdStill = true;
        holdStillSince = System.currentTimeMillis();
    }

    public static boolean isHoldingStill() {
        if (!holdStill) return false;
        if (System.currentTimeMillis() - holdStillSince > HOLD_STILL_MAX_MS) holdStill = false;
        return holdStill;
    }

    public static boolean isConcealing() {
        return concealing;
    }

    public static void start(Target target, Difficulty tier, Runnable revealed) {
        onRevealed = revealed == null ? () -> { } : revealed;
        if (target == null) {
            reveal();
            return;
        }
        concealing = true;

        List<Target> decoys = ItemTiers.pool(tier, true,
                com.vivet.speedrun.session.Session.activeDimension(), Set.of());

        if (decoys.size() < VISIBLE_SIDES * 2) decoys = ItemTiers.pool(tier, true, Set.of());
        List<ItemStack> built = new ArrayList<>(TRAVEL + 1 + TAIL);

        for (int i = 0; i < TRAVEL; i++) built.add(decoy(decoys, target));
        built.add(target.displayStack());
        for (int i = 0; i < TAIL; i++) built.add(decoy(decoys, target));

        reel = List.copyOf(built);
        winner = target.displayStack();
        startedAt = System.currentTimeMillis();

        if (holdStill) holdStillSince = startedAt;
        landedAt = 0L;
        lastTickedSlot = -1;
        playing = true;
    }

    private static ItemStack decoy(List<Target> decoys, Target fallback) {
        return decoys.isEmpty()
                ? fallback.displayStack()
                : decoys.get(RANDOM.nextInt(decoys.size())).displayStack();
    }

    public static void cancel() {
        playing = false;
        landedAt = 0L;
        reel = List.of();
        winner = ItemStack.EMPTY;
        reveal();
    }

    private static void reveal() {
        concealing = false;
        holdStill = false;

        Runnable pending = onRevealed;
        onRevealed = () -> { };
        pending.run();
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!playing || reel.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            cancel();
            return;
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        float spin = Math.min(1.0F, elapsed / (float) SPIN_MS);
        float position = TRAVEL * easeOut(spin);

        tickSlots(minecraft, spin, position);

        if (spin >= 1.0F && landedAt == 0L) land(minecraft);

        float collapse = 0.0F;
        if (landedAt != 0L) {
            long since = System.currentTimeMillis() - landedAt;
            if (since >= SETTLE_MS) {
                collapse = Math.min(1.0F, (since - SETTLE_MS) / (float) COLLAPSE_MS);
            }
        }

        if (collapse >= 1.0F) {
            finish(minecraft);
            return;
        }

        draw(graphics, elapsed, position, collapse);
    }

    private static void draw(GuiGraphicsExtractor graphics, long elapsed, float position,
                             float collapse) {
        int centreX = UiScale.guiWidth(graphics) / 2;
        int centreY = UiScale.guiHeight(graphics) / 2;

        float fit = Math.min(1.0F, UiScale.guiWidth(graphics) * WIDTH_BUDGET / FULL_WIDTH);

        float fade = Math.min(1.0F, elapsed / 150.0F);

        for (int step = VISIBLE_SIDES; step >= 0; step--) {
            for (int sign = (step == 0 ? 0 : -1); sign <= 1; sign += 2) {
                int index = Math.round(position) + step * sign;
                float distance = index - position;
                if (Math.abs(distance) > VISIBLE_SIDES + 0.5F) continue;
                if (index < 0 || index >= reel.size()) continue;

                drawItem(graphics, reel.get(index), centreX, centreY, distance, collapse, fit);
                if (step == 0) break;
            }
        }

        drawCentreLine(graphics, centreX, centreY, fade, collapse, fit);
    }

    private static void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int centreX,
                                 int centreY, float distance, float collapse, float fit) {
        float magnitude = Math.abs(distance);
        float shrink = (float) Math.pow(FALLOFF, magnitude);

        float scale = CENTRE_SCALE * shrink * fit;
        if (magnitude > 0.001F) scale *= 1.0F - collapse;
        if (scale <= 0.01F) return;

        float offset = Math.signum(distance) * SPREAD * (1.0F - shrink) / (1.0F - FALLOFF);
        offset *= fit * (1.0F - collapse);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centreX + offset, centreY);
        pose.scale(scale, scale);
        graphics.item(stack, -ITEM_SIZE / 2, -ITEM_SIZE / 2);
        pose.popMatrix();
    }

    private static void drawCentreLine(GuiGraphicsExtractor graphics, int centreX, int centreY,
                                       float fade, float collapse, float fit) {
        int reach = Math.round(ITEM_SIZE * CENTRE_SCALE * fit / 2.0F) + 14;

        float flare = 0.0F;
        if (landedAt != 0L) {
            long since = System.currentTimeMillis() - landedAt;
            flare = Math.max(0.0F, 1.0F - since / 260.0F);
        }

        int alpha = (int) ((90 + 165 * flare) * fade * (1.0F - collapse));
        if (alpha <= 0) return;

        int colour = Theme.withAlpha(0xFFFFFF, alpha);
        int half = flare > 0.35F ? 2 : 1;

        Theme.fill(graphics, centreX - half, centreY - reach, centreX + half, centreY + reach, colour);

        int capAlpha = (int) (alpha * 0.55F);
        Theme.fill(graphics, centreX - 6, centreY - reach - 2, centreX + 6, centreY - reach,
                Theme.withAlpha(0xFFFFFF, capAlpha));
        Theme.fill(graphics, centreX - 6, centreY + reach, centreX + 6, centreY + reach + 2,
                Theme.withAlpha(0xFFFFFF, capAlpha));
    }

    private static void tickSlots(Minecraft minecraft, float spin, float position) {
        int slot = (int) Math.floor(position);
        if (slot == lastTickedSlot) return;

        boolean first = lastTickedSlot == -1;
        lastTickedSlot = slot;
        if (first) return;

        if (slot >= TRAVEL) return;

        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),
                        0.9F + 0.9F * spin, 0.35F));
    }

    private static void land(Minecraft minecraft) {
        landedAt = System.currentTimeMillis();

        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.7F));
        com.vivet.speedrun.client.Cues.itemDrop();
    }

    private static void finish(Minecraft minecraft) {
        ItemStack won = winner;
        cancel();

        if (!won.isEmpty() && minecraft.player != null) {
            minecraft.player.displayItemActivation(won);
        }
    }

    private static float easeOut(float t) {
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse * inverse;
    }
}
