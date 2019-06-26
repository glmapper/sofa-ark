/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.ark.container.model;

import com.alipay.sofa.ark.bootstrap.MainMethodRunner;
import com.alipay.sofa.ark.common.util.AssertUtils;
import com.alipay.sofa.ark.common.util.BizIdentityUtils;
import com.alipay.sofa.ark.common.util.ClassLoaderUtils;
import com.alipay.sofa.ark.common.util.ClassUtils;
import com.alipay.sofa.ark.common.util.StringUtils;
import com.alipay.sofa.ark.container.service.ArkServiceContainerHolder;
import com.alipay.sofa.ark.exception.ArkRuntimeException;
import com.alipay.sofa.ark.spi.constant.Constants;
import com.alipay.sofa.ark.spi.event.BizEvent;
import com.alipay.sofa.ark.spi.model.Biz;
import com.alipay.sofa.ark.spi.model.BizState;
import com.alipay.sofa.ark.spi.service.biz.BizManagerService;
import com.alipay.sofa.ark.spi.service.event.EventAdminService;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Ark Biz Standard Model
 *
 * @author ruoshan
 * @since 0.1.0
 */
public class BizModel implements Biz {

    private String      bizName;

    private String      bizVersion;

    private BizState    bizState;

    private String      mainClass;

    private String      webContextPath;

    private URL[]       urls;

    private ClassLoader classLoader;

    private int         priority               = DEFAULT_PRECEDENCE;

    private Set<String> denyImportPackages;

    private Set<String> denyImportPackageNodes = new HashSet<>();

    private Set<String> denyImportPackageStems = new HashSet<>();

    private Set<String> denyImportClasses;

    private Set<String> denyImportResources;

    public BizModel setBizName(String bizName) {
        AssertUtils.isFalse(StringUtils.isEmpty(bizName), "Biz Name must not be empty!");
        this.bizName = bizName;
        return this;
    }

    public BizModel setBizVersion(String bizVersion) {
        AssertUtils.isFalse(StringUtils.isEmpty(bizVersion), "Biz Version must not be empty!");
        this.bizVersion = bizVersion;
        return this;
    }

    public BizModel setBizState(BizState bizState) {
        this.bizState = bizState;
        return this;
    }

    public BizModel setMainClass(String mainClass) {
        AssertUtils.isFalse(StringUtils.isEmpty(mainClass), "Biz Main Class must not be empty!");
        this.mainClass = mainClass;
        return this;
    }

    public BizModel setClassPath(URL[] urls) {
        this.urls = urls;
        return this;
    }

    public BizModel setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public BizModel setPriority(String priority) {
        this.priority = (priority == null ? DEFAULT_PRECEDENCE : Integer.valueOf(priority));
        return this;
    }

    public BizModel setWebContextPath(String webContextPath) {
        this.webContextPath = (webContextPath == null ? Constants.ROOT_WEB_CONTEXT_PATH
            : webContextPath);
        return this;
    }

    public BizModel setDenyImportPackages(String denyImportPackages) {
        this.denyImportPackages = StringUtils.strToSet(denyImportPackages,
            Constants.MANIFEST_VALUE_SPLIT);
        parsePackageNodeAndStem(this.denyImportPackages, this.denyImportPackageStems,
            this.denyImportPackageNodes);
        return this;
    }

    public BizModel setDenyImportClasses(String denyImportClasses) {
        this.denyImportClasses = StringUtils.strToSet(denyImportClasses,
            Constants.MANIFEST_VALUE_SPLIT);
        return this;
    }

    public BizModel setDenyImportResources(String denyImportResources) {
        this.denyImportResources = StringUtils.strToSet(denyImportResources,
            Constants.MANIFEST_VALUE_SPLIT);
        return this;
    }

    @Override
    public String getBizName() {
        return bizName;
    }

    @Override
    public String getBizVersion() {
        return bizVersion;
    }

    @Override
    public String getIdentity() {
        return BizIdentityUtils.generateBizIdentity(this);
    }

    @Override
    public String getMainClass() {
        return mainClass;
    }

