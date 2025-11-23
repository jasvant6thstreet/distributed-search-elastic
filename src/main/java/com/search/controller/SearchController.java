package com.search.controller;

import com.search.model.SearchDocument;
import com.search.model.SearchResult;
import com.search.service.ElasticsearchSearchService;
import com.search.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for Elasticsearch-based distributed search service
 */
@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class SearchController {
    
    private final ElasticsearchSearchService searchService;
    private final JwtUtil jwtUtil;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("backend", "elasticsearch");
        response.put("metrics", searchService.getMetrics());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate JWT token for authentication
     */
    @PostMapping("/api/auth/token")
    public ResponseEntity<Map<String, Object>> generateToken(@RequestBody Map<String, String> request) {
        String tenantId = request.get("tenantId");
        
        if (tenantId == null || tenantId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId is required"));
        }
        
        String token = jwtUtil.generateToken(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("tenantId", tenantId);
        response.put("expiresIn", 86400);

        return ResponseEntity.ok(response);
    }
    
    /**
     * Index a single document
     */
    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @RequestBody Map<String, Object> request,
            @RequestAttribute("tenantId") String tenantId) {
        
        String content = (String) request.get("content");
        if (content == null || content.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "content is required"));
        }
        
        String docId = request.containsKey("docId") 
            ? (String) request.get("docId") 
            : UUID.randomUUID().toString();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", new HashMap<>());
        
        SearchDocument document = SearchDocument.builder()
                .docId(docId)
                .tenantId(tenantId)
                .content(content)
                .metadata(metadata)
                .build();
        
        try {
            String id = searchService.indexDocument(document);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("docId", id);
            response.put("tenantId", tenantId);
            response.put("indexName", SearchDocument.getIndexName(tenantId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error indexing document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to index document: " + e.getMessage()));
        }
    }
    
    /**
     * Index multiple documents in batch
     */
    @PostMapping("/documents/batch")
    public ResponseEntity<Map<String, Object>> indexDocumentsBatch(
            @RequestBody Map<String, Object> request,
            @RequestAttribute("tenantId") String tenantId) {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documentsData = (List<Map<String, Object>>) request.get("documents");
        
        if (documentsData == null || documentsData.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "documents array is required"));
        }
        
        List<SearchDocument> documents = documentsData.stream()
                .filter(doc -> doc.containsKey("content") && doc.get("content") != null)
                .map(doc -> {
                    String docId = doc.containsKey("docId") 
                        ? (String) doc.get("docId") 
                        : UUID.randomUUID().toString();
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) 
                        doc.getOrDefault("metadata", new HashMap<>());
                    
                    return SearchDocument.builder()
                            .docId(docId)
                            .tenantId(tenantId)
                            .content((String) doc.get("content"))
                            .metadata(metadata)
                            .build();
                })
                .collect(Collectors.toList());
        
        ElasticsearchSearchService.BulkIndexResult result = 
            searchService.indexDocumentsBatch(documents);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("indexed", result.getSuccessCount());
        response.put("failed", result.getFailureCount());
        response.put("total", documentsData.size());
        response.put("indexingTimeMs", result.getTimeMs());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Search for documents
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestBody Map<String, Object> request,
            @RequestAttribute("tenantId") String tenantId) {
        
        String query = (String) request.get("query");
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "query is required"));
        }
        
        int topK = request.containsKey("topK") 
            ? ((Number) request.get("topK")).intValue() 
            : 10;
        
        if (topK < 1 || topK > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "topK must be between 1 and 100"));
        }
        
        ElasticsearchSearchService.SearchResponse searchResponse = 
                searchService.search(tenantId, query, topK);
        
        List<Map<String, Object>> results = searchResponse.getResults().stream()
                .map(result -> {
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("docId", result.getDocId());
                    resultMap.put("score", result.getScore());
                    resultMap.put("snippet", result.getSnippet());
                    resultMap.put("metadata", result.getMetadata());
                    return resultMap;
                })
                .collect(Collectors.toList());
        
        ElasticsearchSearchService.QueryStats stats = searchResponse.getStats();
        Map<String, Object> statsMap = new HashMap<>();
        statsMap.put("queryTimeMs", stats.getQueryTimeMs());
        statsMap.put("docsScanned", stats.getDocsScanned());
        statsMap.put("shardsQueried", stats.getShardsQueried());
        statsMap.put("resultsCount", stats.getResultsCount());
        
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("stats", statsMap);
        response.put("tenantId", tenantId);
        response.put("backend", "elasticsearch");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Search for documents
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "q") String query,
            @RequestParam(name="topK", required = false) Number topKNum,
            @RequestAttribute("tenantId") String tenantId) {

        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "query is required"));
        }

        int topK = topKNum!=null
                ? topKNum.intValue()
                : 10;

        if (topK < 1 || topK > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "topK must be between 1 and 100"));
        }

        ElasticsearchSearchService.SearchResponse searchResponse =
                searchService.search(tenantId, query, topK);

        List<Map<String, Object>> results = searchResponse.getResults().stream()
                .map(result -> {
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("docId", result.getDocId());
                    resultMap.put("score", result.getScore());
                    resultMap.put("snippet", result.getSnippet());
                    resultMap.put("metadata", result.getMetadata());
                    return resultMap;
                })
                .collect(Collectors.toList());

        ElasticsearchSearchService.QueryStats stats = searchResponse.getStats();
        Map<String, Object> statsMap = new HashMap<>();
        statsMap.put("queryTimeMs", stats.getQueryTimeMs());
        statsMap.put("docsScanned", stats.getDocsScanned());
        statsMap.put("shardsQueried", stats.getShardsQueried());
        statsMap.put("resultsCount", stats.getResultsCount());

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("stats", statsMap);
        response.put("tenantId", tenantId);
        response.put("backend", "elasticsearch");

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a document
     */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @PathVariable String docId,
            @RequestAttribute("tenantId") String tenantId) {

        SearchDocument doc = searchService.retrieveDocument(tenantId, docId);

        if (doc!=null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", doc);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No document found"));
        }
    }

    /**
     * Delete a document
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable String docId,
            @RequestAttribute("tenantId") String tenantId) {
        
        boolean success = searchService.deleteDocument(tenantId, docId);
        
        if (success) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("docId", docId);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete document"));
        }
    }
    
    /**
     * Get tenant statistics
     */
    @GetMapping("/api/stats")
    public ResponseEntity<Map<String, Object>> getTenantStats(
            @RequestAttribute("tenantId") String tenantId) {
        
        Map<String, Object> stats = searchService.getTenantStats(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("tenantId", tenantId);
        response.put("stats", stats);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get system-wide metrics
     */
    @GetMapping("/api/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> response = new HashMap<>();
        response.put("backend", "elasticsearch");
        response.put("metrics", searchService.getMetrics());
        
        return ResponseEntity.ok(response);
    }
}
