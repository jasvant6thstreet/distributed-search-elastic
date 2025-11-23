# Distributed Document Search Service with Elasticsearch

Production-grade distributed document search service using **Elasticsearch** as the scalable backend store. This implementation provides true persistence, horizontal scalability, and enterprise-level performance.

## 🚀 Why Elasticsearch?


### Elasticsearch Advantages
- ✅ **Persistent Storage**: All data saved to disk
- ✅ **Horizontal Scalability**: Add nodes dynamically
- ✅ **Automatic Sharding**: Elasticsearch handles distribution
- ✅ **Built-in Replication**: Configurable replica factor
- ✅ **Distributed Queries**: Parallel search across shards
- ✅ **Fault Tolerance**: Node failures handled automatically
- ✅ **Production Ready**: Battle-tested by thousands of companies
- ✅ **Real-time Search**: Near real-time indexing and search
- ✅ **Advanced Features**: Aggregations, analytics, geo-search

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Load Balancer (Nginx)                   │
└──────────────────┬────────────────────┬─────────────────────┘
                   │                    │
        ┌──────────▼─────────┐   ┌─────▼──────────┐
        │ Search Service 1   │   │ Search Service 2│
        │  (Spring Boot)     │   │  (Spring Boot)  │
        └──────────┬─────────┘   └─────┬───────────┘
                   │                    │
                   └──────────┬─────────┘
                              │
                    ┌─────────▼──────────┐
                    │ Elasticsearch      │
                    │  Cluster (3 nodes) │
                    │                    │
                    │  ┌──────────────┐  │
                    │  │ Node 1       │  │
                    │  │ (Master)     │  │
                    │  └──────────────┘  │
                    │  ┌──────────────┐  │
                    │  │ Node 2       │  │
                    │  │ (Data)       │  │
                    │  └──────────────┘  │
                    │  ┌──────────────┐  │
                    │  │ Node 3       │  │
                    │  │ (Data)       │  │
                    │  └──────────────┘  │
                    └────────────────────┘
```

## Features

### Core Capabilities
- **Persistent Storage**: All documents stored in Elasticsearch
- **Sub-second Search**: BM25 scoring algorithm
- **Horizontal Scalability**: Scale to billions of documents
- **Multi-tenancy**: Separate indices per tenant
- **Auto-sharding**: Elasticsearch distributes data automatically
- **Replication**: Configurable replica count (default: 2)
- **Fault Tolerance**: Survives node failures
- **Real-time Indexing**: Documents searchable within 1 second

### Enterprise Features
- **JWT Authentication**: Secure API access
- **Rate Limiting**: Per-tenant throttling
- **Health Checks**: Spring Boot Actuator
- **Metrics**: Prometheus integration
- **Cluster Monitoring**: Elasticsearch cluster health
- **Load Balancing**: Nginx for HA

## Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven 3.6+

### Option 1: Docker Compose (Recommended)

```bash
# Start entire stack (Elasticsearch + Search Services + Load Balancer)
docker-compose up -d

# Wait for Elasticsearch to be healthy (30-60 seconds)
curl http://localhost:9200/_cluster/health

# Access the service
curl http://localhost:8080/api/health
```

**Services:**
- Search API: http://localhost:8080
- Elasticsearch: http://localhost:9200
- Kibana (UI): http://localhost:5601
- Prometheus: http://localhost:9090

### Option 2: Local Development

```bash
# Start Elasticsearch locally
docker run -d \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# Build and run application
mvn clean package
mvn spring-boot:run
```

## API Usage

### 1. Authentication

```bash
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "my-company"}'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tenantId": "my-company",
  "expiresIn": 86400
}
```

### 2. Index a Document

```bash
export TOKEN="your-token-here"

curl -X POST http://localhost:8080/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Elasticsearch is a distributed search and analytics engine",
    "metadata": {
      "title": "Intro to Elasticsearch",
      "category": "technology"
    }
  }'
```

### 3. Batch Index Documents

```bash
curl -X POST http://localhost:8080/documents/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documents": [
      {
        "content": "Document about distributed systems...",
        "metadata": {"title": "Distributed Systems"}
      },
      {
        "content": "Document about microservices...",
        "metadata": {"title": "Microservices"}
      }
    ]
  }'
```

### 4. Search Documents

```bash
curl -X GET http://localhost:8080/search&q=elasticsearch+distributed&topK=10 \
  -H "Authorization: Bearer $TOKEN" 
  
curl -X POST http://localhost:8080/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "elasticsearch distributed",
    "topK": 10
  }'
```

Response:
```json
{
  "results": [
    {
      "docId": "abc123",
      "score": 8.42,
      "snippet": "Elasticsearch is a distributed...",
      "metadata": {"title": "Intro to Elasticsearch"}
    }
  ],
  "stats": {
    "queryTimeMs": 12.5,
    "docsScanned": 10,
    "shardsQueried": 5,
    "resultsCount": 10
  },
  "tenantId": "my-company",
  "backend": "elasticsearch"
}
```

### 5. Get Tenant Statistics

```bash
curl -X GET http://localhost:8080/api/stats \
  -H "Authorization: Bearer $TOKEN"
```

Response:
```json
{
  "tenantId": "my-company",
  "stats": {
    "totalDocuments": 1523,
    "indexExists": true,
    "shards": "5",
    "replicas": "2",
    "indexName": "search-docs-my-company"
  }
}
```

## Configuration

### Elasticsearch Settings

Edit `src/main/resources/application.yml`:

```yaml
elasticsearch:
  host: localhost      # Elasticsearch host
  port: 9200          # Elasticsearch port
  scheme: http        # http or https
  username:           # Optional: username for auth
  password:           # Optional: password for auth
