package com.github.greg.junkietech.yaproxy.lb;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/* Adapted from https://turkogluc.com/java-concurrency-with-load-balancer-simulation/ */

public class RoundRobinLoadBalancer<T> extends ALoadBalancer<T> {

    private int counter = 0;
    private final ReentrantLock lock;

    public RoundRobinLoadBalancer(List<T> targetList) {
        super(targetList);
        lock = new ReentrantLock();
    }

    @Override
    public T nextTarget() {
        lock.lock();
        try {
            T target = getTargetList().get(counter);
            counter += 1;
            if (counter == getTargetList().size()) {
                counter = 0;
            }
            return target;
        } finally {
            lock.unlock();
        }
    }
}
