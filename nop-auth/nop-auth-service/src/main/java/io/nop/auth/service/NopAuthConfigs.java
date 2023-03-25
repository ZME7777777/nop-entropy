/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.auth.core.AuthCoreConfigs;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopAuthConfigs {
    @Description("访问令牌的超时时间，单位为秒")
    IConfigReference<Integer> CFG_AUTH_ACCESS_TOKEN_EXPIRE_SECONDS = varRef("nop.auth.access-token-expire-seconds",
            Integer.class, 30 * 60);

    @Description("更新令牌的超时时间，单位为秒")
    IConfigReference<Integer> CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS = varRef("nop.auth.refresh-token-expire-seconds",
            Integer.class, 300 * 60);

    @Description("系统菜单缓存的超时时间")
    IConfigReference<Duration> CFG_AUTH_SITE_MAP_CACHE_TIMEOUT = varRef("nop.auth.site-map.cache-timeout",
            Duration.class, Duration.of(10, ChronoUnit.MINUTES));

    @Description("静态配置的菜单文件路径")
    IConfigReference<String> CFG_AUTH_SITE_MAP_STATIC_CONFIG_PATH = varRef("nop.auth.site-map.static-config-path",
            String.class, null);

    @Description("是否启用前端调试模式")
    IConfigReference<Boolean> CFG_AUTH_SITE_MAP_SUPPORT_DEBUG = AuthCoreConfigs.CFG_AUTH_SITE_MAP_SUPPORT_DEBUG;

    @Description("是否自动创建缺省用户")
    IConfigReference<Boolean> CFG_AUTH_ALLOW_CREATE_DEFAULT_USER = varRef("nop.auth.login.allow-create-default-user",
            Boolean.class, false);

    @Description("连续登录验证失败之后会临时禁用用户一段时间。管理员可以到后台解锁")
    IConfigReference<Integer> CFG_AUTH_MAX_LOGIN_FAIL_COUNT = varRef("nop.auth.login.max-login-fail-count",
            Integer.class, 10);

    @Description("是否使用验证码机制")
    IConfigReference<Boolean> CFG_AUTH_VERIFY_CODE_ENABLED = varRef("nop.auth.login.verify-code.enabled", Boolean.class,
            false);
}