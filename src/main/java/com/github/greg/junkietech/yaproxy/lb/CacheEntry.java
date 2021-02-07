package com.github.greg.junkietech.yaproxy.lb;

import java.time.LocalDateTime;

import org.apache.http.Header;

import com.github.greg.junkietech.yaproxy.ServiceRoute;

public class CacheEntry {

    private final ServiceRoute origine;
    private final String path;
    private final byte[] responseBody;
    private final Header[] responseHeaders;
    private final LocalDateTime expiration;

    public CacheEntry(ServiceRoute origine, String path, byte[] responseBody, Header[] responseHeaders,
            LocalDateTime expiration) {
        super();
        this.origine = origine;
        this.path = path;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
        this.expiration = expiration;
    }

    public ServiceRoute getOrigine() {
        return origine;
    }

    public String getPath() {
        return path;
    }

    public byte[] getResponseBody() {
        return responseBody;
    }

    public Header[] getResponseHeaders() {
        return responseHeaders;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    @Override
    public String toString() {
        return String.format("cache entry from path %s (%d bytes) originated from %s expiring %s", getPath(),
                getResponseBody().length, getOrigine(), getExpiration());
    }

}
