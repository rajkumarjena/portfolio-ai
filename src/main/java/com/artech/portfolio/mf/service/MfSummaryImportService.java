package com.artech.portfolio.mf.service;

import java.util.Map;

public interface MfSummaryImportService {
    Map<String, Object> upload(String ownerPan, String fileName, byte[] data, String password);
    java.util.List<com.artech.portfolio.mf.entity.MfImportBatch> list(String ownerPan);
    Map<String, Object> report(Long batchId, String ownerPan);
    void discard(Long batchId, String ownerPan);
    Map<String, Object> accept(Long batchId, String ownerPan, Map<Long, Map<String, String>> corrections);
}