    @Override
    public URL[] getClassPath() {
        return urls;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public ClassLoader getBizClassLoader() {
        return classLoader;
    }

    @Override
    public Set<String> getDenyImportPackages() {
        return denyImportPackages;
    }

    @Override
    public Set<String> getDenyImportPackageNodes() {
        return denyImportPackageNodes;
    }

    @Override
    public Set<String> getDenyImportPackageStems() {
        return denyImportPackageStems;
    }

    @Override
    public Set<String> getDenyImportClasses() {
        return denyImportClasses;
    }

    @Override
    public Set<String> getDenyImportResources() {
        return denyImportResources;
    }

    @Override
    public void start(String[] args) throws Throwable {
        // 这里的 check 一下 当前 ark-biz 包的状态，必须是已经解析过的
        AssertUtils.isTrue(bizState == BizState.RESOLVED, "BizState must be RESOLVED");
        if (mainClass == null) {
            throw new ArkRuntimeException(String.format("biz: %s has no main method", getBizName()));
        }

        // 将当前 classLoader 放入到线程上下文中，然后返回 线程上下文中的老的 classLoader
        ClassLoader oldClassLoader = ClassLoaderUtils.pushContextClassLoader(this.classLoader);
        try {
            resetProperties();
            // 构建 MainMethodRunner
            MainMethodRunner mainMethodRunner = new MainMethodRunner(mainClass, args);
            // 启动，里面通过 反射调用 main 方法的方式进行启动的
            mainMethodRunner.run();
            EventAdminService eventAdminService = ArkServiceContainerHolder.getContainer()
                .getService(EventAdminService.class);
            // this can trigger health checker handler
            eventAdminService.sendEvent(new BizEvent(this,
                Constants.BIZ_EVENT_TOPIC_AFTER_INVOKE_BIZ_START));
        } catch (Throwable e) {
            bizState = BizState.BROKEN;
            throw e;
        } finally {
            // 将 oldClassLoader 设置回 线程上下文
            ClassLoaderUtils.popContextClassLoader(oldClassLoader);
        }

        BizManagerService bizManagerService = ArkServiceContainerHolder.getContainer().getService(
            BizManagerService.class);
        if (bizManagerService.getActiveBiz(bizName) == null) {
            bizState = BizState.ACTIVATED;
        } else {
            bizState = BizState.DEACTIVATED;
        }
    }

    @Override
    public void stop() {
        AssertUtils.isTrue(bizState == BizState.ACTIVATED || bizState == BizState.DEACTIVATED
                           || bizState == BizState.BROKEN,
            "BizState must be ACTIVATED, DEACTIVATED or BROKEN.");
        bizState = BizState.DEACTIVATED;
        try {
            EventAdminService eventAdminService = ArkServiceContainerHolder.getContainer()
                .getService(EventAdminService.class);
            // this can trigger uninstall handler
            eventAdminService.sendEvent(new BizEvent(this,
                Constants.BIZ_EVENT_TOPIC_AFTER_INVOKE_BIZ_STOP));
        } finally {
            BizManagerService bizManagerService = ArkServiceContainerHolder.getContainer()
                .getService(BizManagerService.class);
            bizManagerService.unRegisterBiz(bizName, bizVersion);
            bizState = BizState.UNRESOLVED;
            urls = null;
            classLoader = null;
            denyImportPackages = null;
            denyImportClasses = null;
            denyImportResources = null;
        }
    }

    @Override
    public BizState getBizState() {
        return bizState;
    }

    @Override
    public String getWebContextPath() {
        return webContextPath;
    }

    @Override
    public String toString() {
        return "Ark Biz: " + getIdentity();
    }

    private void parsePackageNodeAndStem(Set<String> candidates, Set<String> stems,
                                         Set<String> nodes) {
        for (String pkgPattern : candidates) {
            if (pkgPattern.endsWith(Constants.PACKAGE_PREFIX_MARK)) {
                stems.add(ClassUtils.getPackageName(pkgPattern));
            } else {
                nodes.add(pkgPattern);
            }
        }
    }

    private void resetProperties() {
        System.getProperties().remove("logging.path");
    }
}