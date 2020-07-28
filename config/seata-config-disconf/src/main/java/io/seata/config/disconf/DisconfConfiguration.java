/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.config.disconf;

import io.seata.common.exception.NotSupportYetException;
import io.seata.common.thread.NamedThreadFactory;
import io.seata.common.util.StringUtils;
import io.seata.config.*;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

import static io.seata.config.ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR;
import static io.seata.config.ConfigurationKeys.FILE_ROOT_CONFIG;

/**
 * The type Apollo configuration.
 *
 * @author kpali@qq.com
 */
public class DisconfConfiguration extends AbstractConfiguration {
    private static volatile DisconfConfiguration instance;

    private static final Logger LOGGER = LoggerFactory.getLogger(DisconfConfiguration.class);

    private static final Configuration FILE_CONFIG = ConfigurationFactory.CURRENT_FILE_INSTANCE;
    private static final String CONFIG_TYPE = "disconf";
    private static final String SERVER_ADDR_KEY = "serverAddr";
    private static final String APP = "app";
    private static final String VERSION = "version";
    private static final String ENV = "env";
    private static final String CONFIG_FILE = "configFile";
    private static final String ZK_HOST = "zkHost";
    private static final String ZK_SESSION_TIMEOUT = "zkSessionTimeout";
    private static final String ZK_CONNECT_TIMEOUT = "zkConnectTimeout";
    private static final String ZK_USERNAME = "zkUsername";
    private static final String ZK_PASSWORD = "zkPassword";
    private static final String FILE_CONFIG_KEY_PREFIX = FILE_ROOT_CONFIG + FILE_CONFIG_SPLIT_CHAR + CONFIG_TYPE
            + FILE_CONFIG_SPLIT_CHAR;

    private static final int CORE_CONFIG_OPERATE_THREAD = 1;
    private static final int MAX_CONFIG_OPERATE_THREAD = 2;
    private ExecutorService disconfExecutor;

    private static volatile ZkClient zkClient;
    private static final String SERIALIZER_KEY = "serializer";
    private static final String ZK_ROOT_PATH = "/disconf";
    private static final int MAP_INITIAL_CAPACITY = 8;
    private ConcurrentMap<String, ConcurrentMap<ConfigurationChangeListener, DisconfListener>> configListenersMap
            = new ConcurrentHashMap<>(MAP_INITIAL_CAPACITY);

