package com.rubenverg.moldraw;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.UnificationEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Map;
import java.util.Optional;

public final class CustomMaterialLookup {

    private CustomMaterialLookup() {}

    private static boolean isMaterialNull(Material material) {
        if (material == null) {
            logDebug("isMaterialNull: material is null");
            return true;
        }

        try {
            boolean result = com.adsioho.gtm.compat.MaterialHelper.isNull(material);
            logDebug("isMaterialNull: MaterialHelper.isNull returned " + result + " for material: " +
                    (material.getName() != null ? material.getName() : "unnamed"));
            return result;
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: MaterialHelper.isNull failed", t);
            logDebug("isMaterialNull: falling back to false for material: " +
                    (material.getName() != null ? material.getName() : "unnamed"));
            return false;
        }
    }

    public static Optional<MaterialStack> getMaterialEntry(ItemStack stack) {
        if (stack == null) {
            logDebug("getMaterialEntry: stack is null");
            return Optional.empty();
        }

        if (stack.isEmpty()) {
            logDebug("getMaterialEntry: stack is empty");
            return Optional.empty();
        }

        logDebug("getMaterialEntry: Starting lookup for item: " + stack.getItem().toString() +
                " (Display: " + stack.getDisplayName().getString() + ")");

        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack);
            if (materialStack != null && materialStack.material() != null) {
                logDebug("getMaterialEntry: Found via ChemicalHelper.getMaterial(ItemStack): " +
                        materialStack.material().getName() + " x" + materialStack.amount());
                return Optional.of(materialStack);
            } else {
                logDebug("getMaterialEntry: ChemicalHelper.getMaterial(ItemStack) returned null");
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: ChemicalHelper.getMaterial(ItemStack) failed", t);
        }

        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack.getItem());
            if (materialStack != null && materialStack.material() != null) {
                logDebug("getMaterialEntry: Found via ChemicalHelper.getMaterial(ItemLike): " +
                        materialStack.material().getName() + " x" + materialStack.amount());
                return Optional.of(materialStack);
            } else {
                logDebug("getMaterialEntry: ChemicalHelper.getMaterial(ItemLike) returned null");
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: ChemicalHelper.getMaterial(ItemLike) failed", t);
        }

        try {
            UnificationEntry unificationEntry = ChemicalHelper.getUnificationEntry(stack.getItem());
            if (unificationEntry != null && unificationEntry.material != null) {
                TagPrefix prefix = unificationEntry.tagPrefix;
                Material material = unificationEntry.material;
                long amount = prefix != null ? prefix.getMaterialAmount(material) : 1;
                logDebug("getMaterialEntry: Found via UnificationEntry: " + material.getName() +
                        " (TagPrefix: " + (prefix != null ? prefix.name : "null") +
                        ", Amount: " + amount + ")");
                return Optional.of(new MaterialStack(material, amount));
            } else {
                logDebug("getMaterialEntry: UnificationEntry lookup returned null or had null material");
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: UnificationEntry lookup failed", t);
        }

        try {
            logDebug("getMaterialEntry: Attempting ITEM_MATERIAL_INFO reflection lookup");
            var field = ChemicalHelper.class.getDeclaredField("ITEM_MATERIAL_INFO");
            field.setAccessible(true);
            var itemMaterialInfoMap = field.get(null);

            if (itemMaterialInfoMap instanceof Map) {
                logDebug("getMaterialEntry: ITEM_MATERIAL_INFO map size: " +
                        ((Map<?, ?>) itemMaterialInfoMap).size());
                @SuppressWarnings("unchecked")
                var info = ((Map<ItemLike, Object>) itemMaterialInfoMap).get(stack.getItem());
                if (info != null) {
                    logDebug("getMaterialEntry: Found entry in ITEM_MATERIAL_INFO");
                    var method = info.getClass().getMethod("getMaterial");
                    var result = method.invoke(info);
                    if (result instanceof MaterialStack) {
                        MaterialStack ms = (MaterialStack) result;
                        logDebug("getMaterialEntry: Retrieved MaterialStack: " +
                                ms.material().getName() + " x" + ms.amount());
                        return Optional.of(ms);
                    } else {
                        logDebug("getMaterialEntry: getMaterial method returned non-MaterialStack: " +
                                (result != null ? result.getClass().getName() : "null"));
                    }
                } else {
                    logDebug("getMaterialEntry: No entry found in ITEM_MATERIAL_INFO for this item");
                }
            } else {
                logDebug("getMaterialEntry: ITEM_MATERIAL_INFO is not a Map, type: " +
                        (itemMaterialInfoMap != null ? itemMaterialInfoMap.getClass().getName() : "null"));
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: ITEM_MATERIAL_INFO lookup failed", t);
        }

        logDebug("getMaterialEntry: All lookup methods failed for item: " + stack.getItem().toString());
        return Optional.empty();
    }

    public static Optional<Material> getMaterial(ItemStack stack) {
        if (stack == null) {
            logDebug("getMaterial: stack is null");
            return Optional.empty();
        }

        if (stack.isEmpty()) {
            logDebug("getMaterial: stack is empty");
            return Optional.empty();
        }

        logDebug("=== CustomMaterialLookup.getMaterial ===");
        logDebug("Item: " + stack.getItem().toString());
        logDebug("Description ID: " + stack.getItem().getDescriptionId());
        logDebug("Display Name: " + stack.getDisplayName().getString());
        logDebug("Count: " + stack.getCount());

        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack);
            if (materialStack != null && materialStack.material() != null) {
                Material material = materialStack.material();
                logDebug("Strategy 2 (ChemicalHelper): Found MaterialStack: " +
                        material.getName() + " x" + materialStack.amount());
                if (!isMaterialNull(material)) {
                    logDebug("Strategy 2 (ChemicalHelper) SUCCESS: " + material.getName());
                    return Optional.of(material);
                } else {
                    logDebug("Strategy 2 (ChemicalHelper): Material is considered null");
                }
            } else {
                logDebug("Strategy 2 (ChemicalHelper): No material found");
                if (materialStack == null) {
                    logDebug("  - MaterialStack is null");
                } else if (materialStack.material() == null) {
                    logDebug("  - MaterialStack.material is null");
                }
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: ChemicalHelper.getMaterial failed", t);
        }

        try {
            logDebug("Strategy 4 (getMaterialEntry): Starting...");
            Optional<MaterialStack> materialStack = getMaterialEntry(stack);
            if (materialStack.isPresent()) {
                Material material = materialStack.get().material();
                if (!isMaterialNull(material)) {
                    logDebug("Strategy 4 (getMaterialEntry) SUCCESS: " + material.getName());
                    return Optional.of(material);
                } else {
                    logDebug("Strategy 4 (getMaterialEntry): Material is considered null");
                }
            } else {
                logDebug("Strategy 4 (getMaterialEntry): No material found");
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: getMaterialEntry failed", t);
        }

        logDebug("=== ALL STRATEGIES FAILED ===");
        logDebug("No material found for item: " + stack.getItem().toString());
        return Optional.empty();
    }

    private static boolean isDebugEnabled() {
        return MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode;
    }

    private static void logDebug(String message) {
        if (isDebugEnabled()) {
            MolDraw.LOGGER.debug(message);
        }
    }

    private static void logDebug(String message, Throwable t) {
        if (isDebugEnabled()) {
            MolDraw.LOGGER.debug(message, t);
        }
    }
}
