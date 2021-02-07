package com.github.greg.junkietech.yaproxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.greg.junkietech.yaproxy.config.Configuration;

public class Main {

    private final static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        Configuration config = Main.initConfig(args);
        new YAProxy(config).start();
    }

    private static Configuration initConfig(String[] args) throws IOException {
        Configuration config;
        if (args.length != 1) {
            LOGGER.warn("Usage: <config.yml>");
            throw new UnsupportedOperationException("config.yml location is required as program argument");
        }

        Path path = Paths.get(args[0]);
        try (InputStream in = Files.newInputStream(path)) {
            // try (InputStream in = App.class.getResourceAsStream("/config.yaml")) {
            config = Configuration.loadYaml(in);
            LOGGER.info("loaded config from " + path.toAbsolutePath());
        }
        return config;
    }
}
