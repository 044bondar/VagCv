package com.vagcv.garage.scheduling;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;

/**
 * Читання параметрів із (змерджованого) JobDataMap з ЯВНОЮ політикою:
 *  - обов'язковий ключ відсутній / з помилкою в назві / не число -> JobExecutionException БЕЗ повторного запуску
 *    (повторювати нема сенсу: конфігурація не виправиться сама);
 *  - необов'язковий ключ відсутній -> значення за замовчуванням.
 */
public final class JobParams {

    private JobParams() {
    }

    public static int requiredNonNegativeInt(JobDataMap map, String key) throws JobExecutionException {
        if (!map.containsKey(key)) {
            throw new JobExecutionException("Missing required JobDataMap key '" + key
                    + "' (check spelling). Keys present: " + java.util.Arrays.toString(map.getKeys()));
        }
        int value = toInt(map.get(key), key);
        if (value < 0) {
            throw new JobExecutionException("JobDataMap key '" + key + "' must be >= 0 but was " + value);
        }
        return value;
    }

    public static int intOrDefault(JobDataMap map, String key, int defaultValue) throws JobExecutionException {
        return map.containsKey(key) ? toInt(map.get(key), key) : defaultValue;
    }

    public static boolean booleanOrDefault(JobDataMap map, String key, boolean defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }
        Object v = map.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static int toInt(Object raw, String key) throws JobExecutionException {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new JobExecutionException("JobDataMap key '" + key + "' is not a number: " + raw);
        }
    }
}
