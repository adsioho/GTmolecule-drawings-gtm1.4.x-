package com.rubenverg.moldraw;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class CustomMaterialLookup {

    private CustomMaterialLookup() {}

    private static boolean isMaterialNull(Material material) {
        return material == null;
    }

    public static Optional<Material> getMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        // 过滤机器物品
        if (stack.getItem().getClass().getName().contains("MetaMachineItem")) {
            return Optional.empty();
        }

        // 过滤非原材料物品
        String descriptionId = stack.getItem().getDescriptionId();
        if (descriptionId.contains("machine_casing") ||
                descriptionId.contains("casing") ||
                descriptionId.contains("machine_parts") ||
                descriptionId.contains("machine_block") ||
                descriptionId.contains("structure_block") ||
                descriptionId.contains("machine_hull") ||
                descriptionId.contains("machine_frame") ||
                descriptionId.contains("boiler") ||
                descriptionId.contains("machine")) {
            return Optional.empty();
        }

        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack);
            if (materialStack != null && !isMaterialNull(materialStack.material())) {
                return Optional.of(materialStack.material());
            }
        } catch (Throwable t) {
            // 忽略异常
        }

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
