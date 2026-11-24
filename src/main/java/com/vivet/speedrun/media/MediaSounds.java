package com.vivet.speedrun.media;

import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.vivet.speedrun.VivetSpeedrun;
import com.vivet.speedrun.config.ConfigManager;
import com.vivet.speedrun.mixin.SoundEngineAccessor;
import com.vivet.speedrun.mixin.SoundManagerAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.sound.sampled.AudioFormat;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MediaSounds {
    private static final int RELEASE_MARGIN_TICKS = 5;

    private static final Map<String, SoundBuffer> buffers = new ConcurrentHashMap<>();

    private static final Map<String, Integer> lengths = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> started = new ConcurrentHashMap<>();

    private static final Map<String, Integer> lengthsMs = new ConcurrentHashMap<>();

    private static final float TARGET_RMS = 0.1F;

    private static final float CEILING = 0.89F;

    private static final float MAX_GAIN = 8.0F;
    private static final float MIN_GAIN = 0.05F;

    private static final float HOSTED_CEILING = 0.5F;

    private record Playing(ChannelAccess.ChannelHandle handle, long dueTick) {
    }

    private static final List<Playing> playing = new ArrayList<>();

    private static long clientTick;

    private MediaSounds() {
    }

    private static boolean nothing(String id) {
        return id == null || id.isEmpty();
    }

    public static void prepare(String id) {
        if (nothing(id)) return;
        if (buffers.containsKey(id) || started.containsKey(id)) return;

        Path file = MediaCache.path(id);
        if (file == null) return;

        started.put(id, Boolean.TRUE);

        Thread worker = new Thread(() -> decode(id, file), "vivet-media-decode");
        worker.setDaemon(true);
        worker.start();
    }

    private static void normalise(String id, ByteBuffer samples, AudioFormat format) {
        if (format.getSampleSizeInBits() != 16 || format.isBigEndian()) return;

        ByteBuffer view = samples.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 2;
        if (count <= 0) return;

        double sum = 0.0;
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int value = view.getShort(view.position() + i * 2);
            sum += (double) value * value;
            int magnitude = Math.abs(value);
            if (magnitude > peak) peak = magnitude;
        }
        if (peak <= 0) return;

        double rms = Math.sqrt(sum / count) / Short.MAX_VALUE;
        if (rms <= 0.0) return;

        float wanted = (float) (TARGET_RMS / rms);
        float headroom = CEILING * Short.MAX_VALUE / peak;
        float gain = Math.min(wanted, headroom);

        gain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
        if (gain > 0.98F && gain < 1.02F) return;

        for (int i = 0; i < count; i++) {
            int at = view.position() + i * 2;
            int scaled = Math.round(view.getShort(at) * gain);
            view.putShort(at, (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled)));
        }

        VivetSpeedrun.LOGGER.info("Normalised {}: rms {} peak {}, gain {}x",
                id, String.format("%.3f", rms),
                String.format("%.2f", peak / (float) Short.MAX_VALUE),
                String.format("%.2f", gain));
    }

    public static int lengthMs(String id) {
        return nothing(id) ? 0 : lengthsMs.getOrDefault(id, 0);
    }

    private static void decode(String id, Path file) {
        try (InputStream in = Files.newInputStream(file);
             JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
            AudioFormat format = stream.getFormat();
            ByteBuffer samples = stream.readAll();

            int frames = samples.remaining() / Math.max(1, format.getFrameSize());
            int ticks = Math.round(frames / Math.max(1.0F, format.getFrameRate()) * 20.0F);

            normalise(id, samples, format);

            lengths.put(id, ticks);
            lengthsMs.put(id, Math.round(frames / Math.max(1.0F, format.getFrameRate()) * 1000.0F));
            buffers.put(id, new SoundBuffer(samples, format));
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.warn("Could not decode sound {}", id, e);
        }
    }

    public static void play(String id, float volume) {
        if (nothing(id)) return;

        SoundBuffer buffer = buffers.get(id);
        if (buffer == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) return;

        float master = minecraft.options.getFinalSoundSourceVolume(SoundSource.MASTER);
        float own = HOSTED_CEILING * ConfigManager.get().customAudioGain();
        float gain = Math.max(0.0F, Math.min(1.0F, volume * master * own));
        if (gain <= 0.0F) return;

        ChannelAccess channels = channelAccess(minecraft);
        if (channels == null) return;

        int ticks = lengths.getOrDefault(id, 40) + RELEASE_MARGIN_TICKS;

        channels.createHandle(Library.Pool.STATIC).thenAccept(handle -> {
            if (handle == null) return;

            handle.execute(channel -> {
                channel.setRelative(true);
                channel.disableAttenuation();
                channel.setSelfPosition(Vec3.ZERO);
                channel.setPitch(1.0F);
                channel.setVolume(gain);
                channel.attachStaticBuffer(buffer);
                channel.play();
            });

            synchronized (playing) {
                playing.add(new Playing(handle, clientTick + ticks));
            }
        });
    }

    public static void tick() {
        synchronized (playing) {
            clientTick++;
            if (playing.isEmpty()) return;

            playing.removeIf(sound -> {
                if (sound.dueTick() > clientTick) return false;
                release(sound.handle());
                return true;
            });
        }
    }

    public static void stopAll() {
        synchronized (playing) {
            for (Playing sound : playing) {
                sound.handle().execute(channel -> channel.stop());
                release(sound.handle());
            }
            playing.clear();
        }
    }

    private static void release(ChannelAccess.ChannelHandle handle) {
        try {
            handle.execute(Channel::stop);
        } catch (Exception ignored) {
        }
    }

    private static ChannelAccess channelAccess(Minecraft minecraft) {
        try {
            var engine = ((SoundManagerAccessor) minecraft.getSoundManager()).vivet$soundEngine();
            return engine == null ? null : ((SoundEngineAccessor) engine).vivet$channelAccess();
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("No sound channel free for media", e);
            return null;
        }
    }
}
