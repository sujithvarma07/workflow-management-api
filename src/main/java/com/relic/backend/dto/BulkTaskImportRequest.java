package com.relic.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// A single JSON request body holding every item is fine for hundreds of
// rows; it is not a streaming upload, so a genuinely huge import (hundreds
// of thousands of rows) would need a different ingestion path (e.g. a
// streamed CSV file) rather than this endpoint - see docs/reliability-report.md.
public record BulkTaskImportRequest(

        @NotNull(message = "projectId is required")
        Long projectId,

        @NotEmpty(message = "items must not be empty")
        @Size(max = 2000, message = "at most 2000 items per request")
        @Valid
        List<BulkTaskImportItem> items
) {
}
