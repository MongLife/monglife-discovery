package com.monglife.discovery.app.gateway.global.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglife.core.utils.CommonUtil;
import com.monglife.discovery.app.gateway.vo.TraceVo;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
     * MDC 에 추적 정보를 채운 상태로 로깅을 실행한다.
     *
     * 게이트웨이는 traceId 를 MDC 가 아니라 exchange 속성으로 들고 다녀서 콘솔 패턴의
     * %X{traceId} 자리가 비어 있었다. 로깅 구간만 MDC 를 채워 그 자리를 메운다.
     *
     * ES 필드는 여기서 만들지 않는다. logstash 파이프라인이 message 안의 LogDto JSON 을
     * 풀어 주므로, LogDto 에 담은 값은 그대로 최상위 필드가 된다.
     *
     * WebFlux 라 MDC 를 리액티브 체인 전체로 전파할 수는 없지만, 로깅 호출은 모두
     * 동기 구간에서 일어나므로 그 구간만 감싸면 된다.
     *
     * ⚠ 이벤트 루프 스레드는 요청 사이에 재사용된다. 지우지 않으면 다음 요청 로그에
     *   남의 traceId 가 섞이므로 finally 에서 반드시 지운다.
     */
    public void withTrace(String traceId, int traceOffset, Runnable logging) {
        MDC.put("traceId", traceId);
        MDC.put("traceOffset", String.valueOf(traceOffset));

        try {
            logging.run();
        } finally {
            MDC.remove("traceId");
            MDC.remove("traceOffset");
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
