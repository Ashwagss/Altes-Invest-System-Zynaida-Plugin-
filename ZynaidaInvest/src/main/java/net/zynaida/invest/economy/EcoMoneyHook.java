package net.zynaida.invest.economy;

import net.zynaida.invest.ZynaidaInvest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Direkte EcoMoney Integration über Reflection - Kein Vault benötigt!
 * Nutzt: EcoMoney.getInstance().getAPI()
 */
public class EcoMoneyHook {

    private final ZynaidaInvest plugin;
    private Object ecoMoneyAPI = null;
    private boolean enabled = false;

    // Cached Methods
    private Method getBalanceMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private Method hasMethod;
    private Method formatMoneyMethod;

    // Ob deposit/withdraw nur 2 Parameter haben (UUID, BigDecimal) statt 3
    private boolean withdrawHasReason = true;
    private boolean depositHasReason = true;

    public EcoMoneyHook(ZynaidaInvest plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        Plugin ecoMoney = Bukkit.getPluginManager().getPlugin("EcoMoney");
        if (ecoMoney == null || !ecoMoney.isEnabled()) {
            plugin.getLogger().severe("EcoMoney nicht gefunden!");
            return false;
        }

        try {
            // EcoMoney.getInstance().getAPI()
            Method getInstanceMethod = ecoMoney.getClass().getMethod("getInstance");
            Object ecoMoneyInstance = getInstanceMethod.invoke(null);

            Method getAPIMethod = ecoMoneyInstance.getClass().getMethod("getAPI");
            ecoMoneyAPI = getAPIMethod.invoke(ecoMoneyInstance);

            if (ecoMoneyAPI == null) {
                plugin.getLogger().severe("EcoMoney API ist null!");
                return false;
            }

            Class<?> apiClass = ecoMoneyAPI.getClass();
            plugin.getLogger().info("EcoMoney API Klasse: " + apiClass.getName());

            // Alle Interfaces loggen
            for (Class<?> iface : apiClass.getInterfaces()) {
                plugin.getLogger().info("  Interface: " + iface.getName());
            }

            // Methoden cachen - getBalance
            getBalanceMethod = findMethod(apiClass, "getBalance", UUID.class);
            if (getBalanceMethod == null) {
                plugin.getLogger().severe("getBalance(UUID) nicht gefunden!");
                return false;
            }
            plugin.getLogger().info("  §a✓ getBalance(UUID)");

            // withdraw - versuche erst mit 3 Parametern, dann mit 2
            withdrawMethod = findMethod(apiClass, "withdraw", UUID.class, BigDecimal.class, String.class);
            if (withdrawMethod != null) {
                withdrawHasReason = true;
                plugin.getLogger().info("  §a✓ withdraw(UUID, BigDecimal, String)");
            } else {
                withdrawMethod = findMethod(apiClass, "withdraw", UUID.class, BigDecimal.class);
                if (withdrawMethod != null) {
                    withdrawHasReason = false;
                    plugin.getLogger().info("  §a✓ withdraw(UUID, BigDecimal) [ohne Reason]");
                } else {
                    plugin.getLogger().severe("withdraw Methode nicht gefunden!");
                    logAllMethods(apiClass);
                    return false;
                }
            }

            // deposit - versuche erst mit 3 Parametern, dann mit 2
            depositMethod = findMethod(apiClass, "deposit", UUID.class, BigDecimal.class, String.class);
            if (depositMethod != null) {
                depositHasReason = true;
                plugin.getLogger().info("  §a✓ deposit(UUID, BigDecimal, String)");
            } else {
                depositMethod = findMethod(apiClass, "deposit", UUID.class, BigDecimal.class);
                if (depositMethod != null) {
                    depositHasReason = false;
                    plugin.getLogger().info("  §a✓ deposit(UUID, BigDecimal) [ohne Reason]");
                } else {
                    // Versuche alternative Namen
                    depositMethod = findMethod(apiClass, "addMoney", UUID.class, BigDecimal.class, String.class);
                    if (depositMethod == null) {
                        depositMethod = findMethod(apiClass, "addMoney", UUID.class, BigDecimal.class);
                    }
                    if (depositMethod == null) {
                        depositMethod = findMethod(apiClass, "giveMoney", UUID.class, BigDecimal.class, String.class);
                    }
                    if (depositMethod == null) {
                        depositMethod = findMethod(apiClass, "giveMoney", UUID.class, BigDecimal.class);
                    }
                    if (depositMethod != null) {
                        depositHasReason = depositMethod.getParameterCount() == 3;
                        plugin.getLogger().info("  §a✓ " + depositMethod.getName() + " als deposit-Alternative");
                    } else {
                        plugin.getLogger().severe("deposit/addMoney/giveMoney Methode nicht gefunden!");
                        logAllMethods(apiClass);
                        return false;
                    }
                }
            }

            // has
            hasMethod = findMethod(apiClass, "has", UUID.class, BigDecimal.class);
            if (hasMethod == null) {
                hasMethod = findMethod(apiClass, "hasEnough", UUID.class, BigDecimal.class);
            }
            if (hasMethod != null) {
                plugin.getLogger().info("  §a✓ " + hasMethod.getName() + "(UUID, BigDecimal)");
            } else {
                plugin.getLogger().warning("  §ehas() nicht gefunden - nutze getBalance Fallback");
            }

            // formatMoney mit Fallback
            formatMoneyMethod = findMethod(apiClass, "formatMoney", BigDecimal.class);
            if (formatMoneyMethod == null) {
                formatMoneyMethod = findMethod(apiClass, "format", BigDecimal.class);
            }
            if (formatMoneyMethod != null) {
                plugin.getLogger().info("  §a✓ " + formatMoneyMethod.getName() + "(BigDecimal)");
            } else {
                plugin.getLogger().info("  §e⚠ Keine format-Methode - nutze Standard");
            }

            // Rückgabetypen loggen
            plugin.getLogger().info("  §7withdraw gibt zurück: " + withdrawMethod.getReturnType().getSimpleName());
            plugin.getLogger().info("  §7deposit gibt zurück: " + depositMethod.getReturnType().getSimpleName());

            enabled = true;
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("EcoMoney Verbindung fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sucht eine Methode auf der Klasse und allen Interfaces/Superklassen
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        // Direkt auf der Klasse
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        // Auf allen Interfaces suchen
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return iface.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }

        // Superklassen durchgehen
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            try {
                return superClass.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
            superClass = superClass.getSuperclass();
        }

        return null;
    }

    /**
     * Loggt alle öffentlichen Methoden einer Klasse (Debug)
     */
    private void logAllMethods(Class<?> clazz) {
        plugin.getLogger().warning("  §eAlle Methoden auf " + clazz.getSimpleName() + ":");
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            StringBuilder sb = new StringBuilder();
            sb.append("    §e- ").append(m.getName()).append("(");
            Class<?>[] params = m.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(params[i].getSimpleName());
            }
            sb.append(") -> ").append(m.getReturnType().getSimpleName());
            plugin.getLogger().warning(sb.toString());
        }
        // Auch Interfaces loggen
        for (Class<?> iface : clazz.getInterfaces()) {
            plugin.getLogger().warning("  §eMethoden auf Interface " + iface.getSimpleName() + ":");
            for (Method m : iface.getMethods()) {
                StringBuilder sb = new StringBuilder();
                sb.append("    §e- ").append(m.getName()).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getSimpleName());
                }
                sb.append(") -> ").append(m.getReturnType().getSimpleName());
                plugin.getLogger().warning(sb.toString());
            }
        }
    }

    /**
     * Prüft ob ein Transaktions-Ergebnis erfolgreich war.
     * Behandelt: boolean, Enum, Objekte mit isSuccess/etc.
     */
    private boolean checkTransactionSuccess(Object result, String operation) {
        if (result == null) {
            plugin.getLogger().warning("EcoMoney " + operation + ": Ergebnis ist null!");
            return false;
        }

        plugin.getLogger().info("EcoMoney " + operation + " Ergebnis: " + result.getClass().getSimpleName() + " = " + result);

        // Direkt ein boolean?
        if (result instanceof Boolean b) {
            plugin.getLogger().info("EcoMoney " + operation + ": Boolean = " + b);
            return b;
        }

        // Enum-Prüfung (Name enthält "SUCCESS" oder "OK")
        if (result.getClass().isEnum()) {
            String name = ((Enum<?>) result).name().toUpperCase();
            boolean success = name.contains("SUCCESS") || name.contains("OK") || name.equals("COMPLETED");
            plugin.getLogger().info("EcoMoney " + operation + ": Enum = " + name + " -> " + (success ? "OK" : "FAIL"));
            return success;
        }

        // Methoden auf dem Result-Objekt suchen
        String[] checkMethods = {"isSuccess", "isSuccessful", "wasSuccessful", "successful",
                "transactionSuccess", "isCompleted", "getSuccess"};
        for (String methodName : checkMethods) {
            try {
                Method m = result.getClass().getMethod(methodName);
                Object value = m.invoke(result);
                if (value instanceof Boolean b) {
                    plugin.getLogger().info("EcoMoney " + operation + ": " + methodName + "() = " + b);
                    if (!b) {
                        // Fehlergrund aus dem Result-Objekt auslesen
                        logResultDetails(result, operation);
                    }
                    return b;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                plugin.getLogger().warning("EcoMoney " + operation + " " + methodName + "() Fehler: " + e.getMessage());
            }
        }

        // Letzte Chance: toString() prüfen
        String str = result.toString().toUpperCase();
        if (str.contains("SUCCESS") || str.contains("OK") || str.contains("COMPLETED")) {
            plugin.getLogger().info("EcoMoney " + operation + ": toString enthält SUCCESS -> OK");
            return true;
        }

        // Alle boolean-Methoden loggen
        plugin.getLogger().warning("EcoMoney " + operation + ": Konnte Ergebnis nicht auswerten!");
        plugin.getLogger().warning("  Typ: " + result.getClass().getName());
        plugin.getLogger().warning("  Wert: " + result);
        for (Method m : result.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                try {
                    Object val = m.invoke(result);
                    plugin.getLogger().warning("  " + m.getName() + "() = " + val);
                } catch (Exception ignored) {}
            }
        }

        return false;
    }

    /**
     * Loggt Details eines fehlgeschlagenen TransactionResult (getMessage, getAmount, etc.)
     */
    private void logResultDetails(Object result, String operation) {
        String[] detailMethods = {"getMessage", "getReason", "getErrorMessage", "getAmount"};
        for (String methodName : detailMethods) {
            try {
                Method m = result.getClass().getMethod(methodName);
                Object value = m.invoke(result);
                if (value != null) {
                    plugin.getLogger().warning("EcoMoney " + operation + " FEHLGESCHLAGEN: " + methodName + "() = " + value);
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {}
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getBalance(UUID uuid) {
        if (!enabled) return 0;
        try {
            Object result = getBalanceMethod.invoke(ecoMoneyAPI, uuid);
            if (result instanceof BigDecimal bd) return bd.doubleValue();
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("EcoMoney getBalance Fehler: " + e.getMessage());
        }
        return 0;
    }

    public boolean has(UUID uuid, double amount) {
        if (!enabled) return false;
        // Fallback wenn keine has-Methode
        if (hasMethod == null) {
            return getBalance(uuid) >= amount;
        }
        try {
            Object result = hasMethod.invoke(ecoMoneyAPI, uuid, BigDecimal.valueOf(amount));
            if (result instanceof Boolean b) return b;
        } catch (Exception e) {
            plugin.getLogger().warning("EcoMoney has Fehler: " + e.getMessage());
        }
        // Fallback
        return getBalance(uuid) >= amount;
    }

    public boolean withdraw(UUID uuid, double amount, String reason) {
        if (!enabled || amount <= 0) return false;
        try {
            BigDecimal bdAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            plugin.getLogger().info("EcoMoney withdraw: UUID=" + uuid + " Amount=" + bdAmount + " Reason=" + reason);
            Object result;
            if (withdrawHasReason) {
                result = withdrawMethod.invoke(ecoMoneyAPI, uuid, bdAmount, reason);
            } else {
                result = withdrawMethod.invoke(ecoMoneyAPI, uuid, bdAmount);
            }
            return checkTransactionSuccess(result, "withdraw");
        } catch (Exception e) {
            plugin.getLogger().warning("EcoMoney withdraw Fehler: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean deposit(UUID uuid, double amount, String reason) {
        if (!enabled || amount <= 0) return false;
        try {
            BigDecimal bdAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            plugin.getLogger().info("EcoMoney deposit: UUID=" + uuid + " Amount=" + bdAmount + " Reason=" + reason);
            Object result;
            if (depositHasReason) {
                result = depositMethod.invoke(ecoMoneyAPI, uuid, bdAmount, reason);
            } else {
                result = depositMethod.invoke(ecoMoneyAPI, uuid, bdAmount);
            }
            return checkTransactionSuccess(result, "deposit");
        } catch (Exception e) {
            plugin.getLogger().warning("EcoMoney deposit Fehler: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public String formatMoney(double amount) {
        if (!enabled || formatMoneyMethod == null) {
            return String.format("%.2f$", amount);
        }
        try {
            Object result = formatMoneyMethod.invoke(ecoMoneyAPI, BigDecimal.valueOf(amount));
            if (result instanceof String s) return s;
        } catch (Exception ignored) {}
        return String.format("%.2f$", amount);
    }
}
