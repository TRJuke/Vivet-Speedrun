package com.vivet.speedrun.client.replay;

import com.vivet.speedrun.replay.ReplayEvent;
import com.vivet.speedrun.replay.ReplayItems;
import com.vivet.speedrun.replay.ReplayMoment;
import com.vivet.speedrun.replay.ReplayTrace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public final class ReplayWords {
    public static final int[] PLAYER_COLOURS = { com.vivet.speedrun.client.ui.Theme.ACCENT,
                                                 com.vivet.speedrun.client.ui.Theme.WARNING };

    private ReplayWords() {
    }

    public record EventLine(int centis, int who, Component text) {
    }

    public static java.util.List<EventLine> events(java.util.List<ReplayTrace> traces) {
        java.util.List<EventLine> lines = new java.util.ArrayList<>();
        for (int who = 0; who < traces.size(); who++) {
            ReplayTrace trace = traces.get(who);
            if (trace == null) continue;
            ReplayTrace.Moment previous = null;
            for (ReplayTrace.Moment moment : trace.moments()) {
                Component text = describe(moment, previous);
                if (text != null) lines.add(new EventLine(moment.centis(), who, text));
                previous = moment;
            }
            for (ReplayTrace.Sample sample : trace.samples()) {
                Component text = describe(sample);
                if (text != null) lines.add(new EventLine(sample.centis(), who, text));
            }
        }
        lines.sort((a, b) -> Integer.compare(a.centis(), b.centis()));
        return lines;
    }

    public static Component describe(ReplayTrace.Moment moment, ReplayTrace.Moment previous) {
        switch (moment.type()) {
            case CHAT -> {
                if (moment.text() == null || moment.text().isEmpty()) return null;
                String who = moment.who() == null || moment.who().isEmpty() ? "?" : moment.who();

                return Component.literal("<" + who + "> " + moment.text());
            }
            case CRAFT -> {
                Component name = itemName(moment.id());
                return name == null ? null
                        : Component.translatable("vivet.replay.feed_crafted", Math.max(1, moment.amount()), name);
            }
            case SMELTED -> {
                Component name = itemName(moment.id());
                return name == null ? null
                        : Component.translatable("vivet.replay.feed_smelted", Math.max(1, moment.amount()), name);
            }
            case ENCHANTED -> {
                Component name = itemName(moment.id());
                return name == null ? null : Component.translatable("vivet.replay.feed_enchanted", name);
            }
            case EAT -> {
                Component name = itemName(moment.id());
                return name == null ? null : Component.translatable("vivet.replay.feed_ate", name);
            }
            case THROW -> {
                if (moment.id() == null || moment.id().endsWith("arrow")) return null;
                Component name = itemName(moment.id());
                return name == null ? null : Component.translatable("vivet.replay.feed_threw", name);
            }
            case ENTITY_DIED -> {
                Component name = entityName(moment.id());
                return name == null ? null : Component.translatable("vivet.replay.feed_killed", name);
            }
            case KILLED_BY -> {
                Component name = entityName(moment.id());
                return name == null ? null : Component.translatable("vivet.replay.feed_killed_by", name);
            }
            case DEATH -> {
                if (previous != null && previous.type() == ReplayMoment.KILLED_BY
                        && previous.centis() == moment.centis() && entityName(previous.id()) != null) {
                    return null;
                }
                return Component.translatable("vivet.replay.feed_died");
            }
            case CONTAINER_OPEN -> {
                ReplayMoment.Container kind = ReplayMoment.Container.of(moment.amount());
                return Component.translatable("vivet.replay.feed_opened",
                        Component.translatable("vivet.replay.container." + kind.name().toLowerCase()));
            }
            case ADVANCEMENT -> {
                Component title = advancementTitle(moment.id());
                return title == null ? null : Component.translatable("vivet.replay.feed_earned", title);
            }
            case PORTAL_LIT -> {
                return Component.translatable("vivet.replay.feed_portal");
            }
            case SLEEP -> {
                return Component.translatable("vivet.replay.feed_slept");
            }
            case AWAY -> {
                return Component.translatable("vivet.replay.feed_away",
                        com.vivet.speedrun.game.Time.format(Math.max(0, moment.amount())));
            }
            case TAMPER -> {
                return Component.translatable("vivet.replay.feed_tampered");
            }
            default -> {
                return null;
            }
        }
    }

    public static Component describe(ReplayTrace.Sample sample) {
        if (sample.event() == ReplayEvent.FOUND) return Component.translatable("vivet.replay.feed_found");
        if (sample.event() == ReplayEvent.DIMENSION) {
            return Component.translatable("vivet.replay.feed_went_" + Math.max(0, Math.min(2, sample.dimension())));
        }
        return null;
    }

    public static Component dimensionName(int dimension) {
        return Component.translatable(switch (dimension) {
            case 1 -> "vivet.replay.nether";
            case 2 -> "vivet.replay.end";
            default -> "vivet.replay.overworld";
        });
    }

    public static Component itemName(String id) {
        return id == null || id.isEmpty() ? null : ReplayItems.nameOf(new ReplayTrace.Item(id, 1));
    }

    public static Component entityName(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            Identifier key = Identifier.tryParse(id);
            if (key == null) return null;
            return BuiltInRegistries.ENTITY_TYPE.getOptional(key).map(EntityType::getDescription).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Component advancementTitle(String id) {
        if (id == null) return null;
        Identifier key = Identifier.tryParse(id);
        if (key == null) return null;
        String translation = "advancements." + key.getPath().replace('/', '.') + ".title";
        return Language.getInstance().has(translation) ? Component.translatable(translation) : null;
    }
}
