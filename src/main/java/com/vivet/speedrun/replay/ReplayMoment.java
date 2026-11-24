package com.vivet.speedrun.replay;

public enum ReplayMoment {
    BLOCK_SET,

    ENTITY_DIED,

    ENTITY_HURT,

    ATTACK,

    SWING,

    USE,

    EAT,

    CRAFT,

    PICKUP,

    DROP,

    CHAT,

    HURT,

    DEATH,

    CONTAINER_OPEN,

    CONTAINER_CLOSE,

    TAMPER,

    SLEEP,

    THROW,

    MINE_PROGRESS,

    AWAY,

    ADVANCEMENT,

    SMELTED,

    ENCHANTED,

    PORTAL_LIT,

    KILLED_BY;

    public enum Container {
        OTHER, CHEST, BARREL, SHULKER_BOX, ENDER_CHEST, FURNACE, SMOKER, BLAST_FURNACE,
        CRAFTING, ENCHANTING, ANVIL, BREWING, HOPPER, DISPENSER, SMITHING, GRINDSTONE, STONECUTTER,
        LOOM, CARTOGRAPHY, BEACON, VILLAGER, BOAT, MINECART;

        private static final Container[] ALL = values();

        public static Container of(int code) {
            return code >= 0 && code < ALL.length ? ALL[code] : OTHER;
        }
    }

    private static final ReplayMoment[] VALUES = values();

    public byte index() {
        return (byte) ordinal();
    }

    public static ReplayMoment of(int value) {
        return value >= 0 && value < VALUES.length ? VALUES[value] : null;
    }
}