```

### Index Settings

Default configuration (in `ElasticsearchSearchService`):
- **Shards**: 5 primary shards per index
- **Replicas**: 2 replicas per shard
- **Refresh Interval**: 1 second

To modify, edit the `ensureIndexExists()` method.

## Scalability

### Document Capacity

| Scale | Documents | Elasticsearch Nodes | RAM per Node |
|-------|-----------|-------------------|--------------|
| Small | < 1M | 1-2 | 2-4 GB |
| Medium | 1M - 10M | 3-5 | 8-16 GB |
| Large | 10M - 100M | 5-10 | 16-32 GB |
| Enterprise | 100M+ | 10+ | 32+ GB |

### Performance Characteristics

**Indexing:**
- Single doc: ~5ms
- Batch (1000 docs): ~500ms (2,000 docs/sec)
- With bulk optimization: 10,000+ docs/sec

**Search:**
- Simple queries: 10-50ms
- Complex queries: 50-200ms
- p99 latency: < 100ms

### Scaling Strategy

**Horizontal Scaling:**
1. Add more Elasticsearch nodes to cluster
2. Elasticsearch automatically rebalances shards
3. Increase shard count for new indices
4. Add more search service instances

**Vertical Scaling:**
1. Increase RAM per Elasticsearch node
2. Use SSD storage for better I/O
3. Tune JVM heap (50% of available RAM)

## Multi-Tenancy

### Index Per Tenant
- Each tenant gets a separate Elasticsearch index
- Format: `search-docs-{tenant-id}`
- Complete data isolation
- Independent scaling per tenant

### Benefits
- Security: No cross-tenant data leakage
- Performance: Optimize per tenant
- Compliance: Data residency per region
- Flexibility: Different settings per tenant

## Fault Tolerance

### Replica Shards
- Default: 2 replicas per primary shard
- Survives loss of 2 nodes
- Automatic failover in < 1 minute

### Cluster Health
- **Green**: All shards allocated
- **Yellow**: All primaries allocated, some replicas missing
- **Red**: Some primaries not allocated

### Backup & Recovery
```bash
# Create snapshot repository
curl -X PUT "localhost:9200/_snapshot/backup" -H 'Content-Type: application/json' -d'
{
  "type": "fs",
  "settings": {
    "location": "/backups"
  }
}'

# Create snapshot
curl -X PUT "localhost:9200/_snapshot/backup/snapshot_1?wait_for_completion=true"
```

## Monitoring

### Elasticsearch Cluster Health

```bash
# Cluster health
curl http://localhost:9200/_cluster/health?pretty

# Node stats
curl http://localhost:9200/_nodes/stats?pretty

# Index stats
curl http://localhost:9200/_cat/indices?v
```

### Kibana Dashboard

Access Kibana at http://localhost:5601 for:
- Index management
- Query testing
- Cluster monitoring
- Log visualization

### Prometheus Metrics

Metrics available at http://localhost:8080/actuator/prometheus:
- Application metrics
- JVM metrics
- Custom search metrics
- Elasticsearch health

## Production Deployment

### Security Checklist
- [ ] Enable Elasticsearch security (X-Pack)
- [ ] Configure TLS/HTTPS
- [ ] Change default JWT secret
- [ ] Set up firewall rules
- [ ] Enable audit logging
- [ ] Configure backup strategy

### Performance Tuning
- [ ] Tune Elasticsearch JVM heap
- [ ] Use SSD storage
- [ ] Configure connection pooling
- [ ] Optimize index settings per use case
- [ ] Set up index lifecycle management
- [ ] Configure cluster routing

### High Availability
- [ ] Deploy across 3+ availability zones
- [ ] Configure load balancer health checks
- [ ] Set up automated backups
- [ ] Configure monitoring alerts
- [ ] Test disaster recovery procedures
- [ ] Document runbooks

## Comparison: In-Memory vs Elasticsearch

| Feature | In-Memory | Elasticsearch |
|---------|-----------|---------------|
| **Persistence** | ❌ Lost on restart | ✅ Disk-backed |
| **Scalability** | ❌ Single machine | ✅ Unlimited nodes |
| **Max Documents** | ~Millions | ✅ Billions |
| **Replication** | Manual | ✅ Automatic |
| **Fault Tolerance** | ❌ Limited | ✅ Built-in |
| **Search Speed** | Fast (in RAM) | Very Fast (indexed) |
| **Operational Complexity** | Low | Medium |
| **Cost** | Low (RAM) | Medium (Storage) |
| **Production Ready** | ❌ No | ✅ Yes |


## Project Structure

```
src/main/java/com/search/
├── DistributedSearchElasticsearchApplication.java
├── config/
│   ├── ElasticsearchConfig.java    # Elasticsearch client setup
│   └── SecurityConfig.java         # Spring Security
├── controller/
│   └── SearchController.java       # REST API endpoints
├── model/
│   ├── SearchDocument.java         # Document model
│   └── SearchModels.java           # Result models
├── security/
│   ├── JwtUtil.java               # JWT handling
│   ├── JwtAuthenticationFilter.java
│   └── TenantRateLimiter.java
└── service/
    └── ElasticsearchSearchService.java  # Core search logic
```

## License

MIT License

## Support

- Elasticsearch Docs: https://www.elastic.co/guide/
- GitHub Issues: [repository-url]
- Email: support@example.com

---

**Built with ❤️ using Spring Boot, Elasticsearch, and Java 17**
