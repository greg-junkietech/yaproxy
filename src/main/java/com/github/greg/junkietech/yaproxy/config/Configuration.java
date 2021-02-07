package com.github.greg.junkietech.yaproxy.config;

import java.io.InputStream;

import org.yaml.snakeyaml.Yaml;

public class Configuration {

    private Proxy proxy;

    private LoadBalancerStrategy loadBalancerStrategy = LoadBalancerStrategy.RANDOM;

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public LoadBalancerStrategy getLoadBalancerStrategy() {
        return loadBalancerStrategy;
    }

    public void setLoadBalancerStrategy(LoadBalancerStrategy loadBalancerStrategy) {
        this.loadBalancerStrategy = loadBalancerStrategy;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(String.format("Proxy: %s\n", proxy))
                .append(String.format("LoadBalancerStrategy: %s\n", loadBalancerStrategy)).toString();
    }

    public static Configuration loadYaml(InputStream in) {
        Yaml yaml = new Yaml();
        return yaml.loadAs(in, Configuration.class);
    }

    public static enum LoadBalancerStrategy {
        RANDOM, ROUND_ROBIN
    }

}
