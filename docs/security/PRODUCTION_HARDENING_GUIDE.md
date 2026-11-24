# SearchForge: Production Hardening & Deployment Guide

**Document Version:** 1.0.0  
**Target Audience:** DevOps Engineers, Site Reliability Engineers (SRE), Backend Architects  
**Scope:** SearchForge Production Deployment, Network Hardening, and Operational Runbooks  

---

## 1. Production Deployment Checklist

Before exposing SearchForge to public internet traffic, verify the following baseline requirements:

- [ ] **Generate High-Entropy Admin API Key:** Generate a cryptographic random key ($\ge 32$ characters) using `openssl rand -hex 32` and export as `SEARCHFORGE_ADMIN_API_KEY`.
- [ ] **Enable Strict Production Security Mode:** Set `SEARCHFORGE_STRICT_MODE=true` to guarantee that startup halts if a default key is detected.
- [ ] **Activate Production Spring Profile:** Run with `-Dspring.profiles.active=prod` to disable H2 console, debug banners, and test endpoints.
- [ ] **Terminate TLS at Reverse Proxy:** Deploy an edge reverse proxy (Nginx, Envoy, Cloudflare, or AWS ALB) enforcing HTTPS with TLS 1.3 / 1.2 and modern cipher suites.
- [ ] **Sanitize Inbound IP Headers:** Configure the reverse proxy to strip untrusted `X-Forwarded-For` and `X-Real-IP` headers and append the verified connecting socket IP.
- [ ] **Verify Private IP Blocking in Crawler:** Ensure `searchforge.crawler.block-private-ips=true` (enabled by default) to enforce SSRF defenses.
- [ ] **Tune Database Connection Pool:** Verify PostgreSQL connection limits match HikariCP maximum pool size (`maximum-pool-size: 20`, `leak-detection-threshold: 30000ms`).
- [ ] **Verify Actuator Masking:** Verify that `/actuator/env` and `/actuator/configprops` have `show-values: never`.

---

## 2. Environment Variables & Configuration Reference

| Environment Variable | Config Property | Default Value | Recommended Production Value | Description |
| :--- | :--- | :---: | :---: | :--- |
| `SEARCHFORGE_ADMIN_API_KEY` | `searchforge.security.admin-api-key` | `sf-admin-secret-key-change-in-prod` | *Cryptographic Random (32+ chars)* | Master secret required in `X-API-KEY` header for administrative endpoints. |
| `SEARCHFORGE_STRICT_MODE` | `searchforge.security.strict-mode` | `false` | `true` | When `true`, halts application startup if the default API key is detected. |
| `SEARCHFORGE_RL_SEARCH_CAPACITY` | `searchforge.security.rate-limit.search.capacity` | `60` | `60` | Burst capacity for public search endpoints per client IP. |
| `SEARCHFORGE_RL_SEARCH_REFILL` | `searchforge.security.rate-limit.search.refill-rate` | `1.0` | `1.0` | Refill rate in tokens/second (60 req/min continuous) for search. |
| `SEARCHFORGE_RL_ADMIN_CAPACITY` | `searchforge.security.rate-limit.admin.capacity` | `10` | `10` | Burst capacity for administrative endpoints per client IP. |
| `SEARCHFORGE_RL_ADMIN_REFILL` | `searchforge.security.rate-limit.admin.refill-rate` | `0.167` | `0.167` | Refill rate in tokens/second (10 req/min continuous) for admin. |
| `SEARCHFORGE_CRAWLER_BLOCK_PRIVATE` | `searchforge.crawler.block-private-ips` | `true` | `true` | Enforces SSRF defense; blocks loopback, RFC 1918, link-local, and cloud metadata. |
| `SEARCHFORGE_CRAWLER_MAX_REDIRECTS` | `searchforge.crawler.max-redirects` | `3` | `3` | Maximum HTTP redirect hops allowed before aborting crawl. |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `default` | `prod` | Sets active configuration profile. |

---

## 3. Reverse Proxy Configuration Reference (Nginx)

SearchForge's rate limiter relies on `X-Forwarded-For` or `X-Real-IP` to extract client IP addresses. To prevent IP spoofing attacks, the reverse proxy must strip client-provided proxy headers and set the true connecting IP address.

### Sample Production Nginx Configuration (`/etc/nginx/conf.d/searchforge.conf`)

