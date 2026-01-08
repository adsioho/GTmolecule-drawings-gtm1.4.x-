package com.rubenverg.moldraw;

import com.gregtechceu.gtceu.api.GTCEuAPI;
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

    private CustomMaterialLookup() {
        // 工具类，禁止实例化
    }

    private static boolean isMaterialNull(Material material) {
        if (material == null) {
            logDebug("isMaterialNull: material is null");
            return true;
        }

        try {
            // 使用MaterialHelper.isNull方法
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

        // 方法1: 直接使用 ChemicalHelper.getMaterial(ItemStack)
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

        // 方法2: 使用 ChemicalHelper.getMaterial(ItemLike)
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

        // 方法3: 使用 UnificationEntry 查找
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

        // 方法4: 检查 ITEM_MATERIAL_INFO 映射
        try {
            logDebug("getMaterialEntry: Attempting ITEM_MATERIAL_INFO reflection lookup");
            // 通过反射访问 ChemicalHelper.ITEM_MATERIAL_INFO
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
                    // 尝试获取 MaterialStack
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

    /**
     * 从物品标签推断Material
     * 特别针对gtceu直接注册的物品（有forge:dusts/xxx等标签）
     */
    private static Optional<Material> inferMaterialFromTags(ItemStack stack) {
        if (stack == null) {
            logDebug("inferMaterialFromTags: stack is null");
            return Optional.empty();
        }
        
        if (stack.isEmpty()) {
            logDebug("inferMaterialFromTags: stack is empty");
            return Optional.empty();
        }

        try {
            logDebug("inferMaterialFromTags: Starting tag inference for item: " + stack.getItem().toString());
            
            // 获取物品的所有标签
            var tags = stack.getTags().toList();
            if (tags.isEmpty()) {
                logDebug("inferMaterialFromTags: Item has no tags");
                return Optional.empty();
            }

            logDebug("inferMaterialFromTags: Item has " + tags.size() + " tags");
            if (isDebugEnabled()) {
                for (var tag : tags) {
                    logDebug("  Tag: " + tag.location().toString());
                }
            }

            // 定义我们关心的标签前缀（针对GTCEU直接注册的物品）
            String[] tagPrefixes = {
                    "forge:dusts/",
                    "forge:ingots/",
                    "forge:nuggets/",
                    "forge:plates/",
                    "forge:rods/",
                    "forge:blocks/",
                    "forge:gears/",
                    "forge:bolts/",
                    "forge:screws/",
                    "forge:wires/",
                    "forge:foils/",
                    "forge:gems/",
                    "forge:ores/",
                    "forge:raw_materials/"
            };

            int checkedTags = 0;
            for (var tag : tags) {
                String tagName = tag.location().toString();
                checkedTags++;

                for (String prefix : tagPrefixes) {
                    if (tagName.startsWith(prefix)) {
                        // 提取材料名
                        String materialName = tagName.substring(prefix.length());
                        logDebug("inferMaterialFromTags: Found matching tag: " + tagName + 
                                ", extracted material name: " + materialName);

                        // 尝试直接查找材料
                        Material material = GTCEuAPI.materialManager.getMaterial(materialName);
                        if (material != null) {
                            logDebug("inferMaterialFromTags: Found material in registry: " + 
                                    material.getName());
                            if (!isMaterialNull(material)) {
                                logDebug("inferMaterialFromTags: Material is valid: " + 
                                        material.getName() + " from tag " + tagName);
                                return Optional.of(material);
                            } else {
                                logDebug("inferMaterialFromTags: Material " + material.getName() + 
                                        " is considered null by isMaterialNull");
                            }
                        } else {
                            logDebug("inferMaterialFromTags: No material found with name: " + 
                                    materialName);
                        }
                    }
                }
            }
            
            logDebug("inferMaterialFromTags: Checked " + checkedTags + " tags, no valid material found");
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: Tag inference failed", t);
        }

        return Optional.empty();
    }

    /**
     * 直接获取 Material 对象（不包装 MaterialStack）
     * 增强：支持通过标签推断材料，特别是gtceu直接注册的物品
     */
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

        // 策略1: 先尝试标准GT查找
        // 放宽验证：即使没有TagPrefix也尝试
        try {
            UnificationEntry entry = ChemicalHelper.getUnificationEntry(stack.getItem());
            if (entry != null && entry.material != null) {
                logDebug("Found UnificationEntry: material=" + entry.material.getName() + 
                        ", tagPrefix=" + (entry.tagPrefix != null ? entry.tagPrefix.name : "null"));
                // 放宽：不再强制要求TagPrefix
                if (!isMaterialNull(entry.material)) {
                    logDebug("Strategy 1 (UnificationEntry) SUCCESS: " + entry.material.getName());
                    return Optional.of(entry.material);
                } else {
                    logDebug("Strategy 1 (UnificationEntry): Material is considered null");
                }
            } else {
                logDebug("Strategy 1 (UnificationEntry): No entry found or material is null");
                if (entry == null) {
                    logDebug("  - UnificationEntry is null");
                } else if (entry.material == null) {
                    logDebug("  - UnificationEntry.material is null");
                }
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: getMaterial via UnificationEntry failed", t);
        }

        // 策略2: 通过ChemicalHelper直接获取
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

        // 策略3: 从物品标签推断（针对gtceu直接注册的物品）
        logDebug("Strategy 3 (Tag Inference): Starting...");
        Optional<Material> tagMaterial = inferMaterialFromTags(stack);
        if (tagMaterial.isPresent()) {
            logDebug("Strategy 3 (Tag Inference) SUCCESS: " + tagMaterial.get().getName());
            return tagMaterial;
        } else {
            logDebug("Strategy 3 (Tag Inference): No material found from tags");
        }

        // 策略4: 检查ITEM_MATERIAL_INFO映射
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

        // 最后手段：尝试从物品描述名推断
        try {
            logDebug("Strategy 5 (Description Inference): Starting...");
            // 使用物品的描述名作为参考
            String itemDescription = stack.getItem().getDescriptionId();
            logDebug("  Item description: " + itemDescription);

            // 简化描述名：去掉"item."前缀和模组名部分
            String simplifiedDesc = itemDescription;
            if (simplifiedDesc.contains(".")) {
                simplifiedDesc = simplifiedDesc.substring(simplifiedDesc.lastIndexOf('.') + 1);
            }
            logDebug("  Simplified description: " + simplifiedDesc);

            // 尝试查找材料
            Material material = GTCEuAPI.materialManager.getMaterial(simplifiedDesc);
            if (material != null && !isMaterialNull(material)) {
                logDebug("Strategy 5 (Description Inference) SUCCESS (direct match): " + material.getName());
                return Optional.of(material);
            }

            // 尝试去掉常见后缀
            String[] suffixes = { "_dust", "_ingot", "_nugget", "_plate", "_rod", "_bolt", "_screw", "_gear",
                    "_block" };
            for (String suffix : suffixes) {
                if (simplifiedDesc.endsWith(suffix)) {
                    String baseName = simplifiedDesc.substring(0, simplifiedDesc.length() - suffix.length());
                    logDebug("  Trying suffix '" + suffix + "', base name: " + baseName);
                    material = GTCEuAPI.materialManager.getMaterial(baseName);
                    if (material != null && !isMaterialNull(material)) {
                        logDebug("Strategy 5 (Description Inference) SUCCESS (with suffix removal): " + material.getName());
                        return Optional.of(material);
                    }
                }
            }
            
            logDebug("Strategy 5 (Description Inference): No material found");
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: Description analysis failed", t);
        }

        logDebug("=== ALL STRATEGIES FAILED ===");
        logDebug("No material found for item: " + stack.getItem().toString());
        return Optional.empty();
    }

    /**
     * 获取 ItemStack 的 TagPrefix
     */
    public static Optional<TagPrefix> getTagPrefix(ItemStack stack) {
        if (stack == null) {
            logDebug("getTagPrefix: stack is null");
            return Optional.empty();
        }
        
        if (stack.isEmpty()) {
            logDebug("getTagPrefix: stack is empty");
            return Optional.empty();
        }

        logDebug("getTagPrefix: Looking up TagPrefix for item: " + stack.getItem().toString());
        
        try {
            TagPrefix prefix = ChemicalHelper.getPrefix(stack.getItem());
            if (prefix != null) {
                logDebug("getTagPrefix: Found: " + prefix.name);
                return Optional.of(prefix);
            } else {
                logDebug("getTagPrefix: No TagPrefix found");
            }
        } catch (Throwable t) {
            logDebug("CustomMaterialLookup: getTagPrefix failed", t);
        }

        return Optional.empty();
    }
    
    /**
     * 检查是否启用了调试模式
     */
    private static boolean isDebugEnabled() {
        return MolDrawConfig.INSTANCE != null && MolDrawConfig.INSTANCE.debugMode;
    }
    
    /**
     * 记录调试信息（仅当调试模式启用时）
     */
    private static void logDebug(String message) {
        if (isDebugEnabled()) {
            MolDraw.LOGGER.debug(message);
        }
    }
    
    /**
     * 记录调试信息和异常（仅当调试模式启用时）
     */
    private static void logDebug(String message, Throwable t) {
        if (isDebugEnabled()) {
            MolDraw.LOGGER.debug(message, t);
        }
    }
}