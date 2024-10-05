package com.example.weather;

// Required imports for the HTTP server, file handling, concurrency, and logging
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AggregationServer acts as a central server that collects weather data from multiple ContentServers
 * and serves aggregated data to GETClients. It handles data storage, expiration, and synchronization
 * using Lamport clocks.
 */
public class AggregationServer {
    // Logger for logging messages
    private static final Logger LOGGER = Logger.getLogger(AggregationServer.class.getName());
    // File to store all weather entries
    private static final String DATA_FILE = "weather_data.json";
    // Default port for the server
    private static final int DEFAULT_PORT = 4567;
    // Maximum number of stations to store
    private static final int MAX_STATIONS = 20;
    // Time in milliseconds after which data is considered expired
    private static final long EXPIRY_TIME = 30000; // 30 seconds in milliseconds

    // LinkedHashMap to store weather data per stationID, maintaining insertion order
    private final LinkedHashMap<String, WeatherEntry> weatherDataMap = new LinkedHashMap<>();
    // Lock object for thread safety
    private final Object dataLock = new Object();

    // Lamport clock for synchronization
    private int lamportClock = 0;
    // Lock object for synchronizing Lamport clock updates
    private final Object lamportLock = new Object(); // For thread-safe Lamport clock updates

    /**
     * Main method to start the AggregationServer.
     *
     * @param args Command-line arguments. Optional port number can be provided as the first argument.
     */
    public static void main(String[] args) {
        int port;
        try {
            // Determine the port to run the server on
            port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            // Handle invalid port number input
            LOGGER.severe("Invalid port number. Using default port " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }
        AggregationServer server = new AggregationServer();
        server.start(port);
    }

    /**
     * Starts the Aggregation Server on the specified port.
     *
     * @param port The port number to start the server on.
     */
    private void start(int port) {
        try {
            // Create an HTTP server instance
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            // Define the context and handler for /weather.json endpoint
            server.createContext("/weather.json", new WeatherHandler());
            // Set the executor for handling incoming requests
            server.setExecutor(Executors.newCachedThreadPool());
            // Start the server
            server.start();
            LOGGER.info("Server started on port " + port);

            // Load existing data from the file into memory
            loadDataFromFile();

            // Start a scheduled task to handle data expiration every second
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::handleDataExpiration, 1, 1, TimeUnit.SECONDS);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start server", e);
        } catch (SecurityException e) {
            LOGGER.log(Level.SEVERE, "Insufficient permissions to start server or access files", e);
        }
    }

    /**
     * Inner class that handles HTTP requests to the /weather.json endpoint.
     */
    private class WeatherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String requestMethod = exchange.getRequestMethod();

                // Retrieve and update the Lamport clock from the client's request header
                String clientLamportClock = exchange.getRequestHeaders().getFirst("X-Lamport-Clock");
                if (clientLamportClock != null) {
                    try {
                        updateLamportClock(Integer.parseInt(clientLamportClock));
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid Lamport clock value from client: " + clientLamportClock);
                        // Return 400 Bad Request if Lamport clock is invalid
                        sendResponse(exchange, 400, "Invalid Lamport Clock");
                        return;
                    }
                }

