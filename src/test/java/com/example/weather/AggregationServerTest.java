package com.example.weather;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Test class for testing various functionalities of the AggregationServer.
 * Includes PUT and GET operations, error handling, and data expiration checks.
 */
@TestMethodOrder(OrderAnnotation.class)
public class AggregationServerTest {

    private static final int TEST_PORT = 4567;
    private static Thread serverThread;
    private static final String SERVER_URL = "http://localhost:" + TEST_PORT;

    /**
     * Starts the AggregationServer in a new thread before running any test.
     */
    @BeforeAll
    public static void startServer() {
        serverThread = new Thread(() -> AggregationServer.main(new String[]{String.valueOf(TEST_PORT)}));
        serverThread.start();
        try {
            Thread.sleep(2000); // Wait 2 seconds for the server to start
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the AggregationServer after all tests have completed.
     */
    @AfterAll
    public static void stopServer() {
        // Delete files created by AggregationServer
        try {
            Files.deleteIfExists(Paths.get("weather_data.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Interrupt the server thread to stop the server
        serverThread.interrupt();
    }

    /**
     * Test to ensure that the first PUT request to the server returns a 201 Created response.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(1)
    public void testInitialPutRequestReturns201() throws IOException {
        String stationId = "stationTest1";
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("id", stationId);
        weatherData.put("temperature", "25");

        HttpURLConnection connection = sendPutRequest(weatherData);
        int responseCode = connection.getResponseCode();
        assertEquals(201, responseCode, "First PUT request should return 201");
        connection.disconnect();
    }

    /**
     * Test to ensure that subsequent PUT requests return a 200 OK response.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(2)
    public void testSubsequentPutRequestReturns200() throws IOException {
        String stationId = "stationTest1";
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("id", stationId);
        weatherData.put("temperature", "26");

        // First PUT request
        HttpURLConnection connection1 = sendPutRequest(weatherData);
        connection1.disconnect();

        // Second PUT request
        HttpURLConnection connection2 = sendPutRequest(weatherData);
        int responseCode = connection2.getResponseCode();
        assertEquals(200, responseCode, "Subsequent PUT request should return 200");
        connection2.disconnect();
    }

    /**
     * Test to ensure that a GET request returns the correct weather data.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(3)
    public void testGetRequestReturnsData() throws IOException {
        String stationId = "stationTest1";
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("id", stationId);
        weatherData.put("temperature", "26");

        HttpURLConnection connection = sendGetRequest(stationId);
        String responseBody = readResponseBody(connection);
        Map<String, String> responseData = JSONParser.convertToMap(responseBody);

        assertEquals(weatherData.get("temperature"), responseData.get("temperature"), "Temperature should match");
        connection.disconnect();
    }

    /**
     * Test to ensure that data expires after 30 seconds, returning a 204 No Content response.
     * @throws IOException if the HTTP request fails.
     * @throws InterruptedException if the sleep is interrupted.
     */
    @Test
    @Order(4)
    public void testDataExpiresAfter30Seconds() throws IOException, InterruptedException {
        String stationId = "stationTest2";
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("id", stationId);
        weatherData.put("temperature", "28");

        sendPutRequest(weatherData).disconnect();
        Thread.sleep(31000); // Wait for 31 seconds

        HttpURLConnection connection = sendGetRequest(stationId);
        int responseCode = connection.getResponseCode();
        assertEquals(204, responseCode, "Data should expire and return 204 No Content");
        connection.disconnect();
    }

    /**
     * Test to ensure that invalid JSON in a PUT request returns a 500 Internal Server Error.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(5)
    public void testInvalidJsonReturns500_1() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();;
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        String invalidJson = "{ \"id\": \"stationTest3\", }"; // Invalid JSON - Trailing comma
        byte[] jsonBytes = invalidJson.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBytes);
        }

        int responseCode = connection.getResponseCode();
        assertEquals(500, responseCode, "Invalid JSON should return 500 Internal Server Error");
        connection.disconnect();
    }

    /**
     * Test to ensure that invalid JSON in a PUT request returns a 500 Internal Server Error.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(6)
    public void testInvalidJsonReturns500_2() throws IOException {
        // Send PUT request with invalid JSON
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();;
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        String invalidJson = "{ \"id\": stationTest4, }"; // Invalid JSON - Missing quotes
        byte[] jsonBytes = invalidJson.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBytes);
        }

        int responseCode = connection.getResponseCode();
        assertEquals(500, responseCode, "Invalid JSON should return 500 Internal Server Error");

        connection.disconnect();
    }
    
    /**
     * Test to ensure that an unsupported method returns a 400 Bad Request response.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(7)
    public void testUnsupportedMethodReturns400() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();
        connection.setRequestMethod("POST"); // Unsupported method
        int responseCode = connection.getResponseCode();
        assertEquals(400, responseCode, "Unsupported method should return 400 Bad Request");

        System.out.println("Unsupported method (400) test passed");
        connection.disconnect();
    }
    
    /**
     * Test to ensure that a PUT request with no content returns a 204 No Content response.
     * @throws IOException if the HTTP request fails.
     */
    @Test
    @Order(8)
    public void testNoContentReturns204() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Length", "0"); // No content
        int responseCode = connection.getResponseCode();
        assertEquals(204, responseCode, "No content should return 204 No Content");

        System.out.println("No content (204) test passed");
        connection.disconnect();
    }

    /**
     * Sends a PUT request to the server with the specified weather data.
     * @param data The weather data to send in the PUT request.
     * @return The HttpURLConnection object representing the PUT request.
     * @throws IOException if the HTTP request fails.
     */
    private HttpURLConnection sendPutRequest(Map<String, String> data) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        String jsonData = JSONParser.convertToJson(data);
        byte[] jsonBytes = jsonData.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

        try (var os = connection.getOutputStream()) {
            os.write(jsonBytes);
        }

        return connection;
    }

    /**
     * Sends a GET request to the server for the specified station ID.
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
     * @param connection The HttpURLConnection object representing the connection.
     * @return A string containing the response body.
     * @throws IOException if the HTTP request fails.
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