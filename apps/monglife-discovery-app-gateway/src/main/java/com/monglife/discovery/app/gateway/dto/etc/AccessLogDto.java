package com.monglife.discovery.app.gateway.dto.etc;

import com.monglife.module.common.logging.dto.LogDto;
import com.monglife.module.common.logging.enums.BasicLogType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessLogDto extends LogDto {

    private String httpMethod;

    private String mapping;

    private String clientIp;

    private String userAgent;

    @Builder
    public AccessLogDto(String traceId, Integer traceOffset, String entryMethod, String className, String method, String httpMethod, String mapping, String clientIp, String userAgent) {
        super(traceId, traceOffset, entryMethod, className, method, BasicLogType.METHOD_CALL);
        this.httpMethod = httpMethod;
        this.mapping = mapping;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }
}
