package com.netease.nim.camellia.redis.proxy.conf;

import com.netease.nim.camellia.redis.proxy.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by caojiajun on 2021/1/5
 */
public class ProxyDynamicConf {

    private static final Logger logger = LoggerFactory.getLogger(ProxyDynamicConf.class);

    private static Map<String, String> conf = new HashMap<>();
    private static final Set<DynamicConfCallback> callbackSet = new HashSet<>();
    private static final String fileName = "camellia-redis-proxy.properties";

    private static final ConcurrentHashMap<String, Integer> intCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> longCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> booleanCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> doubleCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> stringCache = new ConcurrentHashMap<>();

    static {
        reload();
        int reloadIntervalSeconds = ConfigurationUtil.getInteger(conf, "dynamic.conf.reload.interval.seconds", 600);
        ExecutorUtils.scheduleAtFixedRate(ProxyDynamicConf::reload, reloadIntervalSeconds, reloadIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 检查本地配置文件是否有变更，如果有，则重新加载，并且会清空缓存，并触发监听者的回调
     */
    public static void reload() {
        URL url = ProxyDynamicConf.class.getClassLoader().getResource(fileName);
        if (url == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} not exists", fileName);
            }
            clearCache();
            triggerCallback();
            return;
        }
        try {
            Properties props = new Properties();
            try {
                props.load(new FileInputStream(url.getPath()));
            } catch (IOException e) {
                props.load(ProxyDynamicConf.class.getClassLoader().getResourceAsStream(fileName));
            }
            Map<String, String> conf = ConfigurationUtil.propertiesToMap(props);

            //如果想用另外一个文件来配置，可以在camellia-redis-proxy.properties中配置dynamic.conf.file.path=xxx
            //xxx需要是文件的绝对路径
            String filePath = conf.get("dynamic.conf.file.path");
            if (filePath != null) {
                try {
                    Properties props1 = new Properties();
                    props1.load(new FileInputStream(filePath));
                    Map<String, String> conf1 = ConfigurationUtil.propertiesToMap(props1);
                    if (conf1 != null) {
                        conf.putAll(conf1);
                    }
                } catch (Exception e) {
                    logger.error("dynamic.conf.file.path={} load error, use classpath:{} default", filePath, fileName, e);
                }
            }

            if (conf.equals(new HashMap<>(ProxyDynamicConf.conf))) {
                if (logger.isDebugEnabled()) {
                    if (filePath != null) {
                        logger.debug("classpath:{} and {} not modify", fileName, filePath);
                    } else {
                        logger.debug("classpath:{} not modify", fileName);
                    }
                }
            } else {
                ProxyDynamicConf.conf = conf;
                if (filePath != null) {
                    logger.info("classpath:{} and {} reload success", fileName, filePath);
                } else {
                    logger.info("classpath:{} reload success", fileName);
                }
                clearCache();
                triggerCallback();
            }
        } catch (Exception e) {
            logger.error("reload error", e);
        }
    }

