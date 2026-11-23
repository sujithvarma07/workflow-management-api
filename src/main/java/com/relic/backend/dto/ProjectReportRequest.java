package com.relic.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ProjectReportRequest(

        @NotNull(message = "projectId is required")
        Long projectId
) {
}
