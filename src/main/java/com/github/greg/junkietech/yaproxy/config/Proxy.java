package com.github.greg.junkietech.yaproxy.config;

import java.util.List;

public class Proxy {

    private EndPoint listen;
    private List<Service> services;
    private String downstreamPoxy;

    public EndPoint getListen() {
        return listen;
    }

    public void setListen(EndPoint listen) {
        this.listen = listen;
    }

    public List<Service> getServices() {
        return services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public String getDownstreamPoxy() {
        return downstreamPoxy;
    }

    public void setDownstreamPoxy(String downstreamPoxy) {
        this.downstreamPoxy = downstreamPoxy;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(String.format("Listen: %s\n", listen))
                .append(String.format("Services: %s\n", services))
                .append(String.format("DownstreamProxy: %s\n", downstreamPoxy)).toString();
    }

    public static class EndPoint {

        private String address;
        private int port;
        private String downstreamPoxy;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDownstreamPoxy() {
            return downstreamPoxy;
        }

        public void setDownstreamPoxy(String downstreamPoxy) {
            this.downstreamPoxy = downstreamPoxy;
        }

        @Override
        public String toString() {
            return new StringBuilder().append(String.format("Address: %s\n", address))
                    .append(String.format("Port: %s\n", port))
                    .append(String.format("DownstreamProxy: %s\n", downstreamPoxy)).toString();
        }
    }

    public static class Service {
        private String name;
        private String domain;
        private List<EndPoint> hosts;
        private String downstreamPoxy;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public List<EndPoint> getHosts() {
            return hosts;
        }

        public void setHosts(List<EndPoint> hosts) {
            this.hosts = hosts;
        }

        public String getDownstreamPoxy() {
            return downstreamPoxy;
        }

        public void setDownstreamPoxy(String downstreamPoxy) {
            this.downstreamPoxy = downstreamPoxy;
        }

        @Override
        public String toString() {
            return new StringBuilder().append(String.format("Name: %s\n", name))
                    .append(String.format("Domain: %s\n", domain)).append(String.format("Hosts: %s\n", hosts))
                    .append(String.format("DownstreamProxy: %s\n", downstreamPoxy)).toString();
        }

    }

}
