package com.monglife.discovery.app.gateway.global.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglife.core.utils.CommonUtil;
import com.monglife.discovery.app.gateway.vo.TraceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HttpUtils {
    
    private final ObjectMapper objectMapper;

    /**
     * 클라이언트 실제 IP 를 구하는 리졸버.
     *
     * nginx 가 X-Forwarded-For 를 붙여서 넘기므로 remoteAddress 를 그대로 쓰면
     * 클라이언트가 아니라 nginx 컨테이너 IP 가 나온다.
     *
     * maxTrustedIndex(1) 은 XFF 의 **마지막** 값, 즉 nginx 가 직접 본 IP 만 신뢰한다.
     * 클라이언트가 XFF 를 위조해 보내도 nginx 가 뒤에 실제 IP 를 덧붙이므로 밀리지 않는다.
     * 프록시 단을 하나 더 두면 그 수만큼 이 값을 올려야 한다.
     */
    private final RemoteAddressResolver remoteAddressResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    public TraceVo increaseAndGetTrace(ServerWebExchange exchange) {

        String traceId = exchange.getAttributeOrDefault("traceId", CommonUtil.randomId());
        int traceOffset = Integer.parseInt(exchange.getAttributeOrDefault("traceOffset", "-1")) + 1;

        exchange.getAttributes().put("traceId", traceId);
        exchange.getAttributes().put("traceOffset", String.valueOf(traceOffset));
        exchange.getRequest().mutate().header("X-Trace-Id", URLEncoder.encode(traceId, StandardCharsets.UTF_8)).build();

        return TraceVo.builder()
                .traceId(traceId)
                .traceOffset(traceOffset)
                .build();
    }

    public void decreaseTraceOffset(ServerWebExchange exchange) {

        String traceOffset = exchange.getAttribute("traceOffset");

        if (traceOffset != null && !traceOffset.isBlank() && traceOffset.matches("-?\\d+")) {
            exchange.getAttributes().put("traceOffset", String.valueOf(Integer.parseInt(traceOffset) - 1));
        }
    }

    /**
     * 클라이언트 실제 IP 조회 (알 수 없으면 "unknown")
     */
    public String getClientIp(ServerWebExchange exchange) {
        InetSocketAddress address = remoteAddressResolver.resolve(exchange);
        return address != null ? address.getHostString() : "unknown";
    }

    public Optional<String> getHeader(ServerHttpRequest request, String key) {
        List<String> values = request.getHeaders().get(key);

        if (values != null && !values.isEmpty()) {
            return Optional.ofNullable(values.get(0));
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> getJsonString(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return Optional.ofNullable(json);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public <T> Mono<Void> setResponse(ServerWebExchange exchange, T responseDto) {

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

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
