package com.vivet.speedrun.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.vivet.speedrun.VivetSpeedrun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MediaClips {
    private static final int MAX_FRAMES = 64;

    private static final int MAX_EDGE = 256;

    private static final int DEFAULT_DELAY_MS = 100;

    private static final Path CACHE_DIR = Path.of("config", "vivet_speedrun", "clips");

    private static final String CACHE_MAGIC = "vivet-clip-1";

    private record Clip(Identifier texture, int frameWidth, int frameHeight, int columns,
                        int atlasWidth, int atlasHeight, int[] starts, int totalMs) {
    }

    private static final Clip FAILED =
            new Clip(null, 0, 0, 0, 0, 0, new int[0], 0);

    private record Pending(String id, NativeImage atlas, int frameWidth, int frameHeight,
                           int columns, int[] starts, int totalMs) {
    }

    private static final Map<String, Clip> uploaded = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> started = new ConcurrentHashMap<>();
    private static final List<Pending> pending = new CopyOnWriteArrayList<>();

    private MediaClips() {
    }

    private static boolean nothing(String id) {
        return id == null || id.isEmpty();
    }

    public static boolean isReady(String id) {
        if (nothing(id)) return false;

        Clip clip = uploaded.get(id);
        return clip != null && clip.texture() != null;
    }

    public static int lengthMs(String id) {
        if (nothing(id)) return 0;

        Clip clip = uploaded.get(id);
        return clip == null ? 0 : clip.totalMs();
    }

    public static boolean draw(GuiGraphicsExtractor graphics, String id, int x, int y,
                               int width, int height, long intoMs, int tint) {
        if (nothing(id)) return false;

        Clip clip = uploaded.get(id);
        if (clip == null || clip.texture() == null || width <= 0 || height <= 0) return false;

        int frame = frameAt(clip, intoMs);
        int column = frame % clip.columns();
        int row = frame / clip.columns();

        float aspect = clip.frameWidth() / (float) Math.max(1, clip.frameHeight());
        int drawWidth = width;
        int drawHeight = Math.round(width / aspect);
        if (drawHeight > height) {
            drawHeight = height;
            drawWidth = Math.round(height * aspect);
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, clip.texture(),
                x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                column * clip.frameWidth(), row * clip.frameHeight(),
                drawWidth, drawHeight,
                clip.frameWidth(), clip.frameHeight(),
                clip.atlasWidth(), clip.atlasHeight(),
                tint);
        return true;
    }

    private static int frameAt(Clip clip, long intoMs) {
        int[] starts = clip.starts();
        if (starts.length <= 1) return 0;
        if (intoMs >= clip.totalMs()) return starts.length - 1;

        for (int i = starts.length - 1; i >= 0; i--) {
            if (intoMs >= starts[i]) return i;
        }
        return 0;
    }

    public static void prepare(String id) {
        if (nothing(id)) return;
        if (uploaded.containsKey(id) || started.containsKey(id)) return;

        Path file = MediaCache.path(id);
        if (file == null) return;

        started.put(id, Boolean.TRUE);

        Thread worker = new Thread(() -> decode(id, file), "vivet-clip-decode");
        worker.setDaemon(true);
        worker.start();
    }

    public static void tick() {
        if (pending.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) return;

        for (Pending waiting : pending) {
            pending.remove(waiting);
            try {
                Identifier texture = Identifier.fromNamespaceAndPath(
                        VivetSpeedrun.MOD_ID, "clip/" + waiting.id().replace('.', '/'));

                minecraft.getTextureManager().register(texture,
                        new DynamicTexture(() -> "vivet clip " + waiting.id(), waiting.atlas()));

                uploaded.put(waiting.id(), new Clip(texture,
                        waiting.frameWidth(), waiting.frameHeight(), waiting.columns(),
                        waiting.atlas().getWidth(), waiting.atlas().getHeight(),
                        waiting.starts(), waiting.totalMs()));
            } catch (Exception e) {
                VivetSpeedrun.LOGGER.warn("Could not load clip {}",
                        waiting.id(), e);
                waiting.atlas().close();
                uploaded.put(waiting.id(), FAILED);
            }
        }
    }

    private static void decode(String id, Path file) {
        try {
            Path cached = cachePath(file);
            Pending fromDisk = readCache(id, cached);
            if (fromDisk != null) {
                pending.add(fromDisk);
                return;
            }

            Pending built = decodeGif(id, file);
            if (built == null) {
                uploaded.put(id, FAILED);
                return;
            }

            writeCache(cached, built);
            pending.add(built);
        } catch (Throwable e) {
            VivetSpeedrun.LOGGER.warn("Could not decode clip {}", id, e);
            uploaded.put(id, FAILED);
        }
    }

    private static Pending decodeGif(String id, Path file) throws Exception {
        List<BufferedImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
             ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                readFrames(reader, frames, delays);
            } finally {
                reader.dispose();
            }
        }

        if (frames.isEmpty()) return null;
        return pack(id, frames, delays);
    }

    private static void readFrames(ImageReader reader, List<BufferedImage> frames,
                                   List<Integer> delays) throws Exception {
        int count = reader.getNumImages(true);
        if (count <= 0) return;

        int canvasWidth = 0, canvasHeight = 0;
        try {
            IIOMetadataNode screen = descriptor(reader.getStreamMetadata(),
                    "javax_imageio_gif_stream_1.0", "LogicalScreenDescriptor");
            if (screen != null) {
                canvasWidth = intAttribute(screen, "logicalScreenWidth", 0);
                canvasHeight = intAttribute(screen, "logicalScreenHeight", 0);
            }
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Clip has no size data", e);
        }

        BufferedImage first = reader.read(0);
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            canvasWidth = first.getWidth();
            canvasHeight = first.getHeight();
        }

        BufferedImage canvas =
                new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pen = canvas.createGraphics();
        BufferedImage saved = null;

        try {
            for (int i = 0; i < count; i++) {
                BufferedImage frame = i == 0 ? first : reader.read(i);

                int left = 0, top = 0, delay = DEFAULT_DELAY_MS;
                String disposal = "none";
                try {
                    IIOMetadata meta = reader.getImageMetadata(i);
                    IIOMetadataNode image = descriptor(meta,
                            "javax_imageio_gif_image_1.0", "ImageDescriptor");
                    if (image != null) {
                        left = intAttribute(image, "imageLeftPosition", 0);
                        top = intAttribute(image, "imageTopPosition", 0);
                    }
                    IIOMetadataNode control = descriptor(meta,
                            "javax_imageio_gif_image_1.0", "GraphicControlExtension");
                    if (control != null) {
                        int hundredths = intAttribute(control, "delayTime", 0);

                        delay = hundredths <= 1 ? DEFAULT_DELAY_MS : hundredths * 10;
                        String named = control.getAttribute("disposalMethod");
                        if (named != null && !named.isEmpty()) disposal = named;
                    }
                } catch (Exception e) {
                    VivetSpeedrun.LOGGER.debug("Clip frame has no timing data", e);
                }

                if ("restoreToPrevious".equals(disposal)) saved = copyOf(canvas);

                pen.drawImage(frame, left, top, null);
                frames.add(copyOf(canvas));
                delays.add(delay);

                if ("restoreToBackgroundColor".equals(disposal)) {
                    clear(pen, left, top, frame.getWidth(), frame.getHeight());
                } else if ("restoreToPrevious".equals(disposal) && saved != null) {
                    clear(pen, 0, 0, canvasWidth, canvasHeight);
                    pen.drawImage(saved, 0, 0, null);
                }
            }
        } finally {
            pen.dispose();
        }
    }

    private static Pending pack(String id, List<BufferedImage> frames, List<Integer> delays) {
        int step = Math.max(1, (frames.size() + MAX_FRAMES - 1) / MAX_FRAMES);

        List<BufferedImage> kept = new ArrayList<>();
        List<Integer> keptDelays = new ArrayList<>();
        for (int i = 0; i < frames.size(); i += step) {
            kept.add(frames.get(i));
            int sum = 0;
            for (int j = i; j < Math.min(frames.size(), i + step); j++) sum += delays.get(j);
            keptDelays.add(sum);
        }

        int sourceWidth = kept.get(0).getWidth();
        int sourceHeight = kept.get(0).getHeight();
        float shrink = Math.min(1.0F,
                MAX_EDGE / (float) Math.max(sourceWidth, sourceHeight));
        int frameWidth = Math.max(1, Math.round(sourceWidth * shrink));
        int frameHeight = Math.max(1, Math.round(sourceHeight * shrink));

        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(kept.size())));
        int rows = (kept.size() + columns - 1) / columns;

        NativeImage atlas = new NativeImage(columns * frameWidth, rows * frameHeight, true);
        for (int i = 0; i < kept.size(); i++) {
            BufferedImage source = kept.get(i);
            int originX = (i % columns) * frameWidth;
            int originY = (i / columns) * frameHeight;

            for (int py = 0; py < frameHeight; py++) {
                int sy = Math.min(sourceHeight - 1, Math.round(py / shrink));
                for (int px = 0; px < frameWidth; px++) {
                    int sx = Math.min(sourceWidth - 1, Math.round(px / shrink));

                    atlas.setPixel(originX + px, originY + py, source.getRGB(sx, sy));
                }
            }
        }

        int[] starts = new int[kept.size()];
        int running = 0;
        for (int i = 0; i < kept.size(); i++) {
            starts[i] = running;
            running += keptDelays.get(i);
        }

        VivetSpeedrun.LOGGER.info("Clip {}: {} frames, {}x{}, {}ms",
                id, kept.size(), frameWidth, frameHeight, running);

        return new Pending(id, atlas, frameWidth, frameHeight, columns, starts, running);
    }

    private static BufferedImage copyOf(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D pen = copy.createGraphics();
        pen.drawImage(source, 0, 0, null);
        pen.dispose();
        return copy;
    }

    private static void clear(Graphics2D pen, int x, int y, int width, int height) {
        java.awt.Composite was = pen.getComposite();
        pen.setComposite(java.awt.AlphaComposite.Clear);
        pen.fillRect(x, y, width, height);
        pen.setComposite(was);
    }

    private static IIOMetadataNode descriptor(IIOMetadata meta, String format, String name) {
        if (meta == null) return null;
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
        if (root == null) return null;

        var found = root.getElementsByTagName(name);
        return found.getLength() == 0 ? null : (IIOMetadataNode) found.item(0);
    }

    private static int intAttribute(IIOMetadataNode node, String name, int fallback) {
        String raw = node.getAttribute(name);
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Path cachePath(Path source) {
        return CACHE_DIR.resolve(source.getFileName().toString());
    }

    private static Pending readCache(String id, Path base) {
        Path atlasFile = Path.of(base.toString() + ".png");
        Path sidecar = Path.of(base.toString() + ".txt");
        if (!Files.isReadable(atlasFile) || !Files.isReadable(sidecar)) return null;

        try {
            List<String> lines = Files.readAllLines(sidecar, StandardCharsets.UTF_8);
            if (lines.size() < 3 || !CACHE_MAGIC.equals(lines.get(0))) return null;

            String[] dims = lines.get(1).split(" ");
            int frameWidth = Integer.parseInt(dims[0]);
            int frameHeight = Integer.parseInt(dims[1]);
            int columns = Integer.parseInt(dims[2]);
            int totalMs = Integer.parseInt(dims[3]);

            String[] parts = lines.get(2).split(",");
            int[] starts = new int[parts.length];
            for (int i = 0; i < parts.length; i++) starts[i] = Integer.parseInt(parts[i]);

            NativeImage atlas;
            try (InputStream in = Files.newInputStream(atlasFile)) {
                atlas = NativeImage.read(in);
            }
            return new Pending(id, atlas, frameWidth, frameHeight, columns, starts, totalMs);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Clip cache unreadable, ignoring it", e);
            return null;
        }
    }

    private static void writeCache(Path base, Pending built) {
        try {
            Files.createDirectories(base.getParent());

            built.atlas().writeToFile(Path.of(base.toString() + ".png"));

            StringBuilder starts = new StringBuilder();
            for (int i = 0; i < built.starts().length; i++) {
                if (i > 0) starts.append(',');
                starts.append(built.starts()[i]);
            }
            String sidecar = CACHE_MAGIC + "\n"
                    + built.frameWidth() + " " + built.frameHeight() + " "
                    + built.columns() + " " + built.totalMs() + "\n"
                    + starts + "\n";
            Files.writeString(Path.of(base.toString() + ".txt"), sidecar, StandardCharsets.UTF_8);
        } catch (Exception e) {
            VivetSpeedrun.LOGGER.debug("Could not cache clip", e);
        }
    }
}
