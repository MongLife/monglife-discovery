package com.monglife.discovery.app.gateway.global.exception;

import org.springframework.web.server.ResponseStatusException;
import com.monglife.core.dto.response.ResponseDto;
import com.monglife.core.enums.error.ErrorCode;
import com.monglife.core.enums.response.GlobalResponse;
import com.monglife.core.enums.response.Response;
import com.monglife.core.exception.ErrorException;
import com.monglife.core.utils.CommonUtil;
import com.monglife.discovery.app.gateway.dto.etc.RequestExceptionLogDto;
import com.monglife.discovery.app.gateway.global.response.GatewayErrorCode;
import com.monglife.discovery.app.gateway.global.utils.HttpUtils;
import com.monglife.module.common.logging.enums.LoggerType;
import com.monglife.module.common.logging.utils.ArgsUtil;
import com.monglife.module.common.logging.utils.LoggingUtil;
import io.micrometer.common.lang.NonNullApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.Collections;
import java.util.Map;

@Order(-1)
@Component
@NonNullApi
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ArgsUtil argsUtil;

    private final LoggingUtil loggingUtil;

    private final HttpUtils httpUtils;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable e) {
        String traceId = exchange.getAttributeOrDefault("traceId", CommonUtil.randomId());
        int traceOffset = Integer.parseInt(exchange.getAttributeOrDefault("traceOffset", "-1")) + 1;

        exchange.getAttributes().put("traceId", traceId);
        exchange.getAttributes().put("traceOffset", String.valueOf(traceOffset));

        String className = this.getClass().getName();
        String methodName = "handle";

        /* 라우트에 매칭되지 않은 요청은 AccessLoggingFilter 를 타지 않으므로 여기서 요청 정보를 남긴다 */
        ServerHttpRequest request = exchange.getRequest();
        String message = String.format("%s %s - %s", request.getMethod().name(), request.getPath().value(), e.getMessage());

        RequestExceptionLogDto exceptionLogDto = RequestExceptionLogDto.builder()
                .traceId(traceId)
                .traceOffset(traceOffset)
                .entryMethod("")
                .className(className)
                .method(methodName)
                .message(message)
                .stackTrace(argsUtil.generateExceptionTrace(e))
                .clientIp(httpUtils.getClientIp(exchange))
                .userAgent(httpUtils.getHeader(request, HttpHeaders.USER_AGENT).orElse("-"))
                .build();

        /* 시스템 정의 예외 처리 */
        if (e instanceof TokenExpiredException errorException) {
            httpUtils.withTrace(traceId, traceOffset, () -> loggingUtil.printInfoLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER));
            return setErrorResponse(exchange, errorException.getErrorCode(), errorException.getResult(), HttpStatus.UNAUTHORIZED);
        } else if (e instanceof TokenNotFoundException errorException) {
            httpUtils.withTrace(traceId, traceOffset, () -> loggingUtil.printInfoLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER));
            return setErrorResponse(exchange, errorException.getErrorCode(), errorException.getResult(), HttpStatus.BAD_REQUEST);
        } else if (e instanceof ErrorException errorException) {
            httpUtils.withTrace(traceId, traceOffset, () -> {
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.CONSOLE_LOGGER);
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER);
            });
            return setErrorResponse(exchange, errorException.getErrorCode(), errorException.getResult(), HttpStatus.INTERNAL_SERVER_ERROR);
        } else if (e instanceof NotFoundException || e instanceof ConnectException || e instanceof WebClientRequestException) {
            httpUtils.withTrace(traceId, traceOffset, () -> {
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.CONSOLE_LOGGER);
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER);
            });
            return setErrorResponse(exchange, GatewayErrorCode.DISCOVERY_GATEWAY_CONNECT_FAIL, Collections.emptyMap(), HttpStatus.INTERNAL_SERVER_ERROR);
        } else if (e instanceof ResponseStatusException responseStatusException) {
            HttpStatusCode httpStatus = responseStatusException.getStatusCode();

            /* 없는 경로 호출 등 클라이언트 잘못이므로 서버 에러 로그로 남기지 않는다 */
            httpUtils.withTrace(traceId, traceOffset, () -> {
                if (httpStatus.is4xxClientError()) {
                    loggingUtil.printInfoLog(exceptionLogDto, LoggerType.CONSOLE_LOGGER);
                    loggingUtil.printInfoLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER);
                } else {
                    loggingUtil.printErrorLog(exceptionLogDto, LoggerType.CONSOLE_LOGGER);
                    loggingUtil.printErrorLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER);
                }
            });

            return setErrorResponse(exchange, GatewayErrorCode.DISCOVERY_GATEWAY_NOT_FOUND, Collections.emptyMap(), httpStatus);
        } else {
            httpUtils.withTrace(traceId, traceOffset, () -> {
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.CONSOLE_LOGGER);
                loggingUtil.printErrorLog(exceptionLogDto, LoggerType.LOGSTASH_LOGGER);
            });
            return setErrorResponse(exchange, GlobalResponse.INTERNAL_SERVER_ERROR, Collections.emptyMap());
        }
    }

    private Mono<Void> setErrorResponse(ServerWebExchange exchange, ErrorCode errorCode, Map<String, ?> result, HttpStatusCode httpStatus) {

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(httpStatus);
        ResponseDto<Map<String, ?>> responseDto = errorCode.toResponseDto(httpStatus.value(), result);

        return exchange.getResponse().writeWith(
                new Jackson2JsonEncoder()
                        .encode(Mono.just(responseDto),
                                exchange.getResponse().bufferFactory(),
                                ResolvableType.forInstance(responseDto),
                                MediaType.APPLICATION_JSON,
                                Hints.from(Hints.LOG_PREFIX_HINT, exchange.getLogPrefix()))
        );
    }

    private Mono<Void> setErrorResponse(ServerWebExchange exchange, Response response, Map<String, ?> result) {

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(response.getHttpStatus()));
        ResponseDto<Map<String, ?>> responseDto = response.toResponseDto(result);

        return exchange.getResponse().writeWith(
                new Jackson2JsonEncoder()
                        .encode(Mono.just(responseDto),
                                exchange.getResponse().bufferFactory(),
                                ResolvableType.forInstance(responseDto),
                                MediaType.APPLICATION_JSON,
                                Hints.from(Hints.LOG_PREFIX_HINT, exchange.getLogPrefix()))
        );
    }
}
