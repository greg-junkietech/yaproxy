# yaproxy - Yet Another Proxy

## What it be

yaproxy is an experimental proxy implementation for HTTP/1.1 written in Java.
yaproxy is designed to be used alternatively as __reverse proxy__ or as traditional __forward proxy__.

Used as __reverse proxy__ yaproxy sits between multiple clients and one or several instances of a downstream service. It supports multiple downstream services with multiple instances, downstream services are identified using the `Host` HTTP header.
yaproxy listens to HTTP requests and forwards them to one of the instances of a downstream service that will process the requests.
Requests are load-balanced and multiple load-balancing strategies are supported.
After processing the request, the downstream service sends the HTTP response back to yaproxy, that forwards the response to the client making the initial request.

Used as __forward proxy__ yaproxy works exactly the same: the difference is only its configuration.

## Requirements
* Java 11
* Maven 3.3 (only for build)

## Usage
yaproxy reads its configuration from a file at start.
A standard `config.yml` looks like:
```
proxy:
  listen: 
    address: "127.0.0.1"
    port: 8080
  services:
    - name: my-service
      domain: my-service.my-company.com
      hosts:
        - address: "127.0.0.1"
          port: 8888
        - address: "127.0.0.2"
          port: 8888
          #downstreamPoxy: localhost:8888
```
A full fledged configuration may provide additional parameters like `downstreamPoxy` and `loadBalancerStrategy`:
```
proxy:
  listen: 
    address: "127.0.0.1"
    port: 8080
  services:
    - name: my-service
      domain: my-service.my-company.com
      #downstreamPoxy: localhost:8888
      hosts:
        - address: "127.0.0.1"
          port: 8888
        - address: "127.0.0.2"
          port: 8888
          #downstreamPoxy: localhost:8888
  #downstreamPoxy: localhost:8888
loadBalancerStrategy: ROUND_ROBIN | RANDOM
```
yaproxy can be executed using the command:
`java -jar yaproxy-1.0-SNAPSHOT-jar-with-dependencies.jar config.yml`

## Features
* multiple load balancer strategies
  * `RANDOM` (default)
  * `ROUND_ROBIN`
* in-memory cache, compliant with HTTP/1.1 header `Cache-Control`
  * matching regex [*(public, *)?(max-age|s-maxage)=(\d+)](https://regex101.com/r/1Gpcn6/1)
  * automatic cache invalidation (every 1') whenever cache entries expired
* support for downstream proxy (though without authentication mechanism)

## Known limitations
* No support for HTTPS
* No support for chunked response entities nor for compressed response content
* Headers from cached responses are forwarded as they were originally received (neither filtered nor adjusted)

## Third-party libraries
* org.yaml
* junit
* org.apache.logging.log4j
* org.apache.httpcomponents

## References
* [RFC 2616 about HTTP/1.1](https://tools.ietf.org/html/rfc2616)
* [Java Concurrency with Load Balancer Simulation](https://turkogluc.com/java-concurrency-with-load-balancer-simulation/)

## Contributing

Contributions are welcome.

## Copyright / License

Copyright 2021 Yaproxy

This software is licensed under the terms of the GNU General Public License v3.0. See the [LICENSE](./LICENSE) file.