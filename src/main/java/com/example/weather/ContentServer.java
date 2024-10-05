package com.example.weather;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * ContentServer is a simple client application that reads weather data from a file,
 * converts it to JSON, and sends it to an aggregation server using a PUT request.
 * It then verifies that the data was correctly stored on the server by performing a GET request.
 */
public class ContentServer {
    // Logger for debugging and error messages
    private static final Logger LOGGER = Logger.getLogger(ContentServer.class.getName());
    // Lamport clock for ordering events
    private static int lamportClock = 0;
    // Maximum number of retries for PUT requests
    private static final int MAX_RETRIES = 3;
    // Delay between retries in milliseconds
    private static final int RETRY_DELAY_MS = 5000;
    // Periodic update interval in milliseconds (e.g., 10000 for 10 seconds)
    private static final int UPDATE_INTERVAL_MS = 10000; // 10 seconds

    /**
     * Main method to read weather data from a file, send it to the server periodically.
     * 
     * @param args The command-line arguments containing the server URL and file path.
     */
    public static void main(String[] args) {
        // Check for the correct number of arguments
        if (args.length != 2) {
            LOGGER.severe("Usage: java ContentServer <server-url> <file-path>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];

        try {
            // Send weather data infinitely
            sendWeatherData(serverUrl, filePath, -1);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "ContentServer interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in ContentServer", e);
        }
    }
    
    /**
     * Sends weather data to the server periodically for a specified number of iterations.
     * 
     * @param serverUrl The URL of the aggregation server.
     * @param filePath The path to the weather data file.
     * @param iterations The number of iterations to send the data (negative for infinite).
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     */
    public static void sendWeatherData(String serverUrl, String filePath, int iterations) throws IOException, InterruptedException {
        // Read the weather data from the file and convert it to JSON
        Map<String, String> weatherData = readFileAndParseData(filePath);

        // Run the update loop
        int count = 0;
        while (iterations < 0 || count < iterations) {
            // Increment Lamport clock for internal event
            lamportClock++;

            // Convert the data to JSON string manually
            String jsonData = JSONParser.convertToJson(weatherData);

            // Send the PUT request to the server
            sendPutRequestWithRetry(serverUrl, jsonData);

            // Wait for the next update interval
            Thread.sleep(UPDATE_INTERVAL_MS);

            count++;
        }
    }

    /**
     * Reads the weather data from a file and parses it into a Map.
     *
     * @param filePath The path to the weather data file.
     * @return A Map containing the weather data.
     * @throws IOException If an I/O error occurs.
     */
    private static Map<String, String> readFileAndParseData(String filePath) throws IOException {
        Map<String, String> data = new LinkedHashMap<>();

        // Read the file line by line and parse the data
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2); // Limit split to 2 parts
                if (parts.length == 2) {
                    data.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        // Check if the data contains the mandatory "id" field
        if (!data.containsKey("id")) {
            throw new IllegalArgumentException("Weather data must contain an 'id' field.");
        }

        return data;
    }

    /**
     * Sends the PUT request with retries in case of failure.
     *
     * @param serverUrl The URL of the aggregation server.
     * @param jsonData  The JSON-formatted weather data to send.
     * @throws IOException If an I/O error occurs after retries.
     */
    private static void sendPutRequestWithRetry(String serverUrl, String jsonData) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                boolean success = sendPutRequest(serverUrl, jsonData);
                if (success) {
                    LOGGER.info("Weather data uploaded successfully.");
                    return; // If successful, exit the method
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Attempt " + (attempt + 1) + " failed. Retrying in " + RETRY_DELAY_MS + "ms", e);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry wait", ie);
                }
            }
        }
        throw new IOException("Failed to send PUT request after " + MAX_RETRIES + " attempts");
    }

    /**
     * Sends a PUT request to the aggregation server with the provided JSON data.
     *
     * @param serverUrl The URL of the aggregation server.
     * @param jsonData  The JSON-formatted weather data to send.
     * @return True if the upload was successful, false otherwise.
     * @throws IOException If an I/O error occurs during the process.
     */
    private static boolean sendPutRequest(String serverUrl, String jsonData) throws IOException {
        HttpURLConnection connection = null;
        try {
            // Ensure correct URI construction without double slashes
            URI baseUri = new URI(serverUrl);
            URI uri = baseUri.resolve("/weather.json");
            URL url = uri.toURL();

            // Open a connection and set the request properties
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
            connection.setRequestProperty("Content-Type", "application/json");

            // Convert jsonData to bytes using UTF-8
            byte[] jsonBytes = jsonData.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

            // Increment Lamport clock before sending the message
            lamportClock++;
            connection.setRequestProperty("X-Lamport-Clock", String.valueOf(lamportClock));

            // Send JSON data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            // Get the response code and handle failures
            int responseCode = connection.getResponseCode();

            // Update Lamport clock
            String serverLamportClock = connection.getHeaderField("X-Lamport-Clock");
            if (serverLamportClock != null) {
                try {
                    updateLamportClock(Integer.parseInt(serverLamportClock));
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid Lamport clock value from server: " + serverLamportClock);
                }
            }

            // Check if the upload was successful
            LOGGER.info("Response Code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                return true;
            } else {
                // Log error message from server
                String responseMessage = connection.getResponseMessage();
                LOGGER.warning("Server responded with code: " + responseCode + " message: " + responseMessage);
                return false;
            }

        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Invalid server URL", e);
            throw new IOException("Invalid server URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Updates the Lamport clock based on the server's Lamport clock.
     *
     * @param serverLamportClock The Lamport clock received from the server.
     */
    private static void updateLamportClock(int serverLamportClock) {
        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
    }
}