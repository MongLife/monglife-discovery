package com.monglife.discovery.app.gateway.filter;

import com.monglife.discovery.app.gateway.dto.etc.AccessLogDto;
import com.monglife.discovery.app.gateway.global.config.FilterConfig;
import com.monglife.discovery.app.gateway.global.utils.HttpUtils;
import com.monglife.discovery.app.gateway.vo.TraceVo;
import com.monglife.module.common.logging.enums.LoggerType;
import com.monglife.module.common.logging.utils.LoggingUtil;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class AccessLoggingFilter extends AbstractGatewayFilterFactory<FilterConfig> {

    private final HttpUtils httpUtils;

    private final LoggingUtil loggingUtil;

    public AccessLoggingFilter(HttpUtils httpUtils, LoggingUtil loggingUtil) {
        super(FilterConfig.class);
        this.httpUtils = httpUtils;
        this.loggingUtil = loggingUtil;
    }

    @Override
    public GatewayFilter apply(FilterConfig config) {
        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();

            TraceVo traceVo = httpUtils.increaseAndGetTrace(exchange);

            if (config.isPreLogger()) {
                String className = this.getClass().getName();
                String methodName = "apply";

                AccessLogDto accessLogDto = AccessLogDto.builder()
                        .traceId(traceVo.getTraceId())
                        .traceOffset(traceVo.getTraceOffset())
                        .entryMethod(String.format("%s#%s", className, methodName))
                        .className(className)
                        .method(methodName)
                        .httpMethod(request.getMethod().name())
                        .mapping(request.getPath().value())
                        .clientIp(httpUtils.getClientIp(exchange))
                        .userAgent(httpUtils.getHeader(request, HttpHeaders.USER_AGENT).orElse("-"))
                        .build();

                httpUtils.withTrace(traceVo.getTraceId(), traceVo.getTraceOffset(),
                        () -> loggingUtil.printInfoLog(accessLogDto, LoggerType.LOGSTASH_LOGGER));
            }

            return chain.filter(exchange);
        };
    }
}
