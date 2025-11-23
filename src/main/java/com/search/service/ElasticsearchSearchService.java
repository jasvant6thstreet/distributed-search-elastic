package com.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import com.search.model.SearchDocument;
import com.search.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Elasticsearch-based search service with persistence and scalability
 * 
 * Features:
 * - Distributed storage with Elasticsearch
 * - Automatic sharding and replication
 * - Multi-tenant with separate indices
 * - Full-text search with BM25 scoring
 * - Horizontal scalability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSearchService {
    
    private final ElasticsearchClient elasticsearchClient;
    
    // Metrics
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalDocuments = new AtomicLong(0);
    private final AtomicLong totalQueryTimeMs = new AtomicLong(0);
    
    /**
     * Default index settings: 5 shards, 2 replicas for fault tolerance
     */
    private static final int DEFAULT_SHARDS = 5;
    private static final int DEFAULT_REPLICAS = 2;
    
    /**
     * Ensure index exists for a tenant, create if not
     */
    private void ensureIndexExists(String tenantId) throws IOException {
        String indexName = SearchDocument.getIndexName(tenantId);
        
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
        boolean exists = elasticsearchClient.indices().exists(existsRequest).value();
        
        if (!exists) {
            log.info("Creating index for tenant: {}", tenantId);
            
            CreateIndexRequest createRequest = CreateIndexRequest.of(c -> c
                .index(indexName)
                .settings(s -> s
                    .numberOfShards(String.valueOf(DEFAULT_SHARDS))
                    .numberOfReplicas(String.valueOf(DEFAULT_REPLICAS))
                    .refreshInterval(t -> t.time("1s")) // Refresh every second
                )
                .mappings(m -> m
                    .properties("content", p -> p.text(t -> t.analyzer("standard")))
                    .properties("doc_id", p -> p.keyword(k -> k))
                    .properties("tenant_id", p -> p.keyword(k -> k))
                    .properties("timestamp", p -> p.date(d -> d))
                    .properties("metadata", p -> p.object(o -> o.enabled(true)))
                )
            );
            
            elasticsearchClient.indices().create(createRequest);
            log.info("Index created successfully: {}", indexName);
        }
    }
    
    /**
     * Index a single document
     */
    public String indexDocument(SearchDocument document) {
        try {
            ensureIndexExists(document.getTenantId());
            
            String indexName = SearchDocument.getIndexName(document.getTenantId());
            
            IndexRequest<SearchDocument> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(document.getDocId())
                .document(document)
                .refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor) // Wait for refresh
            );
            
            IndexResponse response = elasticsearchClient.index(request);
            totalDocuments.incrementAndGet();
            
            log.debug("Indexed document {} in index {}", document.getDocId(), indexName);
            return response.id();
            
        } catch (IOException | ElasticsearchException e) {
            log.error("Error indexing document {}: {}", document.getDocId(), e.getMessage(), e);
            throw new RuntimeException("Failed to index document", e);
        }
    }
    
    /**
     * Index multiple documents in bulk
     */
    public BulkIndexResult indexDocumentsBatch(List<SearchDocument> documents) {
        if (documents.isEmpty()) {
            return new BulkIndexResult(0, 0, 0);
        }
        
        long startTime = System.nanoTime();
        int successCount = 0;
        int failureCount = 0;
        
        try {
            // Group documents by tenant
            Map<String, List<SearchDocument>> docsByTenant = documents.stream()
                .collect(Collectors.groupingBy(SearchDocument::getTenantId));
            
            // Ensure indices exist for all tenants
            for (String tenantId : docsByTenant.keySet()) {
                ensureIndexExists(tenantId);
            }
            
            // Build bulk request
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            
            for (SearchDocument doc : documents) {
                String indexName = SearchDocument.getIndexName(doc.getTenantId());
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .id(doc.getDocId())
                        .document(doc)
                    )
                );
            }
            
            BulkRequest bulkRequest = bulkBuilder
                .refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor)
                .build();
            
            BulkResponse response = elasticsearchClient.bulk(bulkRequest);
            
            // Count successes and failures
            successCount = (int) response.items().stream()
                .filter(item -> item.error()==null)
                .count();
            failureCount = response.items().size() - successCount;
            
            totalDocuments.addAndGet(successCount);
            
            double timeMs = (System.nanoTime() - startTime) / 1_000_000.0;
            
            log.info("Bulk indexed {} documents ({} success, {} failures) in {}ms",
                    documents.size(), successCount, failureCount, timeMs);
            
            return new BulkIndexResult(successCount, failureCount, timeMs);
            
        } catch (IOException | ElasticsearchException e) {
            log.error("Error in bulk indexing: {}", e.getMessage(), e);
            return new BulkIndexResult(successCount, documents.size() - successCount, 
                                      (System.nanoTime() - startTime) / 1_000_000.0);
        }
    }
    
    /**
     * Search documents with full-text search
     */
    public SearchResponse search(String tenantId, String queryText, int topK) {
        long startTime = System.nanoTime();
        
        try {
            String indexName = SearchDocument.getIndexName(tenantId);
            
            // Check if index exists
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
            if (!elasticsearchClient.indices().exists(existsRequest).value()) {
                log.warn("Index does not exist for tenant: {}", tenantId);
                return new SearchResponse(new ArrayList<>(), 
                    new QueryStats(0, 0, 0, 0));
            }
            
            // Build search query - multi-match across content field
            Query query = Query.of(q -> q
                .match(m -> m
                    .field("content")
                    .query(queryText)
                )
            );
            
            // Execute search
            co.elastic.clients.elasticsearch.core.SearchRequest searchRequest = 
                co.elastic.clients.elasticsearch.core.SearchRequest.of(s -> s
                    .index(indexName)
                    .query(query)
                    .size(topK)
                );
            
            co.elastic.clients.elasticsearch.core.SearchResponse<SearchDocument> response = 
                elasticsearchClient.search(searchRequest, SearchDocument.class);
            
            // Convert hits to search results
            List<SearchResult> results = response.hits().hits().stream()
                .map(hit -> {
                    SearchDocument doc = hit.source();
                    double score = hit.score() != null ? hit.score() : 0.0;
                    return SearchResult.fromDocument(doc, score);
                })
                .collect(Collectors.toList());
            
            double queryTimeMs = (System.nanoTime() - startTime) / 1_000_000.0;
            totalQueries.incrementAndGet();
            totalQueryTimeMs.addAndGet((long) queryTimeMs);
            
            // Get shard info
            int shardsQueried = response.shards().successful().intValue();
            
            QueryStats stats = new QueryStats(
                queryTimeMs,
                results.size(),
                shardsQueried,
                results.size()
            );
            
            log.debug("Search completed for tenant {} in {}ms, found {} results",
                     tenantId, queryTimeMs, results.size());
            
            return new SearchResponse(results, stats);
            
        } catch (IOException | ElasticsearchException e) {
            log.error("Error searching for tenant {}: {}", tenantId, e.getMessage(), e);
            double queryTimeMs = (System.nanoTime() - startTime) / 1_000_000.0;
            return new SearchResponse(new ArrayList<>(), 
                new QueryStats(queryTimeMs, 0, 0, 0));
        }
    }
    
    /**
     * Delete a document
     */
    public boolean deleteDocument(String tenantId, String docId) {
        try {
            String indexName = SearchDocument.getIndexName(tenantId);
            
            DeleteRequest request = DeleteRequest.of(d -> d
                .index(indexName)
                .id(docId)
                .refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor)
            );
            
            DeleteResponse response = elasticsearchClient.delete(request);
            
            log.debug("Deleted document {} from index {}", docId, indexName);
            return true;
            
        } catch (IOException | ElasticsearchException e) {
            log.error("Error deleting document {}: {}", docId, e.getMessage(), e);
            return false;
        }
    }
    /**
     * Retrieve a document
     */
    public SearchDocument retrieveDocument(String tenantId, String docId) {
        try {
            String indexName = SearchDocument.getIndexName(tenantId);

            GetRequest request = GetRequest.of(d -> d
                    .index(indexName)
                    .id(docId)
                    .refresh(false)
            );

            GetResponse<SearchDocument> response = elasticsearchClient.get(request, SearchDocument.class);

            log.debug("Retrieved document {} from index {}", docId, indexName);
            if(response.found()) {
                log.debug("Retrieved document {} from index {}", docId, indexName);
                return response.source();
            } else {
                log.debug("No document found for {} in index {}", docId, indexName);
                return null;
            }

        } catch (IOException | ElasticsearchException e) {
            log.error("Error retrieving document {}: {}", docId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get tenant statistics
     */
    public Map<String, Object> getTenantStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            String indexName = SearchDocument.getIndexName(tenantId);
            
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
            if (!elasticsearchClient.indices().exists(existsRequest).value()) {
                stats.put("totalDocuments", 0);
                stats.put("indexExists", false);
                return stats;
            }
            
            // Get document count
            CountRequest countRequest = CountRequest.of(c -> c.index(indexName));
            CountResponse countResponse = elasticsearchClient.count(countRequest);
            
            // Get index info
            GetIndexRequest getIndexRequest = GetIndexRequest.of(g -> g.index(indexName));
            var indexResponse = elasticsearchClient.indices().get(getIndexRequest);
            
            var indexSettings = indexResponse.get(indexName).settings();
            String shards = indexSettings.index().numberOfShards();
            String replicas = indexSettings.index().numberOfReplicas();
            
            stats.put("totalDocuments", countResponse.count());
            stats.put("indexExists", true);
            stats.put("shards", shards);
            stats.put("replicas", replicas);
            stats.put("indexName", indexName);
            
        } catch (IOException | ElasticsearchException e) {
            log.error("Error getting tenant stats: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Get service metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalQueries", totalQueries.get());
        metrics.put("totalDocuments", totalDocuments.get());
        metrics.put("avgQueryTimeMs", totalQueries.get() > 0 
            ? (double) totalQueryTimeMs.get() / totalQueries.get() 
            : 0.0);
        
        try {
            // Get cluster health
            var healthResponse = elasticsearchClient.cluster().health();
            metrics.put("clusterHealth", healthResponse.status().toString());
            metrics.put("numberOfNodes", healthResponse.numberOfNodes());
            metrics.put("numberOfDataNodes", healthResponse.numberOfDataNodes());
        } catch (IOException e) {
            log.error("Error getting cluster health: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    // Helper classes
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResponse {
        private List<SearchResult> results;
        private QueryStats stats;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BulkIndexResult {
        private int successCount;
        private int failureCount;
        private double timeMs;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QueryStats {
        private double queryTimeMs;
        private int docsScanned;
        private int shardsQueried;
        private int resultsCount;
    }
}
