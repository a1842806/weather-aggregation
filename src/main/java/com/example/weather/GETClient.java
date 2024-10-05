package com.example.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A simple GET client that sends a request to an aggregation server and displays the weather data.
 */
public class GETClient {
    // Logger for logging messages and errors
    private static final Logger LOGGER = Logger.getLogger(GETClient.class.getName());
    // Lamport clock for ordering events
    private static int lamportClock = 0;
    // Maximum number of retries for sending the GET request
    private static final int MAX_RETRIES = 3;
    // Delay between retries in milliseconds
    private static final int RETRY_DELAY_MS = 5000;

    public static void main(String[] args) {
        // Check if the server URL is provided
        if (args.length < 1) {
            LOGGER.severe("Usage: java GETClient <server_url> [station_id]");
            return;
        }

        // Extract the server URL and optional station ID from the command-line arguments
        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        // Ensure the URL has the correct format
        serverUrl = ensureCorrectUrlFormat(serverUrl);

        // Normalize server URL to avoid double slashes
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }

        // Build the GET request URL
        String requestUrl = buildRequestUrl(serverUrl, stationId);
        LOGGER.info("Requesting weather data from: " + requestUrl);

        // Send GET request to the aggregation server with retry mechanism
        try {
            sendGETRequestWithRetry(requestUrl);
        } catch (Exception e) { // EDIT: Catch all exceptions
            LOGGER.log(Level.SEVERE, "An unexpected error occurred", e);
        }
    }

    /**
     * Ensures the URL has the correct format by adding "http://" if missing.
     *
     * @param url The input URL.
     * @return The corrected URL.
     */
    private static String ensureCorrectUrlFormat(String url) {
        if (!url.matches("^(http://|https://).*")) {
            url = "http://" + url;
        }
        return url;
    }

    /**
     * Builds the request URL with the optional station ID.
     *
     * @param serverUrl The server URL.
     * @param stationId The station ID.
     * @return The complete request URL.
     */
    private static String buildRequestUrl(String serverUrl, String stationId) {
        if (stationId != null) {
            try {
                // Encode the station ID to handle special characters
                String encodedStationId = URLEncoder.encode(stationId, StandardCharsets.UTF_8.name());
                return serverUrl + "/weather.json?station=" + encodedStationId;
            } catch (UnsupportedEncodingException e) {
                LOGGER.warning("Unsupported encoding for station ID. Using unencoded value.");
                return serverUrl + "/weather.json?station=" + stationId;
            }
        } else {
            return serverUrl + "/weather.json";
        }
    }

    /**
     * Sends the GET request with retries in case of failure.
     *
     * @param requestUrl The request URL.
     * @throws IOException If an I/O error occurs after retries.
     */
    private static void sendGETRequestWithRetry(String requestUrl) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                sendGETRequest(requestUrl);
                return; // If successful, exit the method
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Attempt " + (attempt + 1) + " failed. Retrying in " + RETRY_DELAY_MS + "ms", e);
                incrementLamportClock(); 
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.severe("Interrupted during retry wait");
                    throw new IOException("Interrupted during retry wait", ie);
                }
            }
        }
        throw new IOException("Failed to send GET request after " + MAX_RETRIES + " attempts");
    }

    /**
     * Sends the GET request to the server and processes the response.
     *
     * @param requestUrl The request URL.
     * @throws IOException If an I/O error occurs.
     */
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

            // Check the response code and read the response
            int responseCode = conn.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                    String response = readResponse(conn);
                    parseAndDisplayWeatherData(response);
                    break;
                case HttpURLConnection.HTTP_NO_CONTENT:
                    LOGGER.info("No content available from the server.");
                    return;
                case HttpURLConnection.HTTP_BAD_REQUEST:
                    throw new IOException("Bad request. Response Code: " + responseCode);
                case HttpURLConnection.HTTP_INTERNAL_ERROR:
                    throw new IOException("Server encountered an internal error. Response Code: " + responseCode);
                default:
                    throw new IOException("GET request failed. Response Code: " + responseCode);
            }

            // Update Lamport clock based on server's response
            String serverLamportClock = conn.getHeaderField("X-Lamport-Clock");
            if (serverLamportClock != null) {
                try {
                    updateLamportClock(Integer.parseInt(serverLamportClock));
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid Lamport clock value from server: " + serverLamportClock);
                }
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

    /**
     * Reads the response from the server.
     *
     * @param conn The HttpURLConnection object.
     * @return The response as a string.
     * @throws IOException If an I/O error occurs.
     */
    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { 
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine).append("\n"); 
            }
            return response.toString();
        }
    }

    /**
     * Parses the JSON response and displays the weather data.
     *
     * @param jsonResponse The JSON response string.
     */
    private static void parseAndDisplayWeatherData(String jsonResponse) {
        List<Map<String, String>> dataList;

        // Parse the JSON response
        try {
            dataList = JSONParser.convertToMapList(jsonResponse);
        } catch (IllegalArgumentException e) {
            // Try parsing as a single object
            try {
                Map<String, String> data = JSONParser.convertToMap(jsonResponse);
                dataList = new ArrayList<>();
                dataList.add(data);
            } catch (IllegalArgumentException ex) {
                LOGGER.severe("Failed to parse JSON response.");
                return;
            }
        }
        
        // Check if there is any weather data available
        if (dataList.isEmpty()) {
            System.out.println("No weather data available for the specified station.");
            return;
        }

        // Display the weather data
        System.out.println("Weather Data:");
        for (Map<String, String> data : dataList) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (!"lamportClock".equals(entry.getKey())) { 
                    System.out.println("  " + entry.getKey() + ": " + entry.getValue());
                }
            }
            System.out.println();
        }
        // Log successful display of weather data
        LOGGER.info("Weather data displayed successfully.");
    }

    /**
     * Increments the Lamport clock for an internal event.
     */
    private static void incrementLamportClock() {
        lamportClock++;
        LOGGER.fine("Lamport Clock incremented to: " + lamportClock);
    }

    /**
     * Updates the Lamport clock based on the server's Lamport clock.
     *
     * @param serverLamportClock The Lamport clock received from the server.
     */
    private static void updateLamportClock(int serverLamportClock) {
        lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
        LOGGER.fine("Lamport Clock updated to: " + lamportClock);
    }
}