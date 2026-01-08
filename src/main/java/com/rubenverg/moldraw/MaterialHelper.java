package com.adsioho.gtm.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 兼容性工具：在不调用第三方 mod 的 Material.isNull() 的情况下，判断一个 Material（或类似对象）是否应被视为“空”。
 *
 * 实现策略：
 * - null -> true
 * - 通过反射寻找常见的数值字段/方法（getAmount, getStackSize, amount, qty 等），若存在且值 <= 0 -> true
 * - 通过反射寻找常见的名称方法（getName, getUnlocalizedName, getDisplayName 等），若返回 null/空串/"null"/"air"/"unknown" -> true
 *
 * 设计原则：尽量保守，不抛出异常，若无法判定则返回 false（即认为非空）。
 * 适用于多种 mod 定义的 Material 类或类似类型。
 */
public final class MaterialHelper {

    private MaterialHelper() { /* no instantiation */ }

    /**
     * 调试回调接口，用于输出调试信息
     */
    @FunctionalInterface
    public interface DebugLogger {

        void log(String message);
    }

    private static DebugLogger debugLogger = null;

    /**
     * 设置调试日志记录器
     */
    public static void setDebugLogger(DebugLogger logger) {
        debugLogger = logger;
    }

    /**
     * 记录调试信息
     */
    private static void logDebug(String message) {
        if (debugLogger != null) {
            debugLogger.log(message);
        }
    }

    /**
     * 判断 material 是否可视为“空”。
     * 不会调用任何外部 mod 定义的 isNull()。
     *
     * @param material 任意对象（通常为 Material 或类似类型）
     * @return true 如果判定为空；false 否则
     */
    public static boolean isNull(Object material) {
        if (material == null) {
            logDebug("MaterialHelper.isNull: input is null");
            return true;
        }

        logDebug("MaterialHelper.isNull: Starting analysis of object: " +
                material.getClass().getName());

        try {
            Class<?> cls = material.getClass();

            // 1) 数量/大小类检查（若存在且 <= 0 则视为空）
            String[] numericMethods = {
                    "getAmount", "getStackSize", "getSize", "getSizeInUnits", "getQuantity", "getQty", "getCount"
            };
            for (String methodName : numericMethods) {
                try {
                    Method m = cls.getMethod(methodName);
                    Object val = m.invoke(material);
                    if (val instanceof Number) {
                        long longValue = ((Number) val).longValue();
                        logDebug("MaterialHelper.isNull: Method " + methodName + " returned: " + longValue);
                        if (longValue <= 0L) {
                            logDebug(
                                    "MaterialHelper.isNull: Method " + methodName + " returned <= 0, considering null");
                            return true;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    logDebug("MaterialHelper.isNull: Method " + methodName + " not found");
                } catch (Throwable t) {
                    logDebug("MaterialHelper.isNull: Error invoking method " + methodName + ": " + t.getMessage());
                }
            }

            // 1b) 也尝试查找常见字段（如 amount, stackSize, qty）
            String[] numericFields = { "amount", "stackSize", "size", "quantity", "qty", "count" };
            for (String fieldName : numericFields) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object val = f.get(material);
                    if (val instanceof Number) {
                        long longValue = ((Number) val).longValue();
                        logDebug("MaterialHelper.isNull: Field " + fieldName + " value: " + longValue);
                        if (longValue <= 0L) {
                            logDebug("MaterialHelper.isNull: Field " + fieldName + " value <= 0, considering null");
                            return true;
                        }
                    }
                } catch (NoSuchFieldException ignored) {
                    logDebug("MaterialHelper.isNull: Field " + fieldName + " not found");
                } catch (Throwable t) {
                    logDebug("MaterialHelper.isNull: Error accessing field " + fieldName + ": " + t.getMessage());
                }
            }

            // 2) 名称类检查（若名称为 null / 空 / "null" / "air" / "unknown" 则视为空）
            String[] nameMethods = {
                    "getName", "getUnlocalizedName", "getLocalizedName", "getDisplayName", "name", "getId"
            };
            for (String methodName : nameMethods) {
                try {
                    Method m = cls.getMethod(methodName);
                    Object val = m.invoke(material);
                    logDebug("MaterialHelper.isNull: Method " + methodName + " returned: " +
                            (val != null ? "'" + val.toString() + "'" : "null"));
                    if (val == null) {
                        logDebug("MaterialHelper.isNull: Method " + methodName + " returned null, considering null");
                        return true;
                    }
                    String s = val.toString().trim();
                    if (s.isEmpty()) {
                        logDebug("MaterialHelper.isNull: Method " + methodName +
                                " returned empty string, considering null");
                        return true;
                    }
                    String lower = s.toLowerCase();
                    if (lower.equals("null") || lower.equals("air") || lower.equals("unknown") ||
                            lower.equals("empty")) {
                        logDebug("MaterialHelper.isNull: Method " + methodName + " returned '" + lower +
                                "', considering null");
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                    logDebug("MaterialHelper.isNull: Method " + methodName + " not found");
                } catch (Throwable t) {
                    logDebug("MaterialHelper.isNull: Error invoking method " + methodName + ": " + t.getMessage());
                }
            }

            // 2b) 字段名称检查
            String[] nameFields = { "name", "id", "unlocalizedName", "displayName", "localizedName" };
            for (String fieldName : nameFields) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object val = f.get(material);
                    logDebug("MaterialHelper.isNull: Field " + fieldName + " value: " +
                            (val != null ? "'" + val.toString() + "'" : "null"));
                    if (val == null) {
                        logDebug("MaterialHelper.isNull: Field " + fieldName + " is null, considering null");
                        return true;
                    }
                    String s = val.toString().trim();
                    if (s.isEmpty()) {
                        logDebug("MaterialHelper.isNull: Field " + fieldName + " is empty, considering null");
                        return true;
                    }
                    String lower = s.toLowerCase();
                    if (lower.equals("null") || lower.equals("air") || lower.equals("unknown") ||
                            lower.equals("empty")) {
                        logDebug("MaterialHelper.isNull: Field " + fieldName + " is '" + lower + "', considering null");
                        return true;
                    }
                } catch (NoSuchFieldException ignored) {
                    logDebug("MaterialHelper.isNull: Field " + fieldName + " not found");
                } catch (Throwable t) {
                    logDebug("MaterialHelper.isNull: Error accessing field " + fieldName + ": " + t.getMessage());
                }
            }

            logDebug("MaterialHelper.isNull: Object passed all null checks, considering non-null");
            return false;

        } catch (Throwable t) {
            // 任何反射异常都不应抛出，保持兼容：无法判定 -> 返回 false（认为非空）
            logDebug("MaterialHelper.isNull: Exception during analysis: " + t.getMessage());
            logDebug("MaterialHelper.isNull: Falling back to false (non-null)");
            return false;
        }
    }
}
