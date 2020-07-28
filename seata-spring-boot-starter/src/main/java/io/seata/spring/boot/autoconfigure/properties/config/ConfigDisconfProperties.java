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
package io.seata.spring.boot.autoconfigure.properties.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static io.seata.spring.boot.autoconfigure.StarterConstants.CONFIG_DISCONF_PREFIX;

/**
 * @author kpali@qq.com
 */
@Component
@ConfigurationProperties(prefix = CONFIG_DISCONF_PREFIX)
public class ConfigDisconfProperties {
    private String serverAddr = "127.0.0.1:8014";
    private String app = "seata-server";
    private String version = "1_0_0";
    private String env = "rd";
    private String configFile = "config.txt";
    private String zkHost = "localhost:2181";
    private int zkSessionTimeout = 6000;
    private int zkConnectTimeout = 2000;
    private String zkUsername = "";
    private String zkPassword = "";

    public String getServerAddr() {
        return serverAddr;
    }

    public ConfigDisconfProperties setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
        return this;
    }

    public String getApp() {
        return app;
    }

    public ConfigDisconfProperties setApp(String app) {
        this.app = app;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public ConfigDisconfProperties setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getEnv() {
        return env;
    }

    public ConfigDisconfProperties setEnv(String env) {
        this.env = env;
        return this;
    }

    public String getConfigFile() {
        return configFile;
    }

    public ConfigDisconfProperties setConfigFile(String configFile) {
        this.configFile = configFile;
        return this;
    }

    public String getZkHost() {
        return zkHost;
    }

    public ConfigDisconfProperties setZkHost(String zkHost) {
        this.zkHost = zkHost;
        return this;
    }

    public int getZkSessionTimeout() {
        return zkSessionTimeout;
    }

    public ConfigDisconfProperties setZkSessionTimeout(int zkSessionTimeout) {
        this.zkSessionTimeout = zkSessionTimeout;
        return this;
    }

    public int getZkConnectTimeout() {
        return zkConnectTimeout;
    }

    public ConfigDisconfProperties setZkConnectTimeout(int zkConnectTimeout) {
        this.zkConnectTimeout = zkConnectTimeout;
        return this;
    }

    public String getZkUsername() {
        return zkUsername;
    }

    public ConfigDisconfProperties setZkUsername(String zkUsername) {
        this.zkUsername = zkUsername;
        return this;
    }

    public String getZkPassword() {
        return zkPassword;
    }

    public ConfigDisconfProperties setZkPassword(String zkPassword) {
        this.zkPassword = zkPassword;
        return this;
    }
}