```nginx
# Rate limiting zone for Edge DDoS defense
limit_req_zone $binary_remote_addr zone=sf_edge_limit:10m rate=100r/s;

upstream searchforge_backend {
    server 127.0.0.1:8080 max_fails=3 fail_timeout=10s;
    keepalive 32;
}

server {
    listen 80;
    server_name search.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name search.example.com;

    # SSL / TLS Hardening
    ssl_certificate /etc/letsencrypt/live/search.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/search.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    # Security Headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'" always;
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;

    # Buffer and Request Bounds
    client_max_body_size 2M;
    client_body_buffer_size 128k;

    # Edge Rate Limiting
    limit_req zone=sf_edge_limit burst=50 nodelay;

    location / {
        proxy_pass http://searchforge_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";

        # IP Spoofing Prevention: Overwrite X-Forwarded-For with true remote addr
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts
        proxy_connect_timeout 5s;
        proxy_read_timeout 15s;
        proxy_send_timeout 15s;
    }

    # Restrict Actuator / Internal endpoints at the edge
    location /actuator {
        allow 10.0.0.0/8;       # Internal corporate VPN only
        allow 172.16.0.0/12;
        allow 127.0.0.1;
        deny all;
        proxy_pass http://searchforge_backend;
    }
}
```

---

## 4. Key Rotation Procedure

When an administrative API key needs to be rotated due to scheduled rotation or suspected leakage, follow this procedure:

### Zero-Downtime Key Rotation Runbook
1. **Generate New Secret:**
   ```bash
   NEW_KEY=$(openssl rand -hex 32)
   echo "Generated new key: sf-prod-${NEW_KEY:0:8}..."
   ```
2. **Update Deployment Configuration:**
   - In containerized environments (Kubernetes / Docker Swarm):
     - Update the Kubernetes Secret or AWS Secrets Manager reference with `$NEW_KEY`.
     - Perform a rolling restart of the backend pods:
       ```bash
       kubectl rollout restart deployment/searchforge-backend
       ```
3. **Verify Health of Newly Deployed Pods:**
   ```bash
   curl -i -H "X-API-KEY: $NEW_KEY" https://search.example.com/api/v1/crawler/status
   ```
   Confirm HTTP 200 OK is returned.
4. **Update Administrative Clients / Pipelines:**
   - Update CI/CD automated crawl trigger pipelines, monitoring scripts, and developer tools with `$NEW_KEY`.
5. **Verify Old Key Invalidation:**
   ```bash
   curl -i -H "X-API-KEY: $OLD_KEY" https://search.example.com/api/v1/crawler/status
   ```
   Confirm HTTP 401 Unauthorized (`"Invalid API key"`) is returned.

---

## 5. Incident Response Playbook

### Scenario A: Suspected Administrative API Key Compromise
1. **Identify the Scope:** Check reverse proxy and application logs for unusual administrative calls (`/api/v1/lifecycle/**`, `/api/v1/index/rebuild`) and filter by `X-API-KEY`.
2. **Rotate Key Immediately:** Follow Section 4 to update `SEARCHFORGE_ADMIN_API_KEY` across running instances.
3. **Audit Index Integrity:** Execute an index verification to confirm no unauthorized deletions or corruptions occurred:
   ```bash
   curl -s -H "X-API-KEY: $NEW_KEY" https://search.example.com/api/v1/lifecycle/integrity/verify | jq .
   ```
4. **Review Discovered URLs:** Inspect `discovered_urls` for unexpected or unauthorized domains injected into the frontier.

### Scenario B: High-Volume DoS Flooding on Search Endpoints
1. **Analyze Incoming Traffic:** Inspect Nginx access logs to identify the attacking IP addresses, user-agent strings, or ASN:
   ```bash
   awk '{print $1}' /var/log/nginx/access.log | sort | uniq -c | sort -nr | head -20
   ```
2. **Confirm Application Rate Limiter Behavior:** SearchForge will automatically return HTTP 429 Too Many Requests once a client exceeds 60 req/min. Check application logs for:
   `WARN ... Rate limit exceeded for client IP ... on '/api/v1/search'`
3. **Edge IP Ban:** If a single IP or subnet is flooding, block at the firewall or reverse proxy:
   ```bash
   iptables -I INPUT -s <ATTACKER_IP> -j DROP
   # Or in Cloudflare / AWS WAF: Add IP to Blocklist
   ```

### Scenario C: Crawler SSRF or Internal Probing Detection
1. **Symptoms:** Logs contain warnings such as:
   `WARN ... Blocked SSRF attempt: RFC 1918 Private site-local address '10.0.1.5'`
   `WARN ... Blocked SSRF attempt: Link-local address '169.254.169.254'`
2. **Identify Source URL:** Check crawler logs to identify the seed URL or discovered link that targeted the private IP:
   ```bash
   grep -i "Blocked SSRF attempt" /var/log/searchforge/application.log
   ```
3. **Blacklist Domain:** If an external crawled site systematically redirects to private subnets, blacklist the domain in the crawler frontier or purge the offending seed URL.
