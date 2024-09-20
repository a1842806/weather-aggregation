package com.example.weather;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class GETClient {
    private static final Logger LOGGER = Logger.getLogger(GETClient.class.getName());
    private static int lamportClock = 0;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 5000;

    public static void main(String[] args) {
        if (args.length < 1) {
            LOGGER.severe("Usage: java GETClient <server_url> [station_id]");
            return;
        }

        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        // Ensure the URL has the correct format
        serverUrl = ensureCorrectUrlFormat(serverUrl);

        // Build the GET request URL
        String requestUrl = buildRequestUrl(serverUrl, stationId);
        LOGGER.info("Requesting weather data from: " + requestUrl);

        // Send GET request to the aggregation server with retry mechanism
        sendGETRequestWithRetry(requestUrl);
    }

    private static String ensureCorrectUrlFormat(String url) {
        if (!url.matches("^(http://|https://).*")) {
            url = "http://" + url;
        }
        return url;
    }

    private static String buildRequestUrl(String serverUrl, String stationId) {
        return stationId != null ? serverUrl + "/weather.json?station=" + stationId : serverUrl + "/weather.json";
    }

    private static void sendGETRequestWithRetry(String requestUrl) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                sendGETRequest(requestUrl);
                return; // If successful, exit the method
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Attempt " + (attempt + 1) + " failed. Retrying in " + RETRY_DELAY_MS + "ms", e);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.severe("Interrupted during retry wait");
                    return;
                }
            }
        }
        LOGGER.severe("Failed to send GET request after " + MAX_RETRIES + " attempts");
    }

    private static void sendGETRequest(String requestUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(requestUrl);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Increment and send Lamport clock
            incrementLamportClock();
            conn.setRequestProperty("X-Lamport-Clock", String.valueOf(lamportClock));

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = readResponse(conn);
                parseAndDisplayWeatherData(response);

                // Update Lamport clock based on server's response
                String serverLamportClock = conn.getHeaderField("X-Lamport-Clock");
                if (serverLamportClock != null) {
                    updateLamportClock(Integer.parseInt(serverLamportClock));
                }
            } else {
                throw new IOException("GET request failed. Response Code: " + responseCode);
            }
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Invalid server URL", e);
            throw new IOException("Invalid server URL", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    private static void parseAndDisplayWeatherData(String jsonResponse) {
        List<Map<String, String>> dataList = JSONParser.convertToMapList(jsonResponse);
        
        if (dataList.isEmpty()) {
            System.out.println("No weather data available for the specified station.");
            return;
        }

        System.out.println("Weather Data:");
        for (Map<String, String> data : dataList) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
            System.out.println(); // Add a blank line between different weather stations
        }

        LOGGER.info("Weather data displayed successfully.");
    }

    private static void incrementLamportClock() {
        lamportClock++;
        LOGGER.fine("Lamport Clock incremented to: " + lamportClock);
    }

    private static void updateLamportClock(int serverLamportClock) {
        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
        LOGGER.fine("Lamport Clock updated to: " + lamportClock);
    }
}