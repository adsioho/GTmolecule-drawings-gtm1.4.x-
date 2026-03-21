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
