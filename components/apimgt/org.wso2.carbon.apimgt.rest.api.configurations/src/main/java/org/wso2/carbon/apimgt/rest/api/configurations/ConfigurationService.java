/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.apimgt.rest.api.configurations;

import org.wso2.carbon.apimgt.rest.api.configurations.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.rest.api.configurations.models.EnvironmentConfigurations;

import java.util.List;

/**
 * Service class for get configurations
 */
public class ConfigurationService {
    private static ConfigurationService instance = new ConfigurationService();
    private EnvironmentConfigurations environmentConfigurations;

    private ConfigurationService() {
        environmentConfigurations = ServiceReferenceHolder.getInstance().getEnvironmentConfigurations();
    }

    public static ConfigurationService getInstance() {
        return instance;
    }

    public static String getEnvironmentLabel(){
        return getInstance().getEnvironmentConfigurations().getEnvironmentLabel();
    }

    public static List<String> getClientHosts(){
        return getInstance().getEnvironmentConfigurations().getClientHosts();
    }

    public EnvironmentConfigurations getEnvironmentConfigurations() {
        return environmentConfigurations;
    }
}