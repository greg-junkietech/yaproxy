package com.github.greg.junkietech.yaproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.greg.junkietech.yaproxy.config.Configuration;
import com.github.greg.junkietech.yaproxy.config.Proxy;
import com.github.greg.junkietech.yaproxy.config.Proxy.EndPoint;
import com.github.greg.junkietech.yaproxy.config.Proxy.Service;
import com.github.greg.junkietech.yaproxy.lb.ALoadBalancer;
import com.github.greg.junkietech.yaproxy.lb.RandomLoadBalancer;
import com.github.greg.junkietech.yaproxy.lb.RoundRobinLoadBalancer;
import com.sun.net.httpserver.HttpServer;

public class YAProxy {

    private final static Logger LOGGER = LoggerFactory.getLogger(YAProxy.class);

    private static final int MAX_LIMIT_OF_CONN_PER_SVC_HOST_ROUTE = 50;

    private final Configuration config;
    private final Map<String, ALoadBalancer<ServiceRoute>> serviceMap;
    private final HttpServer server;
    private final ScheduledExecutorService clearCacheService;

    public YAProxy(Configuration config) throws IOException {
        this.config = config;
        this.clearCacheService = Executors.newScheduledThreadPool(config.getProxy().getServices().size());
        this.serviceMap = initServiceMap();
        this.server = initServer();
        initShutdownHook();
    }

    private void initShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("shutting down server...");
            server.stop(3);
            for (ALoadBalancer<ServiceRoute> lb : serviceMap.values()) {
                for (ServiceRoute svcRoute : lb.getTargetList()) {
                    svcRoute.close();
                }
            }
        }));
    }

    public void start() {
        server.start();
        LOGGER.info("server listening on {}:{}...", config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort());
    }

    private Map<String, ALoadBalancer<ServiceRoute>> initServiceMap() {
        final Map<String, ALoadBalancer<ServiceRoute>> result = new ConcurrentHashMap<>(
                config.getProxy().getServices().size());
        config.getProxy().getServices().forEach(service -> {
            LOGGER.info("initializing service {} (domain {})...", service.getName(), service.getDomain());
            List<ServiceRoute> routes = new ArrayList<ServiceRoute>();
            service.getHosts().forEach(host -> {
                PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
                connManager.setMaxTotal(MAX_LIMIT_OF_CONN_PER_SVC_HOST_ROUTE);
                connManager.setDefaultMaxPerRoute(MAX_LIMIT_OF_CONN_PER_SVC_HOST_ROUTE);
                HttpClientBuilder builder = HttpClients.custom().setConnectionManager(connManager)
                        .disableContentCompression();
                HttpHost downstreamProxy = YAProxy.setupDownstreamProxy(builder, host, service, config.getProxy());
                CloseableHttpClient httpClient = builder.build();
                routes.add(new ServiceRoute(service.getName(), service.getDomain(), httpClient,
                        new HttpHost(host.getAddress(), host.getPort())));
                if (downstreamProxy == null) {
                    LOGGER.info("initialized http client for route to service {} (domain {}) host {}:{}",
                            service.getName(), service.getDomain(), host.getAddress(), host.getPort());
                } else {
                    LOGGER.info("initialized http client for route to service {} (domain {}) host {}:{} over proxy {}",
                            service.getName(), service.getDomain(), host.getAddress(), host.getPort(),
                            downstreamProxy.toHostString());
                }
            });
            result.put(service.getDomain(), createLoadBalancer(routes, service.getName()));
            LOGGER.info("service {} (domain {}) initialized", service.getName(), service.getDomain());
        });
        return result;
    }

    private static HttpHost setupDownstreamProxy(HttpClientBuilder builder, EndPoint host, Service service,
            Proxy proxy) {
        String proxyHost = null;
        HttpHost result = null;
        if (host.getDownstreamPoxy() != null) {
            proxyHost = host.getDownstreamPoxy();
        } else if (service.getDownstreamPoxy() != null) {
            proxyHost = service.getDownstreamPoxy();
        } else if (proxy.getDownstreamPoxy() != null) {
            proxyHost = proxy.getDownstreamPoxy();
        }
        if (proxyHost != null) {
            result = HttpHost.create(proxyHost);
            builder.setProxy(result);
        }
        return result;
    }

    private ALoadBalancer<ServiceRoute> createLoadBalancer(List<ServiceRoute> targetList, String serviceName) {
        final ALoadBalancer<ServiceRoute> result;
        switch (config.getLoadBalancerStrategy()) {
            case ROUND_ROBIN:
                result = new RoundRobinLoadBalancer<>(targetList, String.format("lb-%s", serviceName));
                break;
            case RANDOM:
            default:
                result = new RandomLoadBalancer<>(targetList, String.format("lb-%s", serviceName));
        }
        LOGGER.info("initialized load balancer with {} strategy", config.getLoadBalancerStrategy());
        clearCacheService.scheduleAtFixedRate(() -> {
            int count = result.getCache().invalidateExpiredEntries();
            LOGGER.info("{} cache entr{} invalidated for {}", count, count > 1 ? "ies" : "y", result.getName());
        }, 1, 1, TimeUnit.MINUTES);
        return result;
    }

    private HttpServer initServer() throws IOException {
        HttpServer result = HttpServer.create(new InetSocketAddress(config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort()), 0);
        LOGGER.info("initializing server listening on {}:{}", config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort());
        result.createContext("/", new RequestHandler(serviceMap));
        result.setExecutor(Executors.newCachedThreadPool());
        return result;
    }
}
