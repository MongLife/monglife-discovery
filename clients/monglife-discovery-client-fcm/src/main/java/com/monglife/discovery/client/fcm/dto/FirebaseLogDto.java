package com.monglife.discovery.client.fcm.dto;

import com.monglife.module.common.logging.dto.LogDto;
import com.monglife.module.common.logging.enums.BasicLogType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FirebaseLogDto extends LogDto {

    private String message;

    private List<String> tokens;

    @Builder
    public FirebaseLogDto(String traceId, Integer traceOffset, String entryMethod, String className, String method, String message, List<String> tokens) {
        super(traceId, traceOffset, entryMethod, className, method, BasicLogType.EXCEPTION);
        this.message = message;
        this.tokens = tokens;
    }

    @Override
    public String getDetailMessage() {
        return String.format("%s | %s", message, tokens);
    }
}
