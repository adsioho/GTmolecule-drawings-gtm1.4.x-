package com.rubenverg.moldraw;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.UnificationEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * 自定义的 Material 查找器，直接使用 ChemicalHelper 的公共方法。
 * 扩展：支持通过物品标签推断材料，特别是gtceu直接注册的物品。
 */
public final class CustomMaterialLookup {

    private CustomMaterialLookup() {
        // 工具类，禁止实例化
    }

    public static Optional<MaterialStack> getMaterialEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        // 方法1: 直接使用 ChemicalHelper.getMaterial(ItemStack)
        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack);
            if (materialStack != null && materialStack.material() != null) {
                return Optional.of(materialStack);
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: ChemicalHelper.getMaterial(ItemStack) failed", t);
        }

        // 方法2: 使用 ChemicalHelper.getMaterial(ItemLike)
        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack.getItem());
            if (materialStack != null && materialStack.material() != null) {
                return Optional.of(materialStack);
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: ChemicalHelper.getMaterial(ItemLike) failed", t);
        }

        // 方法3: 使用 UnificationEntry 查找
        try {
            UnificationEntry unificationEntry = ChemicalHelper.getUnificationEntry(stack.getItem());
            if (unificationEntry != null && unificationEntry.material != null) {
                TagPrefix prefix = unificationEntry.tagPrefix;
                Material material = unificationEntry.material;
                long amount = prefix != null ? prefix.getMaterialAmount(material) : 1;
                return Optional.of(new MaterialStack(material, amount));
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: UnificationEntry lookup failed", t);
        }

        // 方法4: 检查 ITEM_MATERIAL_INFO 映射
        try {
            // 通过反射访问 ChemicalHelper.ITEM_MATERIAL_INFO
            var field = ChemicalHelper.class.getDeclaredField("ITEM_MATERIAL_INFO");
            field.setAccessible(true);
            var itemMaterialInfoMap = field.get(null);

            if (itemMaterialInfoMap instanceof Map) {
                @SuppressWarnings("unchecked")
                var info = ((Map<ItemLike, Object>) itemMaterialInfoMap).get(stack.getItem());
                if (info != null) {
                    // 尝试获取 MaterialStack
                    var method = info.getClass().getMethod("getMaterial");
                    var result = method.invoke(info);
                    if (result instanceof MaterialStack) {
                        return Optional.of((MaterialStack) result);
                    }
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: ITEM_MATERIAL_INFO lookup failed", t);
        }

        return Optional.empty();
    }

    /**
     * 从物品标签推断Material
     * 特别针对gtceu直接注册的物品（有forge:dusts/xxx等标签）
     */
    private static Optional<Material> inferMaterialFromTags(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            // 获取物品的所有标签
            var tags = stack.getTags().toList();
            if (tags.isEmpty()) {
                return Optional.empty();
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
            
            for (var tag : tags) {
                String tagName = tag.location().toString();
                
                for (String prefix : tagPrefixes) {
                    if (tagName.startsWith(prefix)) {
                        // 提取材料名
                        String materialName = tagName.substring(prefix.length());
                        
                        // 尝试直接查找材料
                        Material material = GTCEuAPI.materialManager.getMaterial(materialName);
                        if (material != null && !isMaterialNull(material)) {
                            MolDraw.LOGGER.debug("CustomMaterialLookup: Found material {} from tag {}", 
                                material.getName(), tagName);
                            return Optional.of(material);
                        }
                        
                        // 如果找不到，尝试命名格式转换
                        
                        // 1. 下划线转驼峰（steel_ingot -> steelIngot -> SteelIngot）
                        String camelCase = toCamelCase(materialName);
                        material = GTCEuAPI.materialManager.getMaterial(camelCase);
                        if (material != null && !isMaterialNull(material)) {
                            MolDraw.LOGGER.debug("CustomMaterialLookup: Found material {} from tag (camelCase) {}", 
                                material.getName(), tagName);
                            return Optional.of(material);
                        }
                        
                        // 2. 首字母大写
                        String capitalized = capitalize(materialName);
                        material = GTCEuAPI.materialManager.getMaterial(capitalized);
                        if (material != null && !isMaterialNull(material)) {
                            MolDraw.LOGGER.debug("CustomMaterialLookup: Found material {} from tag (capitalized) {}", 
                                material.getName(), tagName);
                            return Optional.of(material);
                        }
                        
                        // 3. 全部小写
                        String lowerCase = materialName.toLowerCase();
                        material = GTCEuAPI.materialManager.getMaterial(lowerCase);
                        if (material != null && !isMaterialNull(material)) {
                            MolDraw.LOGGER.debug("CustomMaterialLookup: Found material {} from tag (lowerCase) {}", 
                                material.getName(), tagName);
                            return Optional.of(material);
                        }
                        
                        // 4. 去掉数字后缀（如steel_1 -> steel）
                        if (materialName.matches(".*_\\d+$")) {
                            String baseName = materialName.replaceAll("_\\d+$", "");
                            material = GTCEuAPI.materialManager.getMaterial(baseName);
                            if (material != null && !isMaterialNull(material)) {
                                MolDraw.LOGGER.debug("CustomMaterialLookup: Found material {} from tag (base name) {}", 
                                    material.getName(), tagName);
                                return Optional.of(material);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: Tag inference failed", t);
        }
        
        return Optional.empty();
    }

    /**
     * 直接获取 Material 对象（不包装 MaterialStack）
     * 增强：支持通过标签推断材料，特别是gtceu直接注册的物品
     */
    public static Optional<Material> getMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        // 策略1: 先尝试标准GT查找
        // 放宽验证：即使没有TagPrefix也尝试
        try {
            UnificationEntry entry = ChemicalHelper.getUnificationEntry(stack.getItem());
            if (entry != null && entry.material != null) {
                // 放宽：不再强制要求TagPrefix
                if (!isMaterialNull(entry.material)) {
                    MolDraw.LOGGER.debug("CustomMaterialLookup: Found material via UnificationEntry: {}", 
                        entry.material.getName());
                    return Optional.of(entry.material);
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: getMaterial via UnificationEntry failed", t);
        }

        // 策略2: 通过ChemicalHelper直接获取
        try {
            MaterialStack materialStack = ChemicalHelper.getMaterial(stack);
            if (materialStack != null && materialStack.material() != null) {
                Material material = materialStack.material();
                if (!isMaterialNull(material)) {
                    MolDraw.LOGGER.debug("CustomMaterialLookup: Found material via ChemicalHelper: {}", 
                        material.getName());
                    return Optional.of(material);
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: ChemicalHelper.getMaterial failed", t);
        }

        // 策略3: 从物品标签推断（针对gtceu直接注册的物品）
        Optional<Material> tagMaterial = inferMaterialFromTags(stack);
        if (tagMaterial.isPresent()) {
            return tagMaterial;
        }

        // 策略4: 检查ITEM_MATERIAL_INFO映射
        try {
            Optional<MaterialStack> materialStack = getMaterialEntry(stack);
            if (materialStack.isPresent()) {
                Material material = materialStack.get().material();
                if (!isMaterialNull(material)) {
                    MolDraw.LOGGER.debug("CustomMaterialLookup: Found material via getMaterialEntry: {}", 
                        material.getName());
                    return Optional.of(material);
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: getMaterialEntry failed", t);
        }

        // 最后手段：尝试从物品描述名推断
        try {
            // 使用物品的描述名作为参考
            String itemDescription = stack.getItem().getDescriptionId();
            
            // 简化描述名：去掉"item."前缀和模组名部分
            String simplifiedDesc = itemDescription;
            if (simplifiedDesc.contains(".")) {
                simplifiedDesc = simplifiedDesc.substring(simplifiedDesc.lastIndexOf('.') + 1);
            }
            
            // 尝试查找材料
            Material material = GTCEuAPI.materialManager.getMaterial(simplifiedDesc);
            if (material != null && !isMaterialNull(material)) {
                MolDraw.LOGGER.debug("CustomMaterialLookup: Found material from description: {}", 
                    material.getName());
                return Optional.of(material);
            }
            
            // 尝试去掉常见后缀
            String[] suffixes = {"_dust", "_ingot", "_nugget", "_plate", "_rod", "_bolt", "_screw", "_gear", "_block"};
            for (String suffix : suffixes) {
                if (simplifiedDesc.endsWith(suffix)) {
                    String baseName = simplifiedDesc.substring(0, simplifiedDesc.length() - suffix.length());
                    material = GTCEuAPI.materialManager.getMaterial(baseName);
                    if (material != null && !isMaterialNull(material)) {
                        MolDraw.LOGGER.debug("CustomMaterialLookup: Found material from description (without suffix): {}", 
                            material.getName());
                        return Optional.of(material);
                    }
                }
            }
            
            // 如果描述名包含"gtceu"，尝试提取材料名
            if (itemDescription.contains("gtceu")) {
                // 尝试匹配模式：gtceu:xxx
                if (itemDescription.matches(".*gtceu:([a-zA-Z0-9_]+).*")) {
                    // 这里我们只能尝试从描述字符串中提取
                    // 但实际上更可靠的方式是从标签推断
                }
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: Description analysis failed", t);
        }

        MolDraw.LOGGER.debug("CustomMaterialLookup: No material found for item: {}", 
            stack.getItem());
        return Optional.empty();
    }

    /**
     * 获取 ItemStack 的 TagPrefix
     */
    public static Optional<TagPrefix> getTagPrefix(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        try {
            TagPrefix prefix = ChemicalHelper.getPrefix(stack.getItem());
            if (prefix != null) {
                return Optional.of(prefix);
            }
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: getTagPrefix failed", t);
        }

        return Optional.empty();
    }
    
    /**
     * 辅助方法：判断Material是否为null
     * 使用com.adsioho.gtm.compat.MaterialHelper.isNull
     */
    private static boolean isMaterialNull(Material material) {
        if (material == null) return true;
        
        try {
            // 使用MaterialHelper.isNull方法
            return com.adsioho.gtm.compat.MaterialHelper.isNull(material);
        } catch (Throwable t) {
            MolDraw.LOGGER.debug("CustomMaterialLookup: MaterialHelper.isNull failed", t);
            return false;
        }
    }
    
    /**
     * 辅助方法：下划线转驼峰
     */
    private static String toCamelCase(String str) {
        if (str == null || str.isEmpty()) return str;
        
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * 辅助方法：首字母大写
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}