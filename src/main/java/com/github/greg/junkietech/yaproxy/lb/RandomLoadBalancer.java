package com.github.greg.junkietech.yaproxy.lb;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/* Adapted from https://turkogluc.com/java-concurrency-with-load-balancer-simulation/ */

public class RandomLoadBalancer<T> extends ALoadBalancer<T> {
    public RandomLoadBalancer(List<T> targetList) {
        super(targetList);
    }

    @Override
    public T nextTarget() {
        return getTargetList().get(ThreadLocalRandom.current().nextInt(getTargetList().size()));
    }

}
