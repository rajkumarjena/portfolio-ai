package com.artech.portfolio.mf.service;

import com.artech.portfolio.mf.entity.MfAmc;
import com.artech.portfolio.mf.repository.MfAmcRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Resolves every AMC label to the single normalized row shared by MF holdings. */
@Service
public class MfAmcResolver {
    private final MfAmcRepository repository;
    private final ThreadLocal<Map<String, MfAmc>> transactionCache = new ThreadLocal<>();

    public MfAmcResolver(MfAmcRepository repository) {
        this.repository = repository;
    }

    /** Cache repeated labels only for one transaction, never across DB contexts. */
    public MfAmc resolve(String displayName) {
        String normalized = normalize(displayName);
        if (normalized.isEmpty()) return null;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return findOrCreate(displayName, normalized);
        Map<String, MfAmc> cache = transactionCache.get();
        if (cache == null) {
            cache = new HashMap<>();
            transactionCache.set(cache);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    transactionCache.remove();
                }
            });
        }
        return cache.computeIfAbsent(normalized, key -> findOrCreate(displayName, key));
    }

    private MfAmc findOrCreate(String displayName, String normalized) {
        return repository.findByNormalizedName(normalized).stream().findFirst().orElseGet(() -> {
            MfAmc amc = new MfAmc();
            amc.setName(displayName.trim().replaceAll("\\s+", " "));
            amc.setNormalizedName(normalized);
            return repository.save(amc);
        });
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
