package com.github.greg.junkietech.yaproxy.util;

import java.net.URI;

import org.apache.http.HttpHost;

public abstract class RewriteURLUtil {

    public static String rewriteURL(URI uri, HttpHost svcHost) {
        String uriTrail = uri.toString().replaceFirst("http:\\/\\/[^\\/]+", "");
        String result = String.format("http://%s:%d%s", svcHost.getHostName(), svcHost.getPort(), uriTrail);
        return result;
    }
}
