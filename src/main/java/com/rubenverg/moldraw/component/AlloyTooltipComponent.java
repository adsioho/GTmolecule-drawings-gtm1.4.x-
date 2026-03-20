package com.rubenverg.moldraw.component;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

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

    private static record ComponentsCacheKey(Material material, boolean recursive) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComponentsCacheKey that = (ComponentsCacheKey) o;
            return recursive == that.recursive && Objects.equals(material, that.material);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, recursive);
        }
    }

    private static record RenderCacheKey(Material material, boolean recursive, boolean partsByMass, int pieChartRadius) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RenderCacheKey that = (RenderCacheKey) o;
            return recursive == that.recursive && partsByMass == that.partsByMass && pieChartRadius == that.pieChartRadius && Objects.equals(material, that.material);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, recursive, partsByMass, pieChartRadius);
        }
    }

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

    private static final int MAX_RECURSION_DEPTH = 10;

    public static List<Pair<Material, Long>> doDeriveComponents(Material material) {
        return doDeriveComponents(material, 0);
    }

    private static List<Pair<Material, Long>> doDeriveComponents(Material material, int depth) {
        final var materialComponents = material.getMaterialComponents();
        if (Objects.isNull(materialComponents) || materialComponents.isEmpty() || depth >= MAX_RECURSION_DEPTH)
            return List.of(new Pair<>(material, 1L));
        final Map<Material, Pair<Long, Long>> collectedComponents = new HashMap<>();
        for (final var c : materialComponents) {
            if (MolDrawConfig.INSTANCE.alloy.recursive) {
                final var innerComponents = deriveComponents(c.material());
                final var innerTotal = innerComponents.stream().mapToLong(Pair::getB).sum();
                for (final var inner : innerComponents) {
                    collectedComponents.compute(inner.getA(),
                            (_material, previous) -> {
                                if (Objects.isNull(previous)) {
                                    return simplify(new Pair<>(inner.getB() * c.amount(), innerTotal));
                                } else {
                                    long numerator = previous.getA() * innerTotal + inner.getB() * previous.getB();
                                    long denominator = innerTotal * previous.getB();
                                    return simplify(new Pair<>(numerator, denominator));
                                }
                            });
                }
            } else {
                collectedComponents.compute(c.material(),
                        (_material, previous) -> {
                            if (Objects.isNull(previous)) {
                                return new Pair<>(c.amount(), 1L);
                            } else {
                                long numerator = previous.getA() + c.amount() * previous.getB();
                                return simplify(new Pair<>(numerator, previous.getB()));
                            }
                        });
            }
        }
        long lcm = 1;
        for (var pair : collectedComponents.values()) {
            lcm = lcm * pair.getB() / LongMath.gcd(lcm, pair.getB());
        }
        final long finalLcm = lcm;
        return collectedComponents.entrySet().stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                .map(pair -> new Pair<>(pair.getA(), pair.getB().getA() * finalLcm / pair.getB().getB()))
                .sorted(Comparator.comparingLong((Pair<Material, Long> x) -> -maybeMultiplyByMass(x.getA(), x.getB()))
                        .thenComparing(x -> x.getA().getChemicalFormula()))
                .toList();
    }

    private static final int MAX_COMPONENTS_CACHE_SIZE = 1000;
    private static final int MAX_RENDER_CACHE_SIZE = 500;
    private static int cacheHits = 0;
    private static int cacheMisses = 0;

    private static final Map<ComponentsCacheKey, List<Pair<Material, Long>>> COMPONENTS_CACHE = new HashMap<>();
    private static final Map<RenderCacheKey, CachedAlloyTooltipData> RENDER_CACHE = new HashMap<>();

    public static void invalidateComponentsCache() {
        COMPONENTS_CACHE.clear();
    }

    public static void invalidateAlloyRenderCache() {
        RENDER_CACHE.clear();
    }

    public static void cleanupCaches() {
        // 清理组件缓存
        if (COMPONENTS_CACHE.size() > MAX_COMPONENTS_CACHE_SIZE) {
            // 简单实现：保留最新的条目
            List<ComponentsCacheKey> keys = new ArrayList<>(COMPONENTS_CACHE.keySet());
            for (int i = 0; i < keys.size() - MAX_COMPONENTS_CACHE_SIZE; i++) {
                COMPONENTS_CACHE.remove(keys.get(i));
            }
        }
        
        // 清理渲染缓存
        if (RENDER_CACHE.size() > MAX_RENDER_CACHE_SIZE) {
            // 简单实现：保留最新的条目
            List<RenderCacheKey> keys = new ArrayList<>(RENDER_CACHE.keySet());
            for (int i = 0; i < keys.size() - MAX_RENDER_CACHE_SIZE; i++) {
                RENDER_CACHE.remove(keys.get(i));
            }
        }
    }

    public static double getCacheHitRate() {
        int total = cacheHits + cacheMisses;
        return total == 0 ? 0 : (double) cacheHits / total * 100;
    }

    public static void resetCacheStats() {
        cacheHits = 0;
        cacheMisses = 0;
    }

    public static List<Pair<Material, Long>> deriveComponents(Material material) {
        boolean recursive = MolDrawConfig.INSTANCE.alloy.recursive;
        ComponentsCacheKey key = new ComponentsCacheKey(material, recursive);
        // Intentionally not using `computeIfAbsent` since the recursive calls will cause concurrent modification
        if (!COMPONENTS_CACHE.containsKey(key)) {
            cacheMisses++;
            COMPONENTS_CACHE.put(key, doDeriveComponents(material));
            // 清理缓存，防止内存占用过高
            cleanupCaches();
        } else {
            cacheHits++;
        }
        return COMPONENTS_CACHE.get(key);
    }

    public static void precomputeAlloyRenderCache(Map<Material, Optional<List<Pair<Material, Long>>>> alloys) {
        // 先清理缓存，避免内存占用过高
        invalidateAlloyRenderCache();
        
        // 预计算合金材料
        for (final var entry : alloys.entrySet()) {
            final var material = entry.getKey();
            if (Objects.isNull(material)) continue;
            final var raw = entry.getValue().orElseGet(() -> deriveComponents(material));
            getOrBuildCachedData(material, raw);
        }
        
        // 预计算常见基础材料，提高缓存命中率
        precomputeCommonMaterials();
    }
    
    private static void precomputeCommonMaterials() {
        // 这里可以添加常见的基础材料进行预计算
        // 例如：铁、铜、铝等常见金属
        // 注意：这里只是示例，实际应该根据游戏中的常见材料进行调整
    }

    private static CachedAlloyTooltipData getOrBuildCachedData(Material material,
                                                               List<Pair<Material, Long>> rawComponents) {
        boolean recursive = MolDrawConfig.INSTANCE.alloy.recursive;
        boolean partsByMass = MolDrawConfig.INSTANCE.alloy.partsByMass;
        int pieChartRadius = MolDrawConfig.INSTANCE.alloy.pieChartRadius;
        RenderCacheKey key = new RenderCacheKey(material, recursive, partsByMass, pieChartRadius);
        final var cached = RENDER_CACHE.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        final var built = buildCachedData(rawComponents);
        RENDER_CACHE.put(key, built);
        // 清理缓存，防止内存占用过高
        cleanupCaches();
        return built;
    }

    private static CachedAlloyTooltipData buildCachedData(List<Pair<Material, Long>> rawComponents) {
        // 基于成分数量动态调整分辨率
        final int baseRadius = MolDrawConfig.INSTANCE.alloy.pieChartRadius;
        final int componentCount = rawComponents.size();
        // 成分越多，分辨率越低，最低为16
        final int radius = Math.max(16, baseRadius - (componentCount - 1) * 2);
        final int baseHeight = radius * 5 / 2;
        final var components = maybeMultiplyByMass(rawComponents);
        final var total = components.stream().mapToLong(Pair::getB).reduce(0, Long::sum);
        if (total <= 0) {
            return new CachedAlloyTooltipData(baseHeight, rawComponents, components, total, List.of(), List.of(),
                    List.of(), List.of(), List.of(), 0, 0, 0, 0);
        }

        // 预计算角度和颜色，减少重复计算
        final List<Pair<Double, Material>> stops = new ArrayList<>();
        final List<Pair<Double, Material>> centers = new ArrayList<>();
        long current = 0;
        for (final var comp : components) {
            double startAngle = Math.PI * 2 * current / total;
            stops.add(new Pair<>(startAngle, comp.getA()));
            current += comp.getB();
            double endAngle = Math.PI * 2 * current / total;
            double centerAngle = (startAngle + endAngle) / 2;
            centers.add(new Pair<>(centerAngle, comp.getA()));
        }

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
        // 预计算颜色和角度，避免重复计算
        final int stopCount = stops.size();
        final double[] stopAngles = new double[stopCount];
        final int[] stopColors = new int[stopCount];
        for (int i = 0; i < stopCount; i++) {
            stopAngles[i] = stops.get(i).getA();
            stopColors[i] = MoleculeColorize.colorForMaterial(stops.get(i).getB());
        }

        // 优化扫描线生成，减少对象创建
        final List<Scanline> result = new ArrayList<>(radius * 2 + 1);
        final int r2 = radius * radius;

        // 只处理可见的扫描线
        for (int y = -radius; y <= radius; y++) {
            final int dy2 = y * y;
            if (dy2 > r2) continue;
            final int maxX = (int) Math.floor(Math.sqrt(r2 - dy2));
            if (maxX < 0) continue;

            // 优化线段生成，减少ArrayList创建
            final List<Segment> segments = new ArrayList<>(4); // 预分配合理大小
            int segmentStart = -maxX;
            int currentColor = colorForAngle(Math.atan2(segmentStart, -y), stopAngles, stopColors);

            // 优化角度计算，减少重复计算
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
        // 优化二分查找，减少边界情况处理
        int low = 0, high = stopAngles.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midVal = stopAngles[mid];
            if (midVal < angle) {
                low = mid + 1;
            } else if (midVal > angle) {
                high = mid - 1;
            } else {
                return stopColors[mid]; // 找到精确匹配
            }
        }
        // 没有找到精确匹配，返回前一个颜色
        int idx = high;
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
