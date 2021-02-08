package com.github.greg.junkietech.yaproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.greg.junkietech.yaproxy.lb.ALoadBalancer;
import com.github.greg.junkietech.yaproxy.lb.CacheEntry;
import com.github.greg.junkietech.yaproxy.util.HttpIOUtil;
import com.github.greg.junkietech.yaproxy.util.RewriteURLUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RequestHandler implements HttpHandler {

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private final static Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    private final Map<String, ALoadBalancer<ServiceRoute>> serviceMap;

    public RequestHandler(Map<String, ALoadBalancer<ServiceRoute>> serviceMap) {
        super();
        this.serviceMap = serviceMap;
    }

    @Override
    public void handle(HttpExchange exchange) {
        final String reqHost = exchange.getRequestHeaders().getFirst("Host");
        LOGGER.info("received {} {} {} from {} with header Host '{}'", exchange.getProtocol(),
                exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRemoteAddress().toString(), reqHost);
        try {
            ALoadBalancer<ServiceRoute> lb = serviceMap.get(reqHost);
            /* String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("application/json")) {
                HttpIOUtil.writeResponse(exchange, "{'error':'Content-Type application/json is required'}",
                        HttpStatus.SC_BAD_REQUEST);
            } else */ if (lb == null) {
                HttpIOUtil.writeResponse(exchange, "{'error':'no service domain defined for host " + reqHost + "'}",
                        HttpStatus.SC_BAD_REQUEST);
            } else {
                handleClientRequest(exchange, lb);
            }
        } catch (IOException e) {
            LOGGER.warn("IOException occurred while serving {} {} {} from {} with header Host '{}': {}",
                    exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRemoteAddress().toString(), reqHost, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.warn("RuntimeException occurred while serving {} {} {} from {} with header Host '{}': {}",
                    exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRemoteAddress().toString(), reqHost, e.getMessage());
            LOGGER.warn("RuntimeException details", e);
        }
    }

    private static void handleClientRequest(HttpExchange exchange, ALoadBalancer<ServiceRoute> lb) throws IOException {
        if (handleClientRequestFromCache(exchange, lb)) {
        } else {
            handleClientRequestFromDownstream(exchange, lb);
        }
    }

    private static boolean handleClientRequestFromCache(HttpExchange exchange, ALoadBalancer<ServiceRoute> lb)
            throws IOException {
        CacheEntry ce = lb.getCache().getEntry(exchange.getRequestURI().getPath());
        boolean result = ce != null && LocalDateTime.now().isBefore(ce.getExpiration());
        if (result) {
            // TODO filter or adjust forwarded headers
            copyResponseHeaders(ce.getResponseHeaders(), exchange.getResponseHeaders());
            exchange.sendResponseHeaders(HttpStatus.SC_OK, ce.getResponseBody().length);
            OutputStream os = exchange.getResponseBody();
            os.write(ce.getResponseBody());

            String cachingLoginfo = String.format("from cache");
            os.close();
            LOGGER.info("served {} bytes ({}) {} {} {} from {}", ce.getResponseBody().length, cachingLoginfo,
                    exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRemoteAddress().toString());
        }
        return result;
    }

    private static void handleClientRequestFromDownstream(HttpExchange exchange, ALoadBalancer<ServiceRoute> lb)
            throws IOException {
        ServiceRoute svcRoute = lb.nextTarget();
        ProxyRequest proxyRequest = createProxyRequest(exchange, svcRoute);
        try (CloseableHttpResponse incomingResponse = svcRoute.getHttpClient().execute(svcRoute.getHost(),
                proxyRequest.getHttpRequest())) {
            handleServiceResponse(incomingResponse, exchange, lb, proxyRequest);
        } catch (IOException e) {
            LOGGER.warn(
                    "IOException occurred while serving {} {} {} from {} matching service '{}' defined by domain '{}' proxied to host '{}': {}",
                    exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRemoteAddress().toString(), svcRoute.getServiceName(), svcRoute.getServiceDomain(),
                    svcRoute.getHost().toHostString(), e.getMessage());
            HttpIOUtil.writeResponse(exchange,
                    "{'error':'IOException occurred while serving request proxied to host "
                            + svcRoute.getHost().toHostString() + ": " + e.getMessage() + "}",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static ProxyRequest createProxyRequest(HttpExchange exchange, ServiceRoute svcRoute) {
        HttpRequest httpRequest;

        // HTTP/1.1 RFC 2616 section 4.3: https://tools.ietf.org/html/rfc2616
        // The presence of a message-body in a request is signaled by the
        // inclusion of a Content-Length or Transfer-Encoding header field in
        // the request's message-headers.

        String rewriteURL = RewriteURLUtil.rewriteURL(exchange.getRequestURI(), svcRoute.getHost());
        if (exchange.getRequestHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)
                || exchange.getRequestHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING)) {
            HttpEntityEnclosingRequest eResult = new BasicHttpEntityEnclosingRequest(exchange.getRequestMethod(),
                    rewriteURL);
            eResult.setEntity(new InputStreamEntity(exchange.getRequestBody(),
                    getContentLength(exchange.getRequestHeaders().getFirst("Content-Length"))));
            httpRequest = eResult;
        } else {
            httpRequest = new BasicHttpRequest(exchange.getRequestMethod(), rewriteURL);
        }

        copyRequestHeaders(exchange.getRequestHeaders(), httpRequest, svcRoute.getHost());
        return new ProxyRequest(exchange.getRequestURI(), rewriteURL, svcRoute, httpRequest);
    }

    private static void handleServiceResponse(HttpResponse svcResponse, HttpExchange exchange,
            ALoadBalancer<ServiceRoute> lb, ProxyRequest proxyRequest) throws IOException {
        int statusCode = svcResponse.getStatusLine().getStatusCode();

        copyResponseHeaders(svcResponse.getAllHeaders(), exchange.getResponseHeaders());

        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
            // HTTP/1.1 RFC 2616 section 4.3: https://tools.ietf.org/html/rfc2616
            // All 1xx (informational), 204 (no content), and 304 (not modified) responses
            // MUST NOT include a message-body. All other responses do include a
            // message-body, although it MAY be of zero length.
            exchange.sendResponseHeaders(statusCode, 0);
        } else {
            long contentLength = RequestHandler.getContentLength(svcResponse.getFirstHeader("Content-Length"));
            exchange.sendResponseHeaders(statusCode, contentLength);
            HttpEntity entity = svcResponse.getEntity();
            if (entity.isChunked()) {
                throw new UnsupportedOperationException("Chunked / compressed content not supported");
            } else {
                handleServiceResponseBody(svcResponse, entity, exchange, lb, proxyRequest, contentLength);
            }
        }
    }

    private static void handleServiceResponseBody(HttpResponse svcResponse, HttpEntity entity, HttpExchange exchange,
            ALoadBalancer<ServiceRoute> lb, ProxyRequest proxyRequest, long contentLength) throws IOException {
        OutputStream os = exchange.getResponseBody();
        int statusCode = svcResponse.getStatusLine().getStatusCode();

        // see HTTP/1.1 RFC 2616 section 14.9: https://tools.ietf.org/html/rfc2616#section-14.9
        Header ccHeader = svcResponse.getFirstHeader(HEADER_CACHE_CONTROL);
        Matcher ccHeaderPatternMatcher;
        final String cachingLoginfo;
        if (statusCode != HttpStatus.SC_OK) {
            cachingLoginfo = String.format("not cached as status code %s != %s", statusCode, HttpStatus.SC_OK);
            entity.writeTo(os);
        } else if (ccHeader == null) {
            cachingLoginfo = String.format("not cached as no %s", HEADER_CACHE_CONTROL);
            entity.writeTo(os);
        } else if ((ccHeaderPatternMatcher = Pattern.compile(" *(public, *)?(max-age|s-maxage)=(\\d+)")
                .matcher(ccHeader.getValue())).matches()) {
            byte[] content = handleCachingResponseBody(entity, lb, proxyRequest, contentLength, ccHeaderPatternMatcher,
                    svcResponse.getAllHeaders());
            cachingLoginfo = String.format("cached as supported %s", ccHeader);
            os.write(content);
        } else {
            cachingLoginfo = String.format("not cached as unsupported %s", ccHeader);
            entity.writeTo(os);
        }
        os.close();
        LOGGER.info(
                "served {} bytes ({}) {} {} {} from {} matching service '{}' defined by domain '{}' proxied to host '{}'",
                contentLength, cachingLoginfo, exchange.getProtocol(), exchange.getRequestMethod(),
                exchange.getRequestURI().toString(), exchange.getRemoteAddress().toString(),
                proxyRequest.getServiceRoute().getServiceName(), proxyRequest.getServiceRoute().getServiceDomain(),
                proxyRequest.getServiceRoute().getHost().toHostString());
    }

    private static byte[] handleCachingResponseBody(HttpEntity entity, ALoadBalancer<ServiceRoute> lb,
            ProxyRequest proxyRequest, long contentLength, Matcher ccHeaderPatternMatcher, Header[] responseHeaders)
            throws IOException {
        // TODO cope with large content where content length exceeds int capacity (2'048 MB)
        ByteArrayOutputStream baos = new ByteArrayOutputStream((int)contentLength);
        entity.writeTo(baos);
        byte[] responseBody = baos.toByteArray();
        String ageS = ccHeaderPatternMatcher.group(3);
        long ageL = Long.parseLong(ageS);
        CacheEntry ce = new CacheEntry(proxyRequest.getServiceRoute(), proxyRequest.getClientURI().getPath(),
                responseBody, responseHeaders, LocalDateTime.now().plusSeconds(ageL));
        lb.getCache().putEntry(ce);
        return responseBody;
    }

    private static long getContentLength(Header contentLengthHeader) {
        if (contentLengthHeader != null) {
            return getContentLength(contentLengthHeader.getValue());
        }
        return -1L;
    }

    private static long getContentLength(String contentLengthHeaderValue) {
        if (contentLengthHeaderValue != null) {
            return Long.parseLong(contentLengthHeaderValue);
        }
        return -1L;
    }

    private static void copyRequestHeaders(Headers requestHeaders, HttpRequest request, HttpHost svcHost) {
        requestHeaders.keySet().forEach(header -> {
            if ("Content-Length".equalsIgnoreCase(header)) {
            } else if ("Host".equalsIgnoreCase(header)) {
                request.addHeader(header, svcHost.toHostString());
            } else {
                requestHeaders.get(header).forEach(value -> {
                    request.addHeader(header, value);
                });
            }
        });
    }

    private static void copyResponseHeaders(Header[] svcResponseHeaders, Headers clientResponseHeaders) {
        for (Header header : svcResponseHeaders) {
            if ("Content-Length".equalsIgnoreCase(header.getName())) {
            } else {
                clientResponseHeaders.add(header.getName(), header.getValue());
            }

        }

    }

}
