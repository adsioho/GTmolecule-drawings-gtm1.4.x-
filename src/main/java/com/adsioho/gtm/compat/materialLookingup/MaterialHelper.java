package com.adsioho.gtm.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MaterialHelper {

    private MaterialHelper() { /* no instantiation */ }

    @FunctionalInterface
    public interface DebugLogger {

        void log(String message);
    }

    private static DebugLogger debugLogger = null;

    public static void setDebugLogger(DebugLogger logger) {
        debugLogger = logger;
    }

    private static void logDebug(String message) {
        if (debugLogger != null) {
            debugLogger.log(message);
        }
    }

    public static boolean isNull(Object material) {
        if (material == null) {
            logDebug("MaterialHelper.isNull: input is null");
            return true;
        }

        logDebug("MaterialHelper.isNull: Starting analysis of object: " +
                material.getClass().getName());

        try {
            Class<?> cls = material.getClass();

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
            logDebug("MaterialHelper.isNull: Exception during analysis: " + t.getMessage());
            logDebug("MaterialHelper.isNull: Falling back to false (non-null)");
            return false;
        }
    }
}