    public DisconfConfiguration() {
        disconfExecutor = new ThreadPoolExecutor(CORE_CONFIG_OPERATE_THREAD,
                MAX_CONFIG_OPERATE_THREAD, Integer.MAX_VALUE, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("disconf-config-executor", MAX_CONFIG_OPERATE_THREAD));
        ZkSerializer zkSerializer  = getZkSerializer();
        String zkHost = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ZK_HOST);
        int zkSessionTimeout = Integer.parseInt(FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ZK_SESSION_TIMEOUT));
        int zkConnectTimeout = Integer.parseInt(FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ZK_CONNECT_TIMEOUT));
        String zkUsername = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ZK_USERNAME);
        String zkPassword = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ZK_PASSWORD);
        zkClient = new ZkClient(zkHost, zkSessionTimeout, zkConnectTimeout, zkSerializer);
        if (!StringUtils.isBlank(zkUsername) && !StringUtils.isBlank(zkPassword)) {
            StringBuilder auth = new StringBuilder(zkUsername).append(":").append(zkPassword);
            zkClient.addAuthInfo("digest", auth.toString().getBytes());
        }
        boolean zkRootPathExists = zkClient.exists(ZK_ROOT_PATH);
        if (!zkRootPathExists) {
            zkClient.createPersistent(ZK_ROOT_PATH, true);
        }
    }

    public static DisconfConfiguration getInstance() {
        if (null == instance) {
            synchronized (DisconfConfiguration.class) {
                if (null == instance) {
                    instance = new DisconfConfiguration();
                }
            }
        }
        return instance;
    }

    @Override
    public String getTypeName() {
        return CONFIG_TYPE;
    }

    @Override
    public String getLatestConfig(String dataId, String defaultValue, long timeoutMills) {
        String value;
        if ((value = getConfigFromSysPro(dataId)) != null) {
            return value;
        }
        String serverAddr = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + SERVER_ADDR_KEY);
        String app = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + APP);
        String env = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ENV);
        String version = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + VERSION);
        String configFile = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + CONFIG_FILE);

        ConfigFuture configFuture = new ConfigFuture(dataId, defaultValue, ConfigFuture.ConfigOperation.GET,
                timeoutMills);
        disconfExecutor.execute(() -> complete(
                getConfigUsingApi(serverAddr, app, env, version, configFile, dataId),
                configFuture
        ));
        return (String) configFuture.get();
    }

    @Override
    public boolean putConfig(String dataId, String content, long timeoutMills) {
        throw new NotSupportYetException("not support putConfig");
    }

    @Override
    public boolean putConfigIfAbsent(String dataId, String content, long timeoutMills) {
        throw new NotSupportYetException("not support atomic operation putConfigIfAbsent");
    }

    @Override
    public boolean removeConfig(String dataId, long timeoutMills) {
        throw new NotSupportYetException("not support removeConfig");
    }

    @Override
    public void addConfigListener(String dataId, ConfigurationChangeListener listener) {
        LOGGER.info("Disconf: add config listener, dataId: {}", dataId);
        if (null == dataId || null == listener) {
            return;
        }
        String path = buildZkPath();
        if (!zkClient.exists(path)) {
            zkClient.createPersistent(path, true);
        }
        configListenersMap.putIfAbsent(dataId, new ConcurrentHashMap<>());
        DisconfListener disconfListener = new DisconfListener(dataId, listener);
        configListenersMap.get(dataId).put(listener, disconfListener);
        zkClient.subscribeDataChanges(path, disconfListener);
    }

    @Override
    public void removeConfigListener(String dataId, ConfigurationChangeListener listener) {
        LOGGER.info("Disconf: remove config listener, dataId: {}", dataId);
        Set<ConfigurationChangeListener> configChangeListeners = getConfigListeners(dataId);
        if (configChangeListeners == null || listener == null) {
            return;
        }
        String path = buildZkPath();
        if (zkClient.exists(path)) {
            for (ConfigurationChangeListener entry : configChangeListeners) {
                if (listener.equals(entry)) {
                    DisconfListener disconfListener = null;
                    if (configListenersMap.containsKey(dataId)) {
                        disconfListener = configListenersMap.get(dataId).get(listener);
                        configListenersMap.get(dataId).remove(entry);
                    }
                    if (disconfListener != null) {
                        zkClient.unsubscribeDataChanges(path, disconfListener);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public Set<ConfigurationChangeListener> getConfigListeners(String dataId) {
        if (configListenersMap.containsKey(dataId)) {
            return configListenersMap.get(dataId).keySet();
        } else {
            return null;
        }
    }

    /**
     * complete the future
     *
     * @param configValue
     * @param configFuture
     */
    private void complete(String configValue, ConfigFuture configFuture) {
        if (configValue != null) {
            configFuture.setResult(configValue);
        }
    }

    private String getConfigUsingApi(String serverAddr, String app, String env, String version, String configFile, String key) {
        LOGGER.info("Disconf: get config [" + key+ "] ...");
        String url = "http://" + serverAddr + "/api/config/file?app=" + app + "&env=" + env + "&version=" + version + "&key=" + configFile;

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String content = null;
        try {
            httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(url);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(35000)
                    .setConnectionRequestTimeout(35000)
                    .setSocketTimeout(60000)
                    .build();
            httpGet.setConfig(requestConfig);
            response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            content = EntityUtils.toString(entity);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String value = null;
        if (content != null) {
            Properties prop = new Properties();
            try {
                prop.load(new ByteArrayInputStream(content.getBytes()));
                for (String propName : prop.stringPropertyNames()) {
                    if (propName.equals(key)) {
                        value = prop.getProperty(propName);
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        LOGGER.info("Disconf: " + key + "=" + value);
        return value;
    }

    public static class DisconfListener implements IZkDataListener {
        private final String dataId;
        private final ConfigurationChangeListener listener;

        public DisconfListener(String dataId, ConfigurationChangeListener listener) {
            this.dataId = dataId;
            this.listener = listener;
        }

        public ConfigurationChangeListener getTargetListener() {
            return this.listener;
        }

        @Override
        public void handleDataChange(String dataPath, Object data) throws Exception {
            String newValue = null;
            if (data != null) {
                String content = asciiToNative(data.toString());
                if (content.startsWith("\"")) {
                    content = content.substring(1);
                }
                if (content.endsWith("\"")) {
                    content = content.substring(0, content.length() - 1);
                }
                content = content.replace("\\n", "\n");

                Properties prop = new Properties();
                prop.load(new ByteArrayInputStream(content.getBytes()));
                for (String propName : prop.stringPropertyNames()) {
                    if (propName.equals(dataId)) {
                        newValue = prop.getProperty(propName);
                        break;
                    }
                }
            }
            LOGGER.info("Disconf: config modify, " + dataId + "=" + newValue);

            ConfigurationChangeEvent event = new ConfigurationChangeEvent().setDataId(dataId).setNewValue(newValue)
                    .setChangeType(ConfigurationChangeType.MODIFY);
            listener.onProcessEvent(event);
        }

        @Override
        public void handleDataDeleted(String dataPath) throws Exception {
            ConfigurationChangeEvent event = new ConfigurationChangeEvent().setDataId(dataId).setChangeType(
                    ConfigurationChangeType.DELETE);
            listener.onProcessEvent(event);
        }

        private static String asciiToNative(String asciicode) {
            String[] asciis = asciicode.split("\\\\u");
            String nativeValue = asciis[0];
            try {
                for (int i = 1; i < asciis.length; i++) {
                    String code = asciis[i];
                    nativeValue += (char) Integer.parseInt(code.substring(0, 4), 16);
                    if (code.length() > 4) {
                        nativeValue += code.substring(4, code.length());
                    }
                }
            } catch (NumberFormatException e) {
                return asciicode;
            }
            return nativeValue;
        }
    }

    private ZkSerializer getZkSerializer() {
        ZkSerializer zkSerializer = null;
        String serializer = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + SERIALIZER_KEY);
        if (StringUtils.isNotBlank(serializer)) {
            try {
                Class<?> clazz = Class.forName(serializer);
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                zkSerializer = (ZkSerializer) constructor.newInstance();
            } catch (ClassNotFoundException cfe) {
                LOGGER.warn("No zk serializer class found, serializer:{}", serializer, cfe);
            } catch (Throwable cause) {
                LOGGER.warn("found zk serializer encountered an unknown exception", cause);
            }
        }
        if (zkSerializer == null) {
            zkSerializer = new DefaultZkSerializer();
            LOGGER.info("Use default zk serializer: io.seata.config.disconf.DefaultZkSerializer.");
        }
        return zkSerializer;
    }

    private String buildZkPath() {
        String app = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + APP);
        String env = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + ENV);
        String version = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + VERSION);
        String configFile = FILE_CONFIG.getConfig(FILE_CONFIG_KEY_PREFIX + CONFIG_FILE);
        return String.format("%s/%s_%s_%s/file/%s", ZK_ROOT_PATH, app, version, env, configFile);
    }
}
