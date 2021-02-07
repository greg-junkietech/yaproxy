package com.github.greg.junkietech.yaproxy;

import java.net.URI;

import org.apache.http.HttpRequest;

public class ProxyRequest {

    private final URI clientURI;
    private final String rewriteURL;
    private final ServiceRoute serviceRoute;
    private final HttpRequest httpRequest;

    public ProxyRequest(URI clientURI, String rewriteURL, ServiceRoute serviceRoute, HttpRequest httpRequest) {
        super();
        this.clientURI = clientURI;
        this.rewriteURL = rewriteURL;
        this.serviceRoute = serviceRoute;
        this.httpRequest = httpRequest;
    }

    public URI getClientURI() {
        return clientURI;
    }

    public String getRewriteURL() {
        return rewriteURL;
    }

    public ServiceRoute getServiceRoute() {
        return serviceRoute;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

}
