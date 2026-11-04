/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
 * Scope: the HTTP adapter of SupplierLocations: pages through
 * supplier-service's public GET /suppliers with the caller's own bearer
 * token (author's decision: the existing public API, no new supplier
 * endpoint) until every asked-for id is seen, short timeouts.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class HttpSupplierLocations implements SupplierLocations {

    // suppliers asked for per page; the seed has ~20, so one page in practice
    static final int PAGE_SIZE = 100;

    private final RestClient rest;

    HttpSupplierLocations(@Value("${order.supplier.base-url}") String baseUrl) {
        // an accept waits on this, so a stalled supplier-service must fail fast
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(3));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    @Override
    public Map<Long, Coordinates> of(Set<Long> supplierIds) {
        String authorization = callersAuthorization();
        Set<Long> wanted = new HashSet<>(supplierIds);
        Map<Long, Coordinates> found = new HashMap<>();
        int page = 0;
        int totalPages = 1;
        while (!wanted.isEmpty() && page < totalPages) {
            SupplierPage body = fetch(page, authorization);
            for (SupplierItem s : body.content()) {
                Long id = parseId(s.id());
                if (id != null && wanted.remove(id) && s.latitude() != null && s.longitude() != null) {
                    found.put(id, new Coordinates(s.latitude(), s.longitude()));
                }
            }
            totalPages = body.totalPages();
            page++;
        }
        return found;
    }

    private SupplierPage fetch(int page, String authorization) {
        try {
            SupplierPage body = rest.get()
                    .uri(b -> b.path("/suppliers").queryParam("page", page).queryParam("size", PAGE_SIZE).build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(SupplierPage.class);
            if (body == null || body.content() == null) {
                throw new SupplierLookupException("supplier-service answered an empty page");
            }
            return body;
        } catch (RestClientException e) {
            throw new SupplierLookupException("supplier-service could not be asked: " + e.getMessage(), e);
        }
    }

    // GET /suppliers wants a user token; the accept's own is passed on
    private static String callersAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && !header.isBlank()) return header;
        }
        throw new SupplierLookupException("no caller token to ask supplier-service with");
    }

    private static Long parseId(String id) {
        try {
            return id == null ? null : Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // only the fields the check reads; supplier-service sends ids as strings
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupplierPage(List<SupplierItem> content, int totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupplierItem(String id, Double latitude, Double longitude) {
    }
}
