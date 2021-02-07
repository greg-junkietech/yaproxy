package com.github.greg.junkietech.yaproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

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

    public YAProxy(Configuration config) throws IOException {
        this.config = config;
        serviceMap = YAProxy.initServiceMap(config);
        server = YAProxy.initServer(config, serviceMap);
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
        LOGGER.info("server listening on {}:{}...", config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort());
        server.start();
    }

    private static Map<String, ALoadBalancer<ServiceRoute>> initServiceMap(Configuration config) {
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
            result.put(service.getDomain(), YAProxy.createLoadBalancer(config, routes));
            LOGGER.info("service {} (domain {}) initialized", service.getName(), service.getDomain());
        });
        return result;
    }

    private static HttpHost setupDownstreamProxy(HttpClientBuilder builder, EndPoint host, Service service,
            Proxy proxy) {
        String proxyHost = null;
        if (host.getDownstreamPoxy() != null) {
            proxyHost = host.getDownstreamPoxy();
        } else if (service.getDownstreamPoxy() != null) {
            proxyHost = service.getDownstreamPoxy();
        } else if (proxy.getDownstreamPoxy() != null) {
            proxyHost = proxy.getDownstreamPoxy();
        }
        if (proxyHost != null) {
            HttpHost result = HttpHost.create(proxyHost);
            builder.setProxy(result);
            return result;
        }
        return null;
    }

    private static ALoadBalancer<ServiceRoute> createLoadBalancer(Configuration config, List<ServiceRoute> targetList) {
        ALoadBalancer<ServiceRoute> result;
        switch (config.getLoadBalancerStrategy()) {
            case ROUND_ROBIN:
                result = new RoundRobinLoadBalancer<>(targetList);
                break;
            case RANDOM:
            default:
                result = new RandomLoadBalancer<>(targetList);
        }
        LOGGER.info("initialized load balancer with {} strategy", config.getLoadBalancerStrategy());
        return result;
    }

    private static HttpServer initServer(Configuration config,
            final Map<String, ALoadBalancer<ServiceRoute>> serviceMap) throws IOException {
        HttpServer result = HttpServer.create(new InetSocketAddress(config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort()), 0);
        LOGGER.info("initializing server listening on {}:{}", config.getProxy().getListen().getAddress(),
                config.getProxy().getListen().getPort());
        result.createContext("/", new RequestHandler(serviceMap));
        result.setExecutor(Executors.newCachedThreadPool());
        return result;
    }
}
