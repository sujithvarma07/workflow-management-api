package com.relic.backend.jobs.impl;

import com.relic.backend.dto.BulkTaskImportItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a bulk task import on a background thread (jobTaskExecutor,
 * see AsyncConfig), splitting the item list into fixed-size chunks
 * (app.batch.chunk-size, default 50) and delegating each chunk's actual
 * work - and the job's status transitions - to BulkTaskImportChunkService,
 * whose methods are each their own REQUIRES_NEW transaction. Doing the
 * chunking here and the transactional work there is what bounds memory use:
 * each chunk's persistence context is released when that chunk's
 * transaction commits, rather than the whole job's worth of touched entities
 * accumulating in one Hibernate session for the job's entire duration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkTaskImportProcessor {

    private final BulkTaskImportChunkService chunkService;

    @Value("${app.batch.chunk-size:50}")
    private int chunkSize;

    @Async("jobTaskExecutor")
    public void process(Long jobId, Long projectId, List<BulkTaskImportItem> items) {
        chunkService.markStarted(jobId);

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (int chunkStart = 0; chunkStart < items.size(); chunkStart += chunkSize) {
            int chunkEnd = Math.min(chunkStart + chunkSize, items.size());
            List<BulkTaskImportItem> chunk = items.subList(chunkStart, chunkEnd);

            BulkTaskImportChunkService.ChunkResult result = chunkService.processChunk(projectId, chunk, chunkStart);
            successCount += result.successCount();
            errors.addAll(result.errors());
        }

        chunkService.finish(jobId, items.size(), successCount, errors);
    }
}
