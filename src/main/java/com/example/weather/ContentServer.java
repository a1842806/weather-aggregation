package com.example.weather;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ContentServer {
    private static final Logger LOGGER = Logger.getLogger(ContentServer.class.getName());
    private static int lamportClock = 0;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 5000;

    public static void main(String[] args) {
        // Check for the correct number of arguments
        if (args.length != 2) {
            LOGGER.severe("Usage: java ContentServer <server-url> <file-path>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];

        try {
            // Read the weather data from the file and convert it to JSON
            Map<String, String> weatherData = readFileAndParseData(filePath);

            // Convert the data to JSON string manually
            String jsonData = JSONParser.convertToJson(weatherData);

            // Send the PUT request to the server
            sendPutRequestWithRetry(serverUrl, jsonData);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading file or sending data", e);
        }
    }

    // Read the file and parse the weather data
    private static Map<String, String> readFileAndParseData(String filePath) throws IOException {
        Map<String, String> data = new HashMap<>();

        // Read the file line by line and parse the data
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
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

    // Send the PUT request with retries
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

    // Send the PUT request to the aggregation server with the JSON data
    private static boolean sendPutRequest(String serverUrl, String jsonData) throws IOException {
        HttpURLConnection connection = null;
        try {
            URI uri = new URI(serverUrl + "/weather.json");
            URL url = uri.toURL();

            // Open a connection and set the request properties
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", String.valueOf(jsonData.getBytes().length));
            connection.setRequestProperty("X-Lamport-Clock", String.valueOf(lamportClock));

            // Send JSON data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonData.getBytes());
                os.flush();
            }

            // Get the response code and handle failures
            int responseCode = connection.getResponseCode();
            
            // Update Lamport clock
            String serverLamportClock = connection.getHeaderField("X-Lamport-Clock");
            if (serverLamportClock != null) {
                updateLamportClock(Integer.parseInt(serverLamportClock));
            }

            // Check if the upload was successful
            LOGGER.info("Response Code: " + responseCode);
            return (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED);

        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Invalid server URL", e);
            throw new IOException("Invalid server URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Update the Lamport clock based on the server's response
    private static void updateLamportClock(int serverLamportClock) {
        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
    }
}