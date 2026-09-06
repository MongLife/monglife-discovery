package com.monglife.discovery.app.gateway.dto.etc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.monglife.module.common.logging.dto.LogDto;
import com.monglife.module.common.logging.enums.BasicLogType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 요청자 정보를 함께 남기는 예외 로그.
 *
 * 공용 ExceptionLogDto 에는 clientIp / userAgent 자리가 없다. 게이트웨이에서만 필요한
 * 값이라 공용 모듈을 건드리는 대신 여기에 둔다. logType 은 EXCEPTION 으로 같아서
 * Kibana 에서 다른 서비스의 예외 로그와 같은 조건으로 묶인다.
 */
@Getter
@Setter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RequestExceptionLogDto extends LogDto {

    private String message;

    private String stackTrace;

    private String clientIp;

    private String userAgent;

    @Builder
    public RequestExceptionLogDto(String traceId, Integer traceOffset, String entryMethod, String className, String method, String message, String stackTrace, String clientIp, String userAgent) {
        super(traceId, traceOffset, entryMethod, className, method, BasicLogType.EXCEPTION);
        this.message = message;
        this.stackTrace = stackTrace;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }
}
