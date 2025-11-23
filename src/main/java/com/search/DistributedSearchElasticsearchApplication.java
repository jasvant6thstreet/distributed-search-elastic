package com.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Elasticsearch-based Distributed Search Service
 */
@SpringBootApplication
public class DistributedSearchElasticsearchApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DistributedSearchElasticsearchApplication.class, args);
    }
}
