package com.github.greg.junkietech.yaproxy;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceRoute {

    private final static Logger LOGGER = LoggerFactory.getLogger(ServiceRoute.class);

    private final String serviceName, serviceDomain;
    private final CloseableHttpClient httpClient;
    private final HttpHost host;

    public ServiceRoute(String serviceName, String serviceDomain, CloseableHttpClient httpClient, HttpHost host) {
        super();
        this.serviceName = serviceName;
        this.serviceDomain = serviceDomain;
        this.httpClient = httpClient;
        this.host = host;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceDomain() {
        return serviceDomain;
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    public HttpHost getHost() {
        return host;
    }

    public void close() {
        try {
            LOGGER.info("closing http client for " + toString());
            httpClient.close();
        } catch (IOException e) {
            LOGGER.warn("IOException occurred while closing http client for " + toString(), e);
        }
    }

    @Override
    public String toString() {
        return String.format("service %s for domain %s routed to host %s", getServiceName(), getServiceDomain(),
                getHost().toHostString());
    }

}
