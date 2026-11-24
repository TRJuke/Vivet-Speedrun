package com.vivet.speedrun.client.replay;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;

public final class ReplayBody extends RemotePlayer {
    private double lastX;
    private double lastZ;
    private boolean placed;
    private float stepDistance;
    private float nextStepAt = 1.0F;

    private boolean posedByRecording;

    public ReplayBody(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    static int poseDataId() {
        return DATA_POSE.id();
    }

    void posedByRecording() {
        this.posedByRecording = true;
    }

    @Override
    protected void updatePlayerPose() {
        if (!this.posedByRecording) super.updatePlayerPose();
    }

    @Override
    public void updateSwimming() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.placed) {
            this.lastX = getX();
            this.lastZ = getZ();
            this.placed = true;
            return;
        }

        double dx = getX() - this.lastX;
        double dz = getZ() - this.lastZ;
        this.lastX = getX();
        this.lastZ = getZ();
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 8.0) {
            this.stepDistance = 0.0F;
            this.nextStepAt = 1.0F;
            return;
        }

        this.stepDistance += (float) (moved * 0.6);
        if (this.stepDistance <= this.nextStepAt) return;
        if (isInWater()) {
            this.nextStepAt = (int) this.stepDistance + 1;
            if (ReplayPlayback.audible()) waterSwimSound();
            return;
        }
        if (!onGround() || isFallFlying() || isPassenger()) return;
        BlockPos under = getOnPos();
        BlockState state = level().getBlockState(under);
        if (state.isAir()) return;
        this.nextStepAt = (int) this.stepDistance + 1;
        if (isDiscrete() || !ReplayPlayback.audible()) return;
        playStepSound(under, state);
    }

    void moveInto(ClientLevel level) {
        setLevel(level);
    }

    void copyFrom(ReplayBody body) {
        copyFrom(body, true);
    }

    void copyFrom(net.minecraft.world.entity.player.Player body, boolean effects) {
        setPos(body.getX(), body.getY(), body.getZ());
        setYRot(body.getYRot());

        internalSetAbsorptionAmount(body.getAbsorptionAmount());
        setAirSupply(body.getAirSupply());
        setTicksFrozen(body.getTicksFrozen());
        setMainArm(body.getMainArm());
        setInvulnerableTime(body.getInvulnerableTime());

        for (var attribute : java.util.List.of(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)) {
            var own = getAttribute(attribute);
            if (own != null) own.setBaseValue(body.getAttributeValue(attribute));
        }

        if (!effects) return;
        var theirs = body.getActiveEffectsMap();
        var mine = getActiveEffectsMap();
        if (!theirs.equals(mine)) {
            for (var effect : java.util.List.copyOf(mine.keySet())) {
                if (!theirs.containsKey(effect)) removeEffectNoUpdate(effect);
            }
            for (var instance : theirs.values()) {
                forceAddEffect(new net.minecraft.world.effect.MobEffectInstance(instance), null);
            }
        }
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (isSilent() || !ReplayPlayback.audible()) return;
        level().playLocalSound(getX(), getY(), getZ(), sound, getSoundSource(), volume, pitch, false);
    }
}
