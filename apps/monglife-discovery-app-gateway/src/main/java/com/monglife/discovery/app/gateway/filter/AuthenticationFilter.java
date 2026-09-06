package com.monglife.discovery.app.gateway.filter;

import com.monglife.discovery.app.gateway.dto.etc.AuthenticationLogDto;
import com.monglife.discovery.app.gateway.global.config.FilterConfig;
import com.monglife.discovery.app.gateway.global.exception.TokenExpiredException;
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

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<FilterConfig> {

    private final WebClientService webClientService;
    private final HttpUtils httpUtils;
    private final LoggingUtil loggingUtil;

    public AuthenticationFilter(WebClientService webClientService, HttpUtils httpUtils, LoggingUtil loggingUtil) {
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
                    .substring(6)
                    .trim();

            return webClientService.verityAccessToken(accessToken, traceVo.getTraceId())
                    .onErrorMap(throwable -> {
                        httpUtils.decreaseTraceOffset(exchange);
                        throw new TokenExpiredException(accessToken);
                    })
                    .flatMap(verifyAccessTokenResponseDto -> {
                        if (config.isPreLogger()) {
                            String className = this.getClass().getName();
                            String methodName = "apply";

                            String secretAccessToken = verifyAccessTokenResponseDto.getAccessToken();

                            int startIndex = Math.max(0, secretAccessToken.length() / 4);
                            int endIndex = secretAccessToken.length();

                            secretAccessToken = secretAccessToken.substring(startIndex, endIndex) + "*".repeat(endIndex - startIndex);

                            AuthenticationLogDto authenticationLogDto = AuthenticationLogDto.builder()
                                    .traceId(traceVo.getTraceId())
                                    .traceOffset(traceVo.getTraceOffset())
                                    .entryMethod(String.format("%s#%s", className, methodName))
                                    .className(className)
                                    .method(methodName)
                                    .accessToken(secretAccessToken)
                                    .build();

                            httpUtils.withTrace(traceVo.getTraceId(), traceVo.getTraceOffset(),
                                    () -> loggingUtil.printInfoLog(authenticationLogDto, LoggerType.LOGSTASH_LOGGER));
                        }

                        return chain.filter(exchange);
                    });
        };
    }
}
