package com.github.greg.junkietech.yaproxy.util;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;

public class HttpIOUtil {
    public static void writeResponse(HttpExchange exchange, String response, int code) throws IOException {
        writeResponse(exchange, response.getBytes(), code);
    }

    public static void writeResponse(HttpExchange exchange, byte[] response, int code) throws IOException {
        exchange.sendResponseHeaders(code, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

}
