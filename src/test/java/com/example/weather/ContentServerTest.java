package com.example.weather;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test class for testing the ContentServer functionality.
 * Tests include verifying that ContentServer can send data to the AggregationServer,
 * and ensuring that the AggregationServer processes and stores the data correctly.
 */
public class ContentServerTest {

    private static final String SERVER_URL = "http://localhost:4567";
    private static final String TEST_FILE_PATH = "test_weather_data.txt";
    private static final Map<String, String> weatherData = new LinkedHashMap<>();
    private static Thread serverThread;

    /**
     * Sets up the environment before any tests are run.
     * - Creates a test weather data file.
     * - Starts the AggregationServer in a new thread.
     *
     * @throws IOException if file creation or server start fails.
     */
    @BeforeAll
    public static void setup() throws IOException {
        // Prepare test weather data file
        weatherData.put("id", "stationTestCS");
        weatherData.put("temperature", "30");

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : weatherData.entrySet()) {
            lines.add(entry.getKey() + ": " + entry.getValue());
        }
        Files.write(Paths.get(TEST_FILE_PATH), lines, StandardCharsets.UTF_8);

        // Start the AggregationServer in a new thread
        serverThread = new Thread(() -> AggregationServer.main(new String[]{"4567"}));
        serverThread.start();

        // Wait for the server to start
        try {
            Thread.sleep(2000); // Wait 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cleans up the environment after all tests have been run.
     * - Deletes the test weather data file.
     * - Stops the AggregationServer.
     *
     * @throws IOException if file deletion fails.
     */
    @AfterAll
    public static void cleanup() throws IOException {
        // Delete test weather data file
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
        
        // Delete files created by AggregationServer
        try {
            Files.deleteIfExists(Paths.get("weather_data.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Stop the server
        serverThread.interrupt();
    }

    /**
     * Tests if the ContentServer sends data correctly to the AggregationServer.
     * - Starts the ContentServer in a separate thread.
     * - Sends weather data to the AggregationServer.
     * - Verifies that the AggregationServer receives and stores the data.
     *
     * @throws IOException if HTTP communication fails.
     * @throws InterruptedException if the thread is interrupted during sleep.
     */
    @Test
    public void testContentServerSendsData() throws IOException, InterruptedException {
        // Run the ContentServer in a separate thread
        Thread contentServerThread = new Thread(() -> {
            try {
                ContentServer.sendWeatherData(SERVER_URL, TEST_FILE_PATH, 1); // Send data once
            } catch (Exception e) {
                fail("ContentServer encountered an exception: " + e.getMessage());
            }
        });
        contentServerThread.start();

        // Wait for ContentServer to send data
        Thread.sleep(2000);

        // Verify data on AggregationServer
        HttpURLConnection connection = sendGetRequest(weatherData.get("id"));
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "GET request should return 200");

        String responseBody = readResponseBody(connection);
        Map<String, String> responseData = JSONParser.convertToMap(responseBody);
        assertEquals(weatherData.get("temperature"), responseData.get("temperature"), "Temperature should match");

        connection.disconnect();

        // Stop the ContentServer thread
        contentServerThread.interrupt();
    }

    /**
     * Sends a GET request to the AggregationServer for the specified station ID.
     *
     * @param stationId The station ID for which to retrieve weather data.
     * @return The HttpURLConnection object representing the GET request.
     * @throws IOException if the HTTP request fails.
     */
    private HttpURLConnection sendGetRequest(String stationId) throws IOException {
        String urlStr = SERVER_URL + "/weather.json";
        if (stationId != null) {
            urlStr += "?station=" + stationId;
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        connection.setRequestMethod("GET");
        return connection;
    }

    /**
     * Reads the response body from the provided HTTP connection.
     *
     * @param connection The HttpURLConnection object representing the connection.
     * @return A string containing the response body.
     * @throws IOException if an I/O error occurs while reading the response.
     */
    private String readResponseBody(HttpURLConnection connection) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line).append("\n");
            }
        }
        return responseBody.toString();
    }
}