    /**
     * 直接把配置设置进来（k-v的map）
     */
    public static void reload(Map<String, String> conf) {
        try {
            HashMap<String, String> newConf = new HashMap<>(conf);
            if (ProxyDynamicConf.conf.equals(newConf)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("conf not modify");
                }
            } else {
                ProxyDynamicConf.conf = newConf;
                logger.info("conf reload success");
                clearCache();
                triggerCallback();
            }
        } catch (Exception e) {
            logger.error("reload error");
        }
    }

    // 触发一下监听者的回调
    private static void triggerCallback() {
        for (DynamicConfCallback callback : callbackSet) {
            try {
                callback.callback();
            } catch (Exception e) {
                logger.error("DynamicConfCallback callback error", e);
            }
        }
    }

    //清空缓存
    private static void clearCache() {
        longCache.clear();
        intCache.clear();
        booleanCache.clear();
        doubleCache.clear();
        stringCache.clear();
    }

    /**
     * 注册回调，对于ProxyDynamicConf的调用者，可以注册该回调，从而当ProxyDynamicConf发生配置变更时可以第一时间重新加载
     * @param callback 回调
     */
    public static void registerCallback(DynamicConfCallback callback) {
        if (callback != null) {
            callbackSet.add(callback);
        }
    }

    private static Integer _getInt(String key, Integer defaultValue) {
        return ConfigurationUtil.getInteger(conf, key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return _getInt(key, defaultValue);
    }

    public static int getInt(String key, Long bid, String bgroup, int defaultValue) {
        try {
            if (conf.isEmpty()) return defaultValue;
            String confKey = buildConfKey(key, bid, bgroup);
            Integer value;
            Integer cacheValue = intCache.get(confKey);
            if (cacheValue != null) return cacheValue;
            value = _getInt(confKey, null);
            if (value == null) {
                value = _getInt(key, null);
            }
            if (value == null) {
                intCache.put(confKey, defaultValue);
                return defaultValue;
            }
            intCache.put(confKey, value);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Long _getLong(String key, Long defaultValue) {
        return ConfigurationUtil.getLong(conf, key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        return _getLong(key, defaultValue);
    }

    public static long getLong(String key, Long bid, String bgroup, long defaultValue) {
        try {
            if (conf.isEmpty()) return defaultValue;
            String confKey = buildConfKey(key, bid, bgroup);
            Long value;
            Long cacheValue = longCache.get(confKey);
            if (cacheValue != null) return cacheValue;
            value = _getLong(confKey, null);
            if (value == null) {
                value = _getLong(key, null);
            }
            if (value == null) {
                longCache.put(confKey, defaultValue);
                return defaultValue;
            }
            longCache.put(confKey, value);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return ConfigurationUtil.getBoolean(conf, key, defaultValue);
    }

    public static boolean getBoolean(String key, Long bid, String bgroup, boolean defaultValue) {
        try {
            if (conf.isEmpty()) return defaultValue;
            String confKey = buildConfKey(key, bid, bgroup);
            Boolean value;
            Boolean cacheValue = booleanCache.get(confKey);
            if (cacheValue != null) return cacheValue;
            value = ConfigurationUtil.getBoolean(conf, confKey, null);
            if (value == null) {
                value = ConfigurationUtil.getBoolean(conf, key, null);
            }
            if (value == null) {
                booleanCache.put(confKey, defaultValue);
                return defaultValue;
            }
            booleanCache.put(confKey, value);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Double _getDouble(String key, Double defaultValue) {
        return ConfigurationUtil.getDouble(conf, key, defaultValue);
    }

    public static double getDouble(String key, double defaultValue) {
        return _getDouble(key, defaultValue);
    }

    public static double getDouble(String key, Long bid, String bgroup, double defaultValue) {
        try {
            if (conf.isEmpty()) return defaultValue;
            String confKey = buildConfKey(key, bid, bgroup);
            Double value;
            Double cacheValue = doubleCache.get(confKey);
            if (cacheValue != null) return cacheValue;
            value = _getDouble(confKey, null);
            if (value == null) {
                value = _getDouble(key, null);
            }
            if (value == null) {
                doubleCache.put(confKey, defaultValue);
                return defaultValue;
            }
            doubleCache.put(confKey, value);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String _getString(String key, String defaultValue) {
        return ConfigurationUtil.get(conf, key, defaultValue);
    }

    public static String getString(String key, String defaultValue) {
        return _getString(key, defaultValue);
    }

    public static String getString(String key, Long bid, String bgroup, String defaultValue) {
        try {
            if (conf.isEmpty()) return defaultValue;
            String confKey = buildConfKey(key, bid, bgroup);
            String value;
            String cacheValue = stringCache.get(confKey);
            if (cacheValue != null) return cacheValue;
            value = _getString(confKey, null);
            if (value == null) {
                value = _getString(key, null);
            }
            if (value == null) {
                stringCache.put(confKey, defaultValue);
                return defaultValue;
            }
            stringCache.put(confKey, value);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String buildConfKey(String key, Long bid, String bgroup) {
        if (bid == null || bgroup == null) {
            return  "default.default." + key;
        } else {
            return bid + "." + bgroup + "." + key;
        }
    }
}
