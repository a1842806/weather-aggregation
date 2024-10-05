package com.example.weather;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;

/**
 * Test class for testing the GETClient functionality.
 * This test class verifies that GETClient retrieves and displays data correctly,
 * handles missing data scenarios, and captures logger outputs for various cases.
 */
public class GETClientTest {

    // Configuration variables
    private static final int TEST_PORT = 4567;  // Port on which the test server will run
    private static Thread serverThread;  // Thread to run the AggregationServer
    private static final String SERVER_URL = "http://localhost:" + TEST_PORT;  // URL to access the server
    private static final Logger LOGGER = Logger.getLogger(GETClient.class.getName());  // Logger for capturing outputs

    /**
     * Starts the AggregationServer before all test methods.
     */
    @BeforeAll
    public static void startServer() {
        // Start the AggregationServer in a separate thread
        serverThread = new Thread(() -> AggregationServer.main(new String[]{String.valueOf(TEST_PORT)}));
        serverThread.start();

        // Wait for the server to initialize
        try {
            Thread.sleep(2000); // Wait for 2 seconds to ensure the server is ready
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the AggregationServer and cleans up resources after all test methods.
     */
    @AfterAll
    public static void stopServer() {
        // Delete the weather data file to clean up after tests
        try {
            Files.deleteIfExists(Paths.get("weather_data.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Interrupt the server thread to stop the AggregationServer
        serverThread.interrupt();
    }

    /**
     * Tests if GETClient can successfully retrieve data that was previously sent via a PUT request.
     * Sends a PUT request to store weather data, then runs GETClient to retrieve and verify the data.
     */
    @Test
    @Order(1)
    public void testGETClientRetrievesData() throws IOException {
        // Create sample weather data to be sent to the server
        String stationId = "stationTestGET";
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("id", stationId);
        weatherData.put("temperature", "22");

        // Send PUT request to AggregationServer with the sample weather data
        HttpURLConnection connection = sendPutRequest(weatherData);
        int responseCode = connection.getResponseCode();
        assertEquals(201 | 200, responseCode, "PUT request should return successful response code");
        connection.disconnect();

        // Wait for the server to process the PUT request
        try {
            Thread.sleep(2000); // Wait 2 seconds for the data to be processed by the server
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Capture System.out output to verify GETClient's console output
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        // Run GETClient to retrieve the weather data for the specified station ID
        GETClient.main(new String[]{SERVER_URL, stationId});

        // Restore the original System.out to prevent interference with other tests
        System.setOut(originalOut);

        // Check the captured output from GETClient for the expected data
        String output = outContent.toString(StandardCharsets.UTF_8.name());
        assertTrue(output.contains("Weather Data:"), "Output should contain 'Weather Data:'");
        assertTrue(output.contains("temperature: 22"), "Output should contain 'temperature: 22'");
    }

    /**
     * Tests if GETClient correctly handles a "No Content" scenario.
     * Runs GETClient for a non-existent station and verifies that the logger captures the appropriate message.
     */
    @Test
    @Order(2)
    public void testGETClientHandlesNoContentWithLogger() throws IOException {
        String stationId = "nonExistentStation";

        // Ensure no data exists for the station by deleting any existing weather data file
        deleteDataFile();

        // Set up a ByteArrayOutputStream to capture logger output
        ByteArrayOutputStream logStream = new ByteArrayOutputStream();
        Handler logHandler = new StreamHandler(logStream, new SimpleFormatter());

        // Add the custom handler to capture logger output for GETClient
        Logger logger = Logger.getLogger(GETClient.class.getName());
        logger.addHandler(logHandler);
        logger.setUseParentHandlers(false);  // Disable parent handlers to avoid duplication

        // Run GETClient with the server URL and non-existent station ID
        GETClient.main(new String[]{SERVER_URL, stationId});

        // Flush and remove the custom handler after running the main method
        logHandler.flush();
        logger.removeHandler(logHandler);

        // Convert the captured log output to a string
        String logOutput;
        try {
            logOutput = logStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported", e);
        }

        // Verify that the log output indicates no content is available
        assertTrue(logOutput.contains("No content available from the server.") || logOutput.contains("No weather data available"),
                "Log output should indicate no content available");
    }

    /**
     * Tests if GETClient correctly handles an invalid URL (bad request scenario).
     * Runs GETClient with an invalid URL and verifies that the logger captures the appropriate error message.
     */
    @Test
    @Order(3)
    public void testGETClientHandlesBadRequestWithLogger() {
        // Simulate a bad request by using an invalid URL
        String invalidServerUrl = "http://localhost:4567/invalidEndpoint";

        // Create a custom log handler to capture log messages
        ByteArrayOutputStream logStream = new ByteArrayOutputStream();
        Handler logHandler = new StreamHandler(logStream, new SimpleFormatter());

        // Add the custom handler to capture logger output for GETClient
        LOGGER.addHandler(logHandler);
        LOGGER.setUseParentHandlers(false);  // Disable parent handlers to avoid duplication

        // Run GETClient with the invalid URL
        GETClient.main(new String[]{invalidServerUrl});

        // Flush and remove the custom handler after running the main method
        logHandler.flush();
        LOGGER.removeHandler(logHandler);

        // Verify that the log output contains the expected error message
        String logOutput;
        try {
            logOutput = logStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported", e);
        }
        assertTrue(logOutput.contains("Failed to send GET request") || logOutput.contains("Bad request"),
                "Log output should indicate a bad request or a failure to send GET request");
    }

    /**
     * Helper method to send a PUT request to the AggregationServer with the provided data.
     *
     * @param data A map containing the weather data to be sent.
     * @return HttpURLConnection object for further assertions.
     */
    private HttpURLConnection sendPutRequest(Map<String, String> data) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER_URL + "/weather.json").toURL().openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        String jsonData = JSONParser.convertToJson(data);
        byte[] jsonBytes = jsonData.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBytes);
        }

        return connection;
    }

    /**
     * Helper method to delete the data file ("weather_data.json") to simulate a "No Content" scenario.
     */
    private void deleteDataFile() throws IOException {
        File dataFile = new File("weather_data.json");
        if (dataFile.exists()) {
            dataFile.delete();
        }
    }
}