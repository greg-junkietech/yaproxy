package com.github.greg.junkietech.yaproxy.lb;

import java.util.Collections;
import java.util.List;

/* Adapted from https://turkogluc.com/java-concurrency-with-load-balancer-simulation/ */

public abstract class ALoadBalancer<T> {

    private final List<T> targetList;
    private final Cache cache;
    private final String name;

    public ALoadBalancer(List<T> targetList, String name) {
        this.targetList = Collections.unmodifiableList(targetList);
        this.name = name;
        this.cache = new Cache();
    }

    public abstract T nextTarget();

    public List<T> getTargetList() {
        return targetList;
    }

    public Cache getCache() {
        return cache;
    }

    public String getName() {
        return name;
    }

}
