package io.dropwizard.logging;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.validation.BaseValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketAppenderFactoryTest {

    private static final int TCP_PORT = 24562;

    private Thread thread;
    private CountDownLatch countDownLatch = new CountDownLatch(100);
    private ServerSocket ss;

    @Before
    public void setUp() throws Exception {
        ss = new ServerSocket(TCP_PORT);
        thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket;
                try {
                    socket = ss.accept();
                } catch (SocketException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                new Thread(() -> readData(socket)).start();
            }
        });
        thread.start();
    }

    private void readData(Socket socket) {
        try (Socket s = socket; BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(),
            StandardCharsets.UTF_8))) {
            int counter = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                assertThat(line).startsWith("INFO").contains("com.example.app: Application log " + (counter++));
                countDownLatch.countDown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @After
    public void tearDown() throws Exception {
        thread.interrupt();
        ss.close();
    }

    @Test
    public void testStartSocketAppender() throws Exception {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        objectMapper.getSubtypeResolver().registerSubtypes(TcpSocketAppenderFactory.class);

        YamlConfigurationFactory<DefaultLoggingFactory> factory = new YamlConfigurationFactory<>(
            DefaultLoggingFactory.class, BaseValidator.newValidator(), objectMapper, "dw");
        DefaultLoggingFactory defaultLoggingFactory = factory.build(new File(Resources.getResource(
            "yaml/logging-tcp.yml").toURI()));
        defaultLoggingFactory.configure(new MetricRegistry(), "tcp-test");

        Logger logger = LoggerFactory.getLogger("com.example.app");
        for (int i = 0; i < 100; i++) {
            logger.info("Application log {}", i);
        }
        countDownLatch.await();
    }
}
