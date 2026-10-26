/*
AI Assistance Disclosure:
Tool: Claude Code (Sonnet 5.5), date: 2026-10-26
Scope: issue #225 (N8.5, D12), the account-purge job's call to
       supplier-service's POST /purge/bookmarks, so a purged account's
       bookmarks go with it. Direct API call and service-token protection
       chosen by the author (options C+B). Calls go straight to the
       supplier-service container, not through the gateway, with a
       one-minute SERVICE token from JwtIssuer. Ids are sent in batches of
       500 (the endpoint takes at most 1000). Any failure — connection,
       timeout, a non-2xx answer — is reported as one exception; the
       caller decides what to do (the purge skips the delete and tries
       again on its next run). The timeouts were chosen by the tool.
Author review: Alastair Tan Choon Wei to review via the PR.
*/

package foc.user.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import foc.user.exception.BookmarkCleanupException;
import foc.user.security.JwtIssuer;

@Component
public class SupplierBookmarkCleanupClient {

    static final int BATCH_SIZE = 500;

    private final RestClient restClient;
    private final JwtIssuer jwtIssuer;

    public SupplierBookmarkCleanupClient(
            @Value("${user.supplier.base-url}") String baseUrl, JwtIssuer jwtIssuer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.jwtIssuer = jwtIssuer;
    }

    // Removes every bookmark of these (purged) accounts. Idempotent on the
    // supplier side. Throws BookmarkCleanupException if any batch failed.
    public void purgeBookmarks(Collection<Long> userIds) {
        List<Long> ids = new ArrayList<>(userIds);
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            try {
                restClient.post()
                    .uri("/purge/bookmarks")
                    .header("Authorization", "Bearer " + jwtIssuer.issueServiceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PurgeRequest(batch))
                    .retrieve()
                    .toBodilessEntity();
            } catch (RestClientException e) {
                throw new BookmarkCleanupException(
                    "supplier-service did not remove the bookmarks of " + batch.size() + " account(s)", e);
            }
        }
    }

    record PurgeRequest(List<Long> userIds) {
    }
}
