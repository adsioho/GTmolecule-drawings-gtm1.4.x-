package com.rubenverg.moldraw;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BucketItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.adsioho.gtm.compat.MaterialHelper;
import com.google.common.hash.HashCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.rubenverg.moldraw.component.AlloyTooltipComponent;
import com.rubenverg.moldraw.component.MoleculeTooltipComponent;
import com.rubenverg.moldraw.data.AlloysData;
import com.rubenverg.moldraw.data.MoleculesData;
import com.rubenverg.moldraw.molecule.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod(MolDraw.MOD_ID)
@SuppressWarnings("removal")
public class MolDraw {

    public static final String MOD_ID = "moldraw";
    public static final Logger LOGGER = LogManager.getLogger();

    public MolDraw() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

            modEventBus.addListener(this::modConstruct);
            modEventBus.addListener(this::gatherData);
            modEventBus.addListener(this::registerClientTooltipComponents);
            modEventBus.addListener(this::registerClientReloadListeners);

            MinecraftForge.EVENT_BUS.addListener(this::tooltipGatherComponents);
        });
    }

    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Molecule.class, Molecule.Json.INSTANCE)
            .registerTypeAdapter(Element.class, Element.Json.INSTANCE)
            .registerTypeAdapter(Element.Color.class, Element.Color.Json.INSTANCE)
            .registerTypeAdapter(Element.Counted.class, Element.Counted.Json.INSTANCE)
            .registerTypeAdapter(Atom.class, Atom.Json.INSTANCE)
            .registerTypeAdapter(Bond.class, Bond.Json.INSTANCE)
            .registerTypeAdapter(Bond.Line.class, Bond.Line.Json.INSTANCE)
            .registerTypeAdapter(Parens.class, Parens.Json.INSTANCE)
            .registerTypeAdapter(CircleTransformation.class, CircleTransformation.Json.INSTANCE)
            .setPrettyPrinting()
            .create();

    public void modConstruct(FMLConstructModEvent event) {
        event.enqueueWork(MolDrawConfig::init);
    }

    public void gatherData(GatherDataEvent event) {
        final var gen = event.getGenerator();

        gen.addProvider(event.includeClient(), new DataProvider.Factory<>() {

            @Override
            public @NotNull DataProvider create(@NotNull PackOutput output) {
                final var moleculesPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK,
                        "molecules");
                final var alloysPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "alloys");
                return new DataProvider() {

                    @Override
                    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cachedOutput) {
                        for (final var entry : MoleculesData.molecules().entrySet()) {
                            final var json = gson.toJson(entry.getValue(), Molecule.class);
                            try {
                                cachedOutput.writeIfNeeded(moleculesPathProvider.json(entry.getKey()),
                                        json.getBytes(StandardCharsets.UTF_8), HashCode.fromInt(json.hashCode()));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        for (final var entry : AlloysData.alloys().entrySet()) {
                            final var json = gson.toJson(AlloysData.write(entry.getValue()));
                            try {
                                cachedOutput.writeIfNeeded(alloysPathProvider.json(entry.getKey()),
                                        json.getBytes(StandardCharsets.UTF_8), HashCode.fromInt(json.hashCode()));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public @NotNull String getName() {
                        return "Molecules provider";
                    }
                };
            }
        });
    }

    public void registerClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(MoleculeTooltipComponent.class, MoleculeTooltipComponent.ClientMoleculeTooltipComponent::new);
        event.register(AlloyTooltipComponent.class, AlloyTooltipComponent.ClientAlloyTooltipComponent::new);
    }

    private static final Map<Material, Molecule> molecules = new HashMap<>();
    private static final Map<Material, Optional<List<Pair<Material, Long>>>> alloys = new HashMap<>();

    public void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Unit>() {

            @MethodsReturnNonnullByDefault
            @ParametersAreNonnullByDefault
            @Override
            protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                return Unit.INSTANCE;
            }

            @MethodsReturnNonnullByDefault
            @ParametersAreNonnullByDefault
            @Override
            protected void apply(Unit unit, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                MoleculeColorize.invalidateColorCache();
            }
        });

        event.registerReloadListener(

                new SimplePreparableReloadListener<Map<Material, Molecule>>() {

                    @MethodsReturnNonnullByDefault
                    @ParametersAreNonnullByDefault
                    @Override
                    protected Map<Material, Molecule> prepare(ResourceManager resourceManager,
                                                              ProfilerFiller profilerFiller) {
                        final Map<Material, Molecule> molecules = new HashMap<>();
                        for (final var id : resourceManager
                                .listResources("molecules", path -> path.toString().endsWith(".json")).keySet()) {
                            try (final var stream = resourceManager.open(id)) {
                                final var file = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                final var material = GTCEuAPI.materialManager
                                        .getMaterial(id.toString().replace(".json", "").replace("molecules/", ""));
                                if (Objects.isNull(material)) {
                                    continue;
                                }
                                final var molecule = gson.fromJson(file, Molecule.class);
                                molecules.put(material, molecule);
                            } catch (IOException | JsonSyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return molecules;
                    }

                    @MethodsReturnNonnullByDefault
                    @ParametersAreNonnullByDefault
                    @Override
                    protected void apply(Map<Material, Molecule> prepareResult, ResourceManager resourceManager,
                                         ProfilerFiller profilerFiller) {
                        molecules.clear();
                        molecules.putAll(prepareResult);

                        // 调试信息：打印加载的分子
                        if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                            MolDraw.LOGGER.info("=== Loaded Molecules ===");
                            MolDraw.LOGGER.info("Total loaded: {}", prepareResult.size());
                            for (Map.Entry<Material, Molecule> entry : prepareResult.entrySet()) {
                                MolDraw.LOGGER.info("  {} -> {}",
                                        entry.getKey().getName(),
                                        entry.getValue() != null ? "Molecule loaded" : "null");
                            }
                        }
                    }
                });
        event.registerReloadListener(
                new SimplePreparableReloadListener<Map<Material, Optional<List<Pair<Material, Long>>>>>() {

                    @MethodsReturnNonnullByDefault
                    @ParametersAreNonnullByDefault
                    @Override
                    protected Map<Material, Optional<List<Pair<Material, Long>>>> prepare(ResourceManager resourceManager,
                                                                                          ProfilerFiller profilerFiller) {
                        final Map<Material, Optional<List<Pair<Material, Long>>>> alloys = new HashMap<>();
                        for (final var id : resourceManager
                                .listResources("alloys", path -> path.toString().endsWith(".json")).keySet()) {
                            try (final var stream = resourceManager.open(id)) {
                                final var file = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                final var material = GTCEuAPI.materialManager
                                        .getMaterial(id.toString().replace(".json", "").replace("alloys/", ""));
                                if (Objects.isNull(material)) {
                                    continue;
                                }
                                final var alloy = AlloysData.read(gson.fromJson(file, JsonElement.class));
                                if (alloy.isEmpty()) {
                                    alloys.put(material, Optional.empty());
                                } else {
                                    alloys.put(material, Optional.of(alloy.get().stream().map(pair -> {
                                        final var subMat = GTCEuAPI.materialManager.getMaterial(pair.getA().toString());
                                        if (Objects.isNull(subMat) || MaterialHelper.isNull(subMat))
                                            throw new RuntimeException(
                                                    "Alloy JSON contains a material that doesn't exist");
                                        return new Pair<>(subMat, pair.getB());
                                    }).toList()));
                                }
                            } catch (IOException | JsonSyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return alloys;
                    }

                    @MethodsReturnNonnullByDefault
                    @ParametersAreNonnullByDefault
                    @Override
                    protected void apply(Map<Material, Optional<List<Pair<Material, Long>>>> prepareResult,
                                         ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                        alloys.clear();
                        alloys.putAll(prepareResult);

                        // 调试信息：打印加载的合金
                        if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                            MolDraw.LOGGER.info("=== Loaded Alloys ===");
                            MolDraw.LOGGER.info("Total loaded: {}", prepareResult.size());
                            for (Map.Entry<Material, Optional<List<Pair<Material, Long>>>> entry : prepareResult
                                    .entrySet()) {
                                MolDraw.LOGGER.info("  {} -> {}",
                                        entry.getKey().getName(),
                                        entry.getValue().isPresent() ? "Alloy loaded" : "empty");
                            }
                        }
                    }
                });
    }

    @Nullable
    private static ResourceLocation resolveMaterialId(Material material) {
        if (material == null) {
            return null;
        }

        Object nameObj = material.getName();
        if (nameObj instanceof ResourceLocation) {
            return (ResourceLocation) nameObj;
        }

        if (nameObj != null) {
            String s = nameObj.toString();
            try {
                return new ResourceLocation(s);
            } catch (Exception ignored) {}
        }

        try {
            Class<?> cls = material.getClass();
            try {
                Field infoField = cls.getDeclaredField("materialInfo");
                infoField.setAccessible(true);
                Object info = infoField.get(material);
                if (info != null) {
                    Class<?> infoCls = info.getClass();
                    Field rlField;
                    try {
                        rlField = infoCls.getDeclaredField("resourceLocation");
                    } catch (NoSuchFieldException e) {
                        rlField = infoCls.getDeclaredField("id");
                    }
                    rlField.setAccessible(true);
                    Object val = rlField.get(info);
                    if (val instanceof ResourceLocation) {
                        return (ResourceLocation) val;
                    }
                    if (val != null) {
                        try {
                            return new ResourceLocation(val.toString());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (NoSuchFieldException ignored) {}
        } catch (Exception e) {
            if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                LOGGER.debug("Failed to resolve material id for {}", material, e);
            }
        }

        try {
            String s = material.toString();
            if (s != null && !s.isEmpty()) {
                return new ResourceLocation(s);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static @Nullable Molecule getMolecule(Material material) {
        if (material == null) {
            return null;
        }

        if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
            LOGGER.info("getMolecule: material={}, nameObj={}, class={}",
                    material,
                    material.getName(),
                    material.getClass().getName());
        }

        Molecule molecule = molecules.get(material);
        if (molecule != null) {
            if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                LOGGER.info("getMolecule: found molecule in cache for {}", material);
            }
            return molecule;
        }

        ResourceLocation materialId = resolveMaterialId(material);
        if (materialId == null) {
            if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                LOGGER.info("getMolecule: resolveMaterialId returned null for {}", material);
            }
            return null;
        }

        if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
            LOGGER.info("getMolecule: resolved materialId={} for {}", materialId, material);
        }

        String name = materialId.toString();
        Material canonical = GTCEuAPI.materialManager.getMaterial(name);
        if (canonical != null) {
            Molecule canonicalMolecule = molecules.get(canonical);
            if (canonicalMolecule != null) {
                if (canonical != material) {
                    molecules.put(material, canonicalMolecule);
                }
                if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                    LOGGER.info("getMolecule: using canonical material {} for {}", canonical, material);
                }
                return canonicalMolecule;
            }
        }

        try {
            ResourceLocation resourceId = new ResourceLocation(materialId.getNamespace(),
                    "molecules/" + materialId.getPath() + ".json");
            if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                LOGGER.info("getMolecule: trying resourceId={}", resourceId);
            }
            var resourceOpt = Minecraft.getInstance().getResourceManager().getResource(resourceId);
            if (resourceOpt.isPresent()) {
                if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                    LOGGER.info("getMolecule: resource {} is present", resourceId);
                }
                try (var stream = resourceOpt.get().open()) {
                    var file = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    Molecule loaded = gson.fromJson(file, Molecule.class);
                    if (loaded != null) {
                        Material key = canonical != null ? canonical : material;
                        molecules.put(key, loaded);
                        if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                            LOGGER.info("getMolecule: loaded molecule from resourceId={} for material={}",
                                    resourceId, key);
                        }
                        return loaded;
                    }
                }
            } else {
                if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                    LOGGER.info("getMolecule: resource {} not found in resource manager", resourceId);
                }
            }
        } catch (Exception e) {
            if (MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode) {
                LOGGER.debug("Failed to lazily load molecule for material {}", material, e);
            }
        }

        return null;
    }

    public static @Nullable List<Pair<Material, Long>> getAlloy(Material material) {
        return Optional.ofNullable(alloys.get(material))
                .map(opt -> opt.orElseGet(() -> AlloyTooltipComponent.deriveComponents(material))).orElse(null);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static void tryColorizeFormula(Material material, OptionalInt idx,
                                          List<Either<FormattedText, TooltipComponent>> tooltipElements) {
        if (Objects.nonNull(material.getMaterialComponents()) && !material.getMaterialComponents().isEmpty() ||
                material.isElement()) {
            final var coloredFormula = MoleculeColorize.coloredFormula(new MaterialStack(material, 1), true);
            if (idx.isPresent()) tooltipElements.set(idx.getAsInt(), Either.left(coloredFormula));
            else tooltipElements.add(1, Either.left(coloredFormula));
        }
    }

    @SubscribeEvent
    public void tooltipGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!MolDrawConfig.INSTANCE.enabled) {
            if (MolDrawConfig.INSTANCE.debugMode) {
                MolDraw.LOGGER.debug("MolDraw is disabled");
            }
            return;
        }

        // 添加详细调试信息
        boolean debug = MolDrawConfig.INSTANCE.debugMode;
        if (debug) {
            MolDraw.LOGGER.info("=== MolDraw Tooltip Analysis ===");
            MolDraw.LOGGER.info("ItemStack: {}", event.getItemStack());
            MolDraw.LOGGER.info("Item class: {}", event.getItemStack().getItem().getClass().getName());
            MolDraw.LOGGER.info("Item Description ID: {}", event.getItemStack().getItem().getDescriptionId());

            // 打印所有标签
            var tags = event.getItemStack().getTags().toList();
            MolDraw.LOGGER.info("Item Tags ({}):", tags.size());
            for (var tag : tags) {
                MolDraw.LOGGER.info("  - {}", tag.location());
            }
        }

        Material material;
        if (event.getItemStack().getItem() instanceof BucketItem) {
            BucketItem bi = (BucketItem) event.getItemStack().getItem();
            // 对于流体桶，使用 ChemicalHelper.getMaterial(Fluid)
            material = ChemicalHelper.getMaterial(bi.getFluid());
            if (debug) {
                MolDraw.LOGGER.info("Fluid bucket material lookup: {}", material);
            }
        } else {
            // 直接使用优化后的查找器
            if (debug) {
                MolDraw.LOGGER.info("ItemStack lookup: {}, NBT: {}",
                        event.getItemStack().getItem().getDescriptionId(),
                        event.getItemStack().getTag());
            }

            // 使用新的 getMaterial 方法，直接获取 Material
            Optional<Material> materialOpt = CustomMaterialLookup.getMaterial(event.getItemStack());

            if (materialOpt.isPresent()) {
                material = materialOpt.get();
                if (debug) {
                    MolDraw.LOGGER.info("Found material: {}, Formula: {}, Components: {}, IsElement: {}",
                            material.getName(),
                            material.getChemicalFormula(),
                            material.getMaterialComponents(),
                            material.isElement());

                    // 检查是否有对应的molecule
                    Molecule mol = getMolecule(material);
                    MolDraw.LOGGER.info("Has molecule in cache: {}", mol != null);

                    // 检查是否有对应的alloy
                    List<Pair<Material, Long>> alloy = getAlloy(material);
                    MolDraw.LOGGER.info("Has alloy in cache: {}", alloy != null);

                    // 检查显示配置
                    MolDraw.LOGGER.info("Config - onlyShowOnShift: {}, showMolecules: {}, showAlloys: {}",
                            MolDrawConfig.INSTANCE.onlyShowOnShift,
                            MolDrawConfig.INSTANCE.molecule.showMolecules,
                            MolDrawConfig.INSTANCE.alloy.showAlloys);
                }
            } else {
                if (debug) {
                    MolDraw.LOGGER.info("No material found for item");
                }
                return;
            }
        }

        if (MaterialHelper.isNull(material)) {
            if (debug) {
                MolDraw.LOGGER.info("Material is null or empty: {}", material);
            }
            return;
        }

        final var mol = getMolecule(material);
        final var alloy = getAlloy(material);
        final var tooltipElements = event.getTooltipElements();

        if (debug) {
            MolDraw.LOGGER.info("Molecule found: {}", mol != null);
            MolDraw.LOGGER.info("Alloy found: {}", alloy != null);
            MolDraw.LOGGER.info("Tooltip elements count: {}", tooltipElements.size());
            MolDraw.LOGGER.info("Only show on shift: {}", MolDrawConfig.INSTANCE.onlyShowOnShift);
            MolDraw.LOGGER.info("GTUtil.isShiftDown(): {}", GTUtil.isShiftDown());
        }

        final var idx = IntStream.range(0, tooltipElements.size())
                .filter(i -> tooltipElements.get(i).left()
                        .map(tt -> {
                            String text = tt.getString();
                            if (debug && text.contains(material.getChemicalFormula())) {
                                MolDraw.LOGGER.info("Found formula match at index {}: {}", i, text);
                            }
                            return text.equals(material.getChemicalFormula());
                        })
                        .orElse(false))
                .reduce((a, b) -> b);

        if (debug) {
            MolDraw.LOGGER.info("Formula index: {}", idx.isPresent() ? idx.getAsInt() : -1);
        }

        if (!MolDrawConfig.INSTANCE.onlyShowOnShift || GTUtil.isShiftDown()) {
            if (!Objects.isNull(mol) && MolDrawConfig.INSTANCE.molecule.showMolecules) {
                if (debug) {
                    MolDraw.LOGGER.info("Displaying molecule tooltip");
                }
                if (idx.isPresent()) {
                    tooltipElements.set(idx.getAsInt(), Either.right(new MoleculeTooltipComponent(mol)));
                } else {
                    tooltipElements.add(1, Either.right(new MoleculeTooltipComponent(mol)));
                }
            } else if (!Objects.isNull(alloy) && MolDrawConfig.INSTANCE.alloy.showAlloys) {
                if (debug) {
                    MolDraw.LOGGER.info("Displaying alloy tooltip");
                }
                if (idx.isPresent()) {
                    tooltipElements.set(idx.getAsInt(), Either.right(new AlloyTooltipComponent(alloy)));
                } else {
                    tooltipElements.add(1, Either.right(new AlloyTooltipComponent(alloy)));
                }
            } else {
                if (debug) {
                    MolDraw.LOGGER.info("Displaying colored formula");
                }
                tryColorizeFormula(material, idx, tooltipElements);
            }
        } else {
            if (debug) {
                MolDraw.LOGGER.info("Displaying colored formula (shift not down)");
            }
            tryColorizeFormula(material, idx, tooltipElements);

            if (MolDrawConfig.INSTANCE.onlyShowOnShift) {
                final int ttIndex = idx.orElse(1) + 1;

                if (Objects.nonNull(mol) && MolDrawConfig.INSTANCE.molecule.showMolecules) {
                    if (debug) {
                        MolDraw.LOGGER.info("Adding shift hint for molecule");
                    }
                    tooltipElements.add(ttIndex, Either.left(FormattedText
                            .of(Component.translatable("tooltip.moldraw.shift_view_molecule").getString())));
                } else if (Objects.nonNull(alloy) && MolDrawConfig.INSTANCE.alloy.showAlloys) {
                    if (debug) {
                        MolDraw.LOGGER.info("Adding shift hint for alloy");
                    }
                    tooltipElements.add(ttIndex, Either.left(
                            FormattedText.of(Component.translatable("tooltip.moldraw.shift_view_alloy").getString())));
                }
            }
        }

        if (debug) {
            MolDraw.LOGGER.info("=== End Tooltip Analysis ===");
        }
    }
}
