package com.rubenverg.moldraw.component;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import com.google.common.collect.Streams;
import com.google.common.math.LongMath;
import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.MoleculeColorize;
import org.joml.Vector2i;
import oshi.util.tuples.Pair;

import java.util.*;
import java.util.function.IntBinaryOperator;

import javax.annotation.ParametersAreNonnullByDefault;

public record AlloyTooltipComponent(Material material, List<Pair<Material, Long>> rawComponents)
        implements TooltipComponent {

    private static long maybeMultiplyByMass(Material material, long count) {
        if (MolDrawConfig.INSTANCE.alloy.partsByMass) return count * material.getMass();
        return count;
    }

    private static List<Pair<Material, Long>> maybeMultiplyByMass(List<Pair<Material, Long>> rawComponents) {
        return rawComponents.stream()
                .map(pair -> new Pair<>(pair.getA(), maybeMultiplyByMass(pair.getA(), pair.getB()))).toList();
    }

    private static Pair<Long, Long> simplify(Pair<Long, Long> frac) {
        final var gcd = LongMath.gcd(frac.getA(), frac.getB());
        return new Pair<>(frac.getA() / gcd, frac.getB() / gcd);
    }

    public static List<Pair<Material, Long>> doDeriveComponents(Material material) {
        final var materialComponents = material.getMaterialComponents();
        if (Objects.isNull(materialComponents) || materialComponents.isEmpty())
            return List.of(new Pair<>(material, 1L));
        final Map<Material, Pair<Long, Long>> collectedComponents = new HashMap<>();
        for (final var c : materialComponents) {
            if (MolDrawConfig.INSTANCE.alloy.recursive) {
                final var innerComponents = deriveComponents(c.material());
                final var innerTotal = innerComponents.stream().map(Pair::getB).reduce(0L, Long::sum);
                for (final var inner : innerComponents) {
                    collectedComponents.compute(inner.getA(),
                            (_material, previous) -> Objects.isNull(previous) ?
                                    simplify(new Pair<>(inner.getB() * c.amount(), innerTotal)) :
                                    simplify(new Pair<>(previous.getA() * innerTotal + inner.getB() * previous.getB(),
                                            innerTotal * previous.getB())));
                }
            } else collectedComponents.compute(c.material(),
                    (_material, previous) -> Objects.isNull(previous) ? new Pair<>(c.amount(), 1L) :
                            simplify(new Pair<>(previous.getA() + c.amount() * previous.getB(), previous.getB())));
        }
        final var lcm = collectedComponents.values().stream().map(Pair::getB).reduce(1L,
                (a, b) -> a * b / LongMath.gcd(a, b));
        return collectedComponents.entrySet().stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                .map(pair -> new Pair<>(pair.getA(), pair.getB().getA() * lcm / pair.getB().getB()))
                .sorted(Comparator.comparingLong((Pair<Material, Long> x) -> -maybeMultiplyByMass(x.getA(), x.getB()))
                        .thenComparing(x -> x.getA().getChemicalFormula()))
                .toList();
    }

    private static final Map<Material, List<Pair<Material, Long>>> COMPONENTS_CACHE = new HashMap<>();
    private static final Map<Material, CachedAlloyTooltipData> RENDER_CACHE = new HashMap<>();

    public static void invalidateComponentsCache() {
        COMPONENTS_CACHE.clear();
    }

    public static void invalidateAlloyRenderCache() {
        RENDER_CACHE.clear();
    }

    public static List<Pair<Material, Long>> deriveComponents(Material material) {
        // Intentionally not using `computeIfAbsent` since the recursive calls will cause concurrent modification
        if (!COMPONENTS_CACHE.containsKey(material)) {
            COMPONENTS_CACHE.put(material, doDeriveComponents(material));
        }
        return COMPONENTS_CACHE.get(material);
    }

    public static void precomputeAlloyRenderCache(Map<Material, Optional<List<Pair<Material, Long>>>> alloys) {
        invalidateAlloyRenderCache();
        for (final var entry : alloys.entrySet()) {
            final var material = entry.getKey();
            if (Objects.isNull(material)) continue;
            final var raw = entry.getValue().orElseGet(() -> deriveComponents(material));
            getOrBuildCachedData(material, raw);
        }
    }

    private static CachedAlloyTooltipData getOrBuildCachedData(Material material,
                                                               List<Pair<Material, Long>> rawComponents) {
        final var cached = RENDER_CACHE.get(material);
        if (cached != null) return cached;
        final var built = buildCachedData(rawComponents);
        RENDER_CACHE.put(material, built);
        return built;
    }

    private static CachedAlloyTooltipData buildCachedData(List<Pair<Material, Long>> rawComponents) {
        final int radius = MolDrawConfig.INSTANCE.alloy.pieChartRadius;
        final int baseHeight = radius * 5 / 2;
        final var components = maybeMultiplyByMass(rawComponents);
        final var total = components.stream().mapToLong(Pair::getB).reduce(0, Long::sum);
        if (total <= 0) {
            return new CachedAlloyTooltipData(baseHeight, rawComponents, components, total, List.of(), List.of(),
                    List.of(), List.of(), List.of(), 0, 0, 0, 0);
        }

        final List<Pair<Long, Material>> s = new ArrayList<>();
        final List<Pair<Long, Material>> c = new ArrayList<>();
        var current = 0L;
        for (final var comp : components) {
            s.add(new Pair<>(current, comp.getA()));
            c.add(new Pair<>(current, comp.getA()));
            current += comp.getB();
        }
        final var stops = s.stream().map(pair -> new Pair<>(Math.PI * 2 * pair.getA() / total, pair.getB())).toList();

        c.remove(0);
        c.add(new Pair<>(total, GTMaterials.NULL));
        final var centers = Streams
                .zip(s.stream(), c.stream(),
                        (begin, end) -> new Pair<>(Math.PI *
                                (Objects.requireNonNull(begin).getA() + Objects.requireNonNull(end).getA()) / total,
                                begin.getB()))
                .toList();

        final var font = Minecraft.getInstance().font;
        final List<Pair<Vector2i, Material>> ts = new ArrayList<>();
        final List<Component> textComponents = new ArrayList<>();
        int atMostY = Integer.MAX_VALUE, atLeastY = Integer.MIN_VALUE;
        int al = 0, ar = 0;
        for (int i = 0; i < components.size(); i++) {
            final var count = components.get(i).getB();
            final var material = components.get(i).getA();
            final var center = centers.get(i).getA();
            final int cy = (int) (-Math.cos(center) * 0.9 * radius);
            final var left = center > Math.PI;
            final var ex = (left ? -1 : 1) * (radius + 10);
            final var percentage = count * 100d / total;
            final var percentageString = percentage < 0.1 ? "<0.1%" : "%.1f%%".formatted(percentage);
            final var text = Component.literal(percentageString + " ")
                    .append(MoleculeColorize.coloredFormula(new MaterialStack(material, 1), true));
            final var width = font.width(text);

            if (left) {
                final var topY = Math.min(cy - font.lineHeight / 2, atMostY);
                ts.add(new Pair<>(new Vector2i(ex - 5 - width, topY), material));
                atMostY = topY - font.lineHeight - 1;
                al = Math.max(al, width);
            } else {
                final var topY = Math.max(cy - font.lineHeight / 2, atLeastY);
                ts.add(new Pair<>(new Vector2i(ex + 5, topY), material));
                atLeastY = topY + font.lineHeight + 1;
                ar = Math.max(ar, width);
            }
            textComponents.add(text);
        }
        final int addTop = Math.max(0, -atMostY - baseHeight / 2);
        final int addBottom = Math.max(0, atLeastY - baseHeight / 2);
        final int addLeft = Math.max(0, al + radius + 20 - ClientAlloyTooltipComponent.BASE_WIDTH / 2);
        final int addRight = Math.max(0, ar + radius + 20 - ClientAlloyTooltipComponent.BASE_WIDTH / 2);

        final var pieScanlines = buildPieScanlines(stops, total, radius);
        return new CachedAlloyTooltipData(baseHeight, rawComponents, components, total, stops, centers, ts,
                textComponents, pieScanlines, addTop, addBottom, addLeft, addRight);
    }

    private static List<Scanline> buildPieScanlines(List<Pair<Double, Material>> stops, long total, int radius) {
        if (radius <= 0 || total <= 0 || stops.isEmpty()) return List.of();
        final var stopAngles = new double[stops.size()];
        final var stopColors = new int[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            stopAngles[i] = stops.get(i).getA();
            stopColors[i] = MoleculeColorize.colorForMaterial(stops.get(i).getB());
        }
        final var result = new ArrayList<Scanline>();
        final int r2 = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            final int dy2 = y * y;
            if (dy2 > r2) continue;
            final int maxX = (int) Math.floor(Math.sqrt(r2 - dy2));
            if (maxX < 0) continue;
            final var segments = new ArrayList<Segment>();
            int segmentStart = -maxX;
            int currentColor = colorForAngle(Math.atan2(segmentStart, -y), stopAngles, stopColors);
            for (int x = -maxX + 1; x <= maxX; x++) {
                final int color = colorForAngle(Math.atan2(x, -y), stopAngles, stopColors);
                if (color != currentColor) {
                    segments.add(new Segment(segmentStart, x - 1, currentColor));
                    segmentStart = x;
                    currentColor = color;
                }
            }
            segments.add(new Segment(segmentStart, maxX, currentColor));
            result.add(new Scanline(y, segments));
        }
        return result;
    }

    private static int colorForAngle(double angle, double[] stopAngles, int[] stopColors) {
        if (angle < 0) angle += 2 * Math.PI;
        int idx = Arrays.binarySearch(stopAngles, angle);
        if (idx < 0) idx = -idx - 2;
        if (idx < 0) idx = stopColors.length - 1;
        return stopColors[idx];
    }

    private record Segment(int startX, int endX, int color) {}

    private record Scanline(int y, List<Segment> segments) {}

    private record CachedAlloyTooltipData(int baseHeight, List<Pair<Material, Long>> rawComponents,
                                          List<Pair<Material, Long>> components, long total,
                                          List<Pair<Double, Material>> stops, List<Pair<Double, Material>> centers,
                                          List<Pair<Vector2i, Material>> textStarts, List<Component> textComponents,
                                          List<Scanline> pieScanlines, int addTop, int addBottom, int addLeft,
                                          int addRight) {}

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class ClientAlloyTooltipComponent implements ClientTooltipComponent {

        public final int baseHeight;
        public static final int BASE_WIDTH = 200;

        public final List<Pair<Material, Long>> rawComponents;
        public final List<Pair<Material, Long>> components;
        public final long total;
        public final List<Pair<Double, Material>> stops;
        public final List<Pair<Double, Material>> centers;
        public final List<Pair<Vector2i, Material>> textStarts;
        public final List<Component> textComponents;

        private final List<Scanline> pieScanlines;
        private final int addTop, addBottom;
        private final int addLeft, addRight;

        @SuppressWarnings("UnstableApiUsage")
        public ClientAlloyTooltipComponent(AlloyTooltipComponent component) {
            final var cached = Objects.isNull(component.material) ?
                    buildCachedData(component.rawComponents) :
                    getOrBuildCachedData(component.material, component.rawComponents);

            baseHeight = cached.baseHeight;
            rawComponents = cached.rawComponents;
            components = cached.components;
            total = cached.total;
            stops = cached.stops;
            centers = cached.centers;
            textStarts = cached.textStarts;
            textComponents = cached.textComponents;
            pieScanlines = cached.pieScanlines;
            addTop = cached.addTop;
            addBottom = cached.addBottom;
            addLeft = cached.addLeft;
            addRight = cached.addRight;
        }

        @Override
        public int getHeight() {
            return baseHeight + addBottom + addTop;
        }

        @Override
        public int getWidth(Font font) {
            return BASE_WIDTH + addLeft + addRight;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
            final int xm = BASE_WIDTH / 2 + addLeft + x, ym = baseHeight / 2 + addTop + y;

            for (final var scanline : pieScanlines) {
                final int yPos = ym + scanline.y;
                for (final var segment : scanline.segments) {
                    guiGraphics.fill(xm + segment.startX, yPos, xm + segment.endX + 1, yPos + 1, segment.color);
                }
            }

            final IntBinaryOperator white = (_xp, _yp) -> 0xffffffff;

            for (int i = 0; i < components.size(); i++) {
                final var center = centers.get(i).getA();
                final var textStart = textStarts.get(i).getA();
                final var topY = ym + textStart.y;
                final var centerY = topY + font.lineHeight / 2;
                final var startX = xm + textStart.x;
                final var cx = xm + (int) (Math.sin(center) * 0.9 * MolDrawConfig.INSTANCE.alloy.pieChartRadius);
                final var cy = ym - (int) (Math.cos(center) * 0.9 * MolDrawConfig.INSTANCE.alloy.pieChartRadius);
                final var left = center > Math.PI;
                final var ex = xm + (left ? -1 : 1) * (MolDrawConfig.INSTANCE.alloy.pieChartRadius + 10);

                GraphicalUtils.plotLine(cx, cy, cx, centerY, GraphicalUtils::alwaysDraw, white, guiGraphics);
                GraphicalUtils.plotLine(cx, centerY, ex, centerY, GraphicalUtils::alwaysDraw, white, guiGraphics);
                guiGraphics.drawString(font, textComponents.get(i), startX, topY, 0xffffffff);
            }
        }
    }
}
