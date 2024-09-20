package com.example.weather;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.Map;

public class GETClient {
    // Lamport Clock
    private static int lamportClock = 0;

    public static void main(String[] args) {
        // Read command-line arguments for server and optional station ID
        if (args.length < 1) {
            System.err.println("Usage: java GETClient <server_url> [station_id]");
            return;
        }

        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        // Parse and format the URL correctly (add HTTP protocol if missing)
        if (!serverUrl.startsWith("http://")) {
            serverUrl = "http://" + serverUrl;
        }

        // Build the GET request URL
        String requestUrl = stationId != null ? serverUrl + "/weather?station=" + stationId : serverUrl + "/weather";
        System.out.println("Requesting weather data from: " + requestUrl);

        // Send GET request to the aggregation server
        sendGETRequest(requestUrl);
    }

    public static void sendGETRequest(String requestUrl) {
        try {
            URI uri = new URI(requestUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Increment and send Lamport clock
            incrementLamportClock();
            conn.setRequestProperty("X-Lamport-Clock", String.valueOf(lamportClock));

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                parseAndDisplayWeatherData(response.toString());
            } else {
                System.err.println("GET request failed. Response Code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("Error occurred while sending GET request: " + e.getMessage());
        }
    }

    public static void parseAndDisplayWeatherData(String jsonResponse) {
        // Parse the JSON response and display the weather data
        Map<String, String> data = JSONParser.convertToMap(jsonResponse);

        System.out.println("Weather Data:");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("Weather data displayed successfully.");
    }

    public static void incrementLamportClock() {
        lamportClock++;
        System.out.println("Lamport Clock: " + lamportClock);
    }
}
