/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.conf;

import org.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.skywalking.apm.agent.core.logging.api.ILog;
import org.skywalking.apm.agent.core.logging.api.LogManager;
import org.skywalking.apm.util.ConfigInitializer;
import org.skywalking.apm.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 *
 * @author wusheng
 * @see {@link #initialize()}, to learn more about how to initialzie.
 */
public class SnifferConfigInitializer {
    private static final ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);
    private static String CONFIG_FILE_NAME = "/config/agent.config";
    private static String ENV_KEY_PREFIX = "skywalking.";

    /**
     * Try to locate `agent.config`, which should be in the /config dictionary of agent package.
     * <p>
     * Also try to override the config by system.env and system.properties. All the keys in these two places should
     * start with {@link #ENV_KEY_PREFIX}. e.g. in env `skywalking.agent.application_code=yourAppName` to override
     * `agent.application_code` in config file.
     * <p>
     * At the end, `agent.application_code` and `collector.servers` must be not blank.
     */
    public static void initialize() throws ConfigNotFoundException, AgentPackageNotFoundException {
        InputStream configFileStream;

        // 从配置文件加载配置
        try {
            configFileStream = loadConfigFromAgentFolder();
            Properties properties = new Properties();
            properties.load(configFileStream);
            ConfigInitializer.initialize(properties, Config.class);
        } catch (Exception e) {
            logger.error(e, "Failed to read the config file, skywalking is going to run in default config.");
        }

        // 从环境变量覆盖配置
        try {
            overrideConfigBySystemEnv();
        } catch (Exception e) {
            logger.error(e, "Failed to read the system env.");
        }

        // 校验配置是否正确
        if (StringUtil.isEmpty(Config.Agent.APPLICATION_CODE)) {
            throw new ExceptionInInitializerError("`agent.application_code` is missing.");
        }
        if (StringUtil.isEmpty(Config.Collector.SERVERS)) {
            throw new ExceptionInInitializerError("`collector.servers` is missing.");
        }
    }

    /**
     * Override the config by system env. The env key must start with `skywalking`, the reuslt should be as same as in
     * `agent.config`
     * <p>
     * such as:
     * Env key of `agent.application_code` shoule be `skywalking.agent.application_code`
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static void overrideConfigBySystemEnv() throws IllegalAccessException {
        Properties properties = new Properties();

        // JVM 进程的环境变量
        Properties systemProperties = System.getProperties();
        Iterator<Map.Entry<Object, Object>> entryIterator = systemProperties.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Object, Object> prop = entryIterator.next();
            if (prop.getKey().toString().startsWith(ENV_KEY_PREFIX)) {
                String realKey = prop.getKey().toString().substring(ENV_KEY_PREFIX.length());
                properties.put(realKey, prop.getValue());
            }
        }

        // 系统的环境变量
        Map<String, String> envs = System.getenv();
        for (String envKey : envs.keySet()) {
            if (envKey.startsWith(ENV_KEY_PREFIX)) {
                String realKey = envKey.substring(ENV_KEY_PREFIX.length());
                properties.setProperty(realKey, envs.get(envKey));
            }
        }

        // 覆盖到 Config 对象
        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, Config.class);
        }
    }

    /**
     * Load the config file, where the agent jar is.
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static InputStream loadConfigFromAgentFolder() throws AgentPackageNotFoundException, ConfigNotFoundException {
        File configFile = new File(AgentPackagePath.getPath(), CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("Config file found in {}.", configFile);

                return new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Fail to load agent.config", e);
            }
        }
        throw new ConfigNotFoundException("Fail to load agent config file.");
    }
}
