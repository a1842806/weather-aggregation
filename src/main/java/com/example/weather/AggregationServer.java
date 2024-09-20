package com.example.weather;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AggregationServer {
    private static final Logger LOGGER = Logger.getLogger(AggregationServer.class.getName());
    private static final String STORAGE_FILE = "weather_data.json";
    private static final int DEFAULT_PORT = 4567;
    private static final long EXPIRY_TIME = 30000; // 30 seconds in milliseconds

    private final Map<String, Map<String, String>> weatherDataMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private int lamportClock = 0;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        AggregationServer server = new AggregationServer();
        server.start(port);
    }

    private void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/weather.json", new WeatherHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            LOGGER.info("Server started on port " + port);

            // Start a thread to handle data expiration
            new Thread(this::handleDataExpiration).start();

            // Load existing data from file
            loadDataFromFile();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start server", e);
        }
    }

    private class WeatherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            
            // Update Lamport clock
            String clientLamportClock = exchange.getRequestHeaders().getFirst("X-Lamport-Clock");
            if (clientLamportClock != null) {
                updateLamportClock(Integer.parseInt(clientLamportClock));
            }

            if ("GET".equalsIgnoreCase(requestMethod)) {
                handleGetRequest(exchange);
            } else if ("PUT".equalsIgnoreCase(requestMethod)) {
                handlePutRequest(exchange);
            } else {
                sendResponse(exchange, 400, "Bad Request");
            }
        }
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
        List<Map<String, String>> allData = new ArrayList<>(weatherDataMap.values());
        String response = JSONParser.convertToJson(allData);
        sendResponse(exchange, 200, response);
    }

    private void handlePutRequest(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }

        try {
            Map<String, String> weatherData = JSONParser.convertToMap(requestBody.toString());
            String id = weatherData.get("id");
            if (id == null) {
                sendResponse(exchange, 400, "Missing 'id' field in weather data");
                return;
            }

            boolean isNewEntry = !weatherDataMap.containsKey(id);
            weatherDataMap.put(id, weatherData);
            lastUpdateTime.put(id, System.currentTimeMillis());

            saveDataToFile();

            int statusCode = isNewEntry ? 201 : 200;
            sendResponse(exchange, statusCode, "Success");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing PUT request", e);
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Lamport-Clock", String.valueOf(lamportClock));
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void handleDataExpiration() {
        while (true) {
            long currentTime = System.currentTimeMillis();
            lastUpdateTime.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > EXPIRY_TIME) {
                    weatherDataMap.remove(entry.getKey());
                    return true;
                }
                return false;
            });

            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void saveDataToFile() {
        try (FileWriter file = new FileWriter(STORAGE_FILE)) {
            String jsonData = JSONParser.convertToJson(new ArrayList<>(weatherDataMap.values()));
            file.write(jsonData);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving data to file", e);
        }
    }

    private void loadDataFromFile() {
        File file = new File(STORAGE_FILE);
        if (file.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(STORAGE_FILE)));
                List<Map<String, String>> dataList = JSONParser.convertToMapList(content);
                for (Map<String, String> data : dataList) {
                    String id = data.get("id");
                    if (id != null) {
                        weatherDataMap.put(id, data);
                        lastUpdateTime.put(id, System.currentTimeMillis());
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error loading data from file", e);
            }
        }
    }

    private void updateLamportClock(int clientLamportClock) {
        lamportClock = Math.max(lamportClock, clientLamportClock) + 1;
    }
}