                // Handle the request based on its method (GET or PUT)
                if ("GET".equalsIgnoreCase(requestMethod)) {
                    handleGetRequest(exchange);
                } else if ("PUT".equalsIgnoreCase(requestMethod)) {
                    handlePutRequest(exchange);
                } else {
                    // Return 400 Bad Request for unsupported methods
                    sendResponse(exchange, 400, "Bad Request");
                }
            } catch (Exception e) {
                // Catch any unexpected exceptions and return 500 Internal Server Error
                LOGGER.log(Level.SEVERE, "Unexpected error handling request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Handles GET requests to retrieve weather data.
     *
     * @param exchange The HttpExchange object representing the request and response.
     * @throws IOException If an I/O error occurs.
     */
    private void handleGetRequest(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String stationId = null;
        if (query != null) {
            // Parse the query string to extract parameters
            Map<String, String> queryParams = parseQueryString(query);
            stationId = queryParams.get("station");
        }

        Map<String, String> responseData = null;

        synchronized (dataLock) {
            if (stationId != null) {
                // If a station ID is specified, retrieve data for that station
                WeatherEntry entry = weatherDataMap.get(stationId);
                if (entry != null) {
                    responseData = entry.getData();
                }
            } else {
                // If no station ID is specified, return the most recent weather data
                if (!weatherDataMap.isEmpty()) {
                    // Find the entry with the latest timestamp
                    WeatherEntry latestEntry = null;
                    for (WeatherEntry entry : weatherDataMap.values()) {
                        if (latestEntry == null || entry.getTimestamp() > latestEntry.getTimestamp()) {
                            latestEntry = entry;
                        }
                    }
                    if (latestEntry != null) {
                        responseData = latestEntry.getData();
                    }
                } else {
                    // Return 204 No Content if there is no data
                    sendResponse(exchange, 204, "No Content");
                    return;
                }
            }
        }

        // Check if there is data to send
        if (responseData == null) {
            sendResponse(exchange, 204, "No Content");
        } else {
            // Convert the data to JSON and send the response
            String response = JSONParser.convertToJson(responseData);
            sendResponse(exchange, 200, response);
        }
    }

    /**
     * Parses a query string into a map of key-value pairs.
     *
     * @param query The query string to parse.
     * @return A map containing the parsed query parameters.
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new HashMap<>();
        // Split the query string by '&' to separate parameters
        for (String param : query.split("&")) {
            // Split each parameter by '=' to get key and value
            String[] pair = param.split("=", 2);
            try {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : "";
                result.put(key, value);
            } catch (UnsupportedEncodingException e) {
                LOGGER.warning("Unsupported encoding in query parameter: " + param);
            }
        }
        return result;
    }

    /**
     * Handles PUT requests to store weather data.
     *
     * @param exchange The HttpExchange object representing the request and response.
     * @throws IOException If an I/O error occurs.
     */
    private void handlePutRequest(HttpExchange exchange) throws IOException {
        // Retrieve the Content-Length header to check if there is content
        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthHeader == null || contentLengthHeader.trim().isEmpty()) {
            // Return 204 No Content if Content-Length is missing or empty
            sendResponse(exchange, 204, "No Content");
            return;
        }

        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthHeader);
        } catch (NumberFormatException e) {
            // Return 400 Bad Request if Content-Length is invalid
            sendResponse(exchange, 400, "Invalid Content-Length");
            return;
        }

        if (contentLength == 0) {
            // Return 204 No Content if Content-Length is zero
            sendResponse(exchange, 204, "No Content");
            return;
        }

        // Read the request body containing the weather data
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line).append("\n");
        }

        boolean isNewStation = false;

        try {
            // Convert the JSON request body to a LinkedHashMap to preserve order
            LinkedHashMap<String, String> weatherData = (LinkedHashMap<String, String>) JSONParser.convertToMap(requestBody.toString());
            String id = weatherData.get("id");
            if (id == null) {
                // Return 400 Bad Request if 'id' field is missing
                sendResponse(exchange, 400, "Missing 'id' field in JSON data");
                return;
            }

            // Update Lamport clock for the received event
            incrementLamportClock();

            // Add Lamport clock to the data
            weatherData.put("lamportClock", String.valueOf(lamportClock));

            synchronized (dataLock) {
                // Check if it's a new station
                isNewStation = !weatherDataMap.containsKey(id);

                // Remove the old entry if it exists to update the insertion order
                if (weatherDataMap.containsKey(id)) {
                    weatherDataMap.remove(id);
                } else if (weatherDataMap.size() >= MAX_STATIONS) {
                    // Remove the oldest entry if size exceeds MAX_STATIONS
                    Iterator<String> iterator = weatherDataMap.keySet().iterator();
                    if (iterator.hasNext()) {
                        String oldestKey = iterator.next();
                        iterator.remove();
                        LOGGER.info("Removed oldest station ID to maintain max size: " + oldestKey);
                    }
                }

                // Add the new WeatherEntry with current timestamp
                WeatherEntry entry = new WeatherEntry(weatherData, System.currentTimeMillis());
                weatherDataMap.put(id, entry);
            }

            // Check if the data file exists before saving
            boolean dataFileExisted = Files.exists(Paths.get(DATA_FILE));

            // Save data to the file
            saveDataToFile();

            // Determine the appropriate status code
            int statusCode;
            if (isNewStation || !dataFileExisted) {
                statusCode = 201; // HTTP Created
            } else {
                statusCode = 200; // HTTP OK
            }

            // Return appropriate status code
            sendResponse(exchange, statusCode, "Success");
        } catch (IllegalArgumentException e) {
            // Return 500 Internal Server Error if JSON parsing fails
            LOGGER.warning("Invalid JSON data: " + e.getMessage());
            sendResponse(exchange, 500, "Internal Server Error");
        } catch (Exception e) {
            // Log the exception and return 500 Internal Server Error
            LOGGER.log(Level.SEVERE, "Error processing PUT request", e);
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Sends an HTTP response to the client.
     *
     * @param exchange   The HttpExchange object representing the request and response.
     * @param statusCode The HTTP status code to send.
     * @param response   The response body as a string.
     * @throws IOException If an I/O error occurs.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        // Update Lamport clock before sending the response
        incrementLamportClock();

        // Set response headers
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Lamport-Clock", String.valueOf(lamportClock));
        // Convert response string to bytes
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        // Send response headers
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        // Write the response body
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error writing response", e);
            throw e;
        }
    }

    /**
     * Saves all weather data to a single file.
     */
    private void saveDataToFile() {
        synchronized (dataLock) {
            Path dataFilePath = Paths.get(DATA_FILE);
            // Write data to a temporary file first for atomicity
            Path tempFilePath = dataFilePath.resolveSibling(dataFilePath.getFileName() + ".tmp");
            try (FileWriter file = new FileWriter(tempFilePath.toFile())) {
                // Convert data to JSON and write to file
                List<Map<String, String>> dataList = new ArrayList<>();
                for (WeatherEntry entry : weatherDataMap.values()) {
                    dataList.add(entry.getData());
                }
                String jsonData = JSONParser.convertToJson(dataList);
                file.write(jsonData);
                file.flush();
                // Atomically replace the old file with the new file
                Files.move(tempFilePath, dataFilePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error saving data to file", e);
            }
        }
    }

    /**
     * Loads existing weather data from the file into memory.
     */
    private void loadDataFromFile() {
        Path dataFilePath = Paths.get(DATA_FILE);
        if (Files.exists(dataFilePath)) {
            synchronized (dataLock) {
                try {
                    // Read the content of the file
                    String content = new String(Files.readAllBytes(dataFilePath), StandardCharsets.UTF_8);
                    // Convert JSON content to a list of Maps
                    List<Map<String, String>> dataList = JSONParser.convertToMapList(content);
                    for (Map<String, String> data : dataList) {
                        String id = data.get("id");
                        if (id != null) {
                            // Assume current time as timestamp (you can adjust if timestamps are saved)
                            WeatherEntry entry = new WeatherEntry((LinkedHashMap<String, String>) data, System.currentTimeMillis());
                            weatherDataMap.put(id, entry);

                            // Update Lamport clock based on loaded data
                            String entryLamportClock = data.get("lamportClock");
                            if (entryLamportClock != null) {
                                try {
                                    updateLamportClock(Integer.parseInt(entryLamportClock));
                                } catch (NumberFormatException e) {
                                    LOGGER.warning("Invalid Lamport clock value in file for station ID: " + id);
                                }
                            }
                        }
                    }
                    LOGGER.info("Loaded existing data from file.");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading data from file", e);
                }
            }
        }
    }

    /**
     * Periodically checks for expired data and removes it.
     */
    private void handleDataExpiration() {
        long currentTime = System.currentTimeMillis();
        synchronized (dataLock) {
            Iterator<Map.Entry<String, WeatherEntry>> iterator = weatherDataMap.entrySet().iterator();
            boolean dataChanged = false;
            while (iterator.hasNext()) {
                Map.Entry<String, WeatherEntry> entry = iterator.next();
                // Check if the data has expired
                if (currentTime - entry.getValue().getTimestamp() > EXPIRY_TIME) {
                    iterator.remove();
                    dataChanged = true;
                    LOGGER.info("Expired data for station ID: " + entry.getKey());
                }
            }
            // Save data to file if any entries were removed
            if (dataChanged) {
                saveDataToFile();
            }
        }
    }

    /**
     * Updates the Lamport clock based on the client's clock.
     *
     * @param clientLamportClock The Lamport clock value received from the client.
     */
    private void updateLamportClock(int clientLamportClock) {
        synchronized (lamportLock) {
            // Update the Lamport clock following the Lamport clock rules
            lamportClock = Math.max(lamportClock, clientLamportClock) + 1;
            LOGGER.fine("Lamport Clock updated to: " + lamportClock);
        }
    }

    /**
     * Increments the Lamport clock for internal events.
     */
    private void incrementLamportClock() {
        synchronized (lamportLock) {
            lamportClock++;
            LOGGER.fine("Lamport Clock incremented to: " + lamportClock);
        }
    }

    /**
     * Inner class to store weather data along with timestamp.
     */
    private static class WeatherEntry {
        // Map containing the weather data fields
        private final LinkedHashMap<String, String> data;
        // Timestamp when the data was last updated
        private final long timestamp;

        /**
         * Constructs a new WeatherEntry.
         *
         * @param data      The weather data map.
         * @param timestamp The timestamp when the data was received.
         */
        public WeatherEntry(LinkedHashMap<String, String> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        /**
         * Retrieves the weather data map.
         *
         * @return The weather data map.
         */
        public LinkedHashMap<String, String> getData() {
            return data;
        }

        /**
         * Retrieves the timestamp when the data was last updated.
         *
         * @return The timestamp in milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }
    }
}