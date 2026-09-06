package com.monglife.discovery.app.gateway.filter;

import com.monglife.core.vo.passport.PassportDataVo;
import com.monglife.core.vo.passport.PassportVo;
import com.monglife.discovery.app.gateway.dto.etc.GeneratePassportLogDto;
import com.monglife.discovery.app.gateway.global.config.FilterConfig;
import com.monglife.discovery.app.gateway.global.exception.PassportGenerateException;
import com.monglife.discovery.app.gateway.global.exception.TokenNotFoundException;
import com.monglife.discovery.app.gateway.global.utils.HttpUtils;
import com.monglife.discovery.app.gateway.service.WebClientService;
import com.monglife.discovery.app.gateway.vo.TraceVo;
import com.monglife.module.common.logging.enums.LoggerType;
import com.monglife.module.common.logging.utils.LoggingUtil;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class GeneratePassportFilter extends AbstractGatewayFilterFactory<FilterConfig> {

    private final WebClientService webClientService;
    private final HttpUtils httpUtils;
    private final LoggingUtil loggingUtil;

    public GeneratePassportFilter(WebClientService webClientService, HttpUtils httpUtils, LoggingUtil loggingUtil) {
        super(FilterConfig.class);
        this.webClientService = webClientService;
        this.httpUtils = httpUtils;
        this.loggingUtil = loggingUtil;
    }

    @Override
    public GatewayFilter apply(FilterConfig config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            TraceVo traceVo = httpUtils.increaseAndGetTrace(exchange);

            String accessToken = httpUtils.getHeader(request, "Authorization")
                    .orElseThrow(TokenNotFoundException::new)
                    .substring(7);

            return webClientService.getPassportData(accessToken, traceVo.getTraceId())
                    .onErrorMap(throwable -> {
                        httpUtils.decreaseTraceOffset(exchange);
                        throw new PassportGenerateException(accessToken);
                    })
                    .flatMap(passportDataVo -> {
                        PassportVo passportVo = PassportVo.builder()
                                .data(PassportDataVo.builder()
                                        .account(passportDataVo.getPassportDataAccountVo())
                                        .appVersion(passportDataVo.getPassportDataAppVersionVo())
                                        .build())
                                .createdAt(LocalDateTime.now())
                                .build();

                        String passportJson = httpUtils.getJsonString(passportVo)
                                .orElseThrow(() -> new PassportGenerateException(accessToken));

                        request.mutate().header("passport", URLEncoder.encode(passportJson, StandardCharsets.UTF_8)).build();

                        if (config.isPreLogger()) {
                            String className = this.getClass().getName();
                            String methodName = "apply";

                            GeneratePassportLogDto generatePassportLogDto = GeneratePassportLogDto.builder()
                                    .traceId(traceVo.getTraceId())
                                    .traceOffset(traceVo.getTraceOffset())
                                    .entryMethod(String.format("%s#%s", className, methodName))
                                    .className(className)
                                    .method(methodName)
                                    .accountId(passportVo.getData().getAccount().getAccountId())
                                    .appPackageName(passportVo.getData().getAppVersion().getAppPackageName())
                                    .buildVersion(passportVo.getData().getAppVersion().getBuildVersion())
                                    .build();

                            httpUtils.withTrace(traceVo.getTraceId(), traceVo.getTraceOffset(),
                                    () -> loggingUtil.printInfoLog(generatePassportLogDto, LoggerType.LOGSTASH_LOGGER));
                        }

                        return chain.filter(exchange);
                    });
        };
    }
}
