package com.chatbot.saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DailyOrderCountResponse {
    private LocalDate day;
    private long count;
}
