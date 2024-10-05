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

/**
 * IntegrationTest class verifies the end-to-end functionality of the system,
 * including interactions between AggregationServer, ContentServer, and GETClient.
 * Tests multiple scenarios including data updates, expiration, and Lamport clock synchronization.
 */
public class IntegrationTest {

    // Configuration constants
    private static final String SERVER_URL = "http://localhost:4567";
    private static Thread aggregationServerThread;  // Thread to run the AggregationServer
    private static List<Thread> contentServerThreads = new ArrayList<>();  // List of threads for ContentServer instances
    private static final String TEST_DATA_FILE_PREFIX = "test_weather_data_";  // Prefix for test data file names
    private static final String GET_CLIENT_OUTPUT_FILE = "get_client_output.txt";  // Output file for GETClient results

    /**
     * Starts the AggregationServer and initializes resources before all test methods.
     * 
     * @throws IOException If there is an error starting the server or creating necessary files.
     */
    @BeforeAll
    public static void startServers() throws IOException {
        // Start the AggregationServer in a separate thread
        aggregationServerThread = new Thread(() -> AggregationServer.main(new String[]{"4567"}));
        aggregationServerThread.start();

        // Wait for the server to initialize
        try {
            Thread.sleep(2000); // Wait for 2 seconds to ensure server is up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the AggregationServer and ContentServers, and cleans up test data files after all tests are executed.
     * 
     * @throws IOException If there is an error deleting test files.
     */
    @AfterAll
    public static void stopServers() throws IOException {
        // Stop the AggregationServer
        aggregationServerThread.interrupt();

        // Stop all ContentServer threads
        for (Thread thread : contentServerThreads) {
            thread.interrupt();
        }

        // Clean up test data files
        for (int i = 1; i <= contentServerThreads.size(); i++) {
            Files.deleteIfExists(Paths.get(TEST_DATA_FILE_PREFIX + i + ".txt"));
        }

        // Delete the AggregationServer's data file and GETClient output file
        Files.deleteIfExists(Paths.get("weather_data.json"));
        Files.deleteIfExists(Paths.get(GET_CLIENT_OUTPUT_FILE));
    }

    /**
     * Integration scenario to verify the interaction between multiple ContentServers and GETClient.
     * Tests data creation, retrieval, and expiration handling.
     */
    @Test
    public void testIntegrationScenario() throws IOException, InterruptedException {
        // Step 1: Create two ContentServers with different data
        Map<String, String> weatherData1 = new LinkedHashMap<>();
        weatherData1.put("id", "station1");
        weatherData1.put("temperature", "20");

        Map<String, String> weatherData2 = new LinkedHashMap<>();
        weatherData2.put("id", "station2");
        weatherData2.put("temperature", "25");

        // Create test data files for ContentServers
        createTestDataFile(1, weatherData1);
        createTestDataFile(2, weatherData2);

        // Start ContentServer instances
        startContentServer(1, 1);  // ContentServer for station1
        startContentServer(2, 1);  // ContentServer for station2

        // Wait for ContentServers to send data
        Thread.sleep(5000);

        // Step 2: Run GETClient to retrieve data for station1
        String outputStation1 = runGETClient("station1");
        assertTrue(outputStation1.contains("temperature: 20"), "GETClient should retrieve temperature 20 for station1");

        // Step 3: Run GETClient to retrieve data for station2
        String outputStation2 = runGETClient("station2");
        assertTrue(outputStation2.contains("temperature: 25"), "GETClient should retrieve temperature 25 for station2");

        // Step 4: Wait for data to expire (31 seconds)
        Thread.sleep(31000);

        // Step 5: Run GETClient again to ensure data has expired
        String outputAfterExpiry = runGETClient("station1");
        assertTrue(outputAfterExpiry.isEmpty(), "Data should have expired for station1");
    }

    /**
     * Tests if data can be modified and the changes are correctly reflected in subsequent GETClient requests.
     * 
     * @throws IOException If there is an error modifying the test data file.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Test
    public void testModifiedData() throws IOException, InterruptedException {
        // Step 1: Create a test data file with initial air_temp value of 13.3
        Map<String, String> weatherData = new LinkedHashMap<>();
        weatherData.put("id", "stationTest");
        weatherData.put("air_temp", "13.3");
        String dataFilePath = "content/integration_test.txt";
        createTestDataFile(dataFilePath, weatherData);

        // Step 2: Start ContentServer using the test data file
        startContentServerWithCustomFile(dataFilePath, 1);  // Single update

        // Wait for the data to be sent
        Thread.sleep(5000);

        // Step 3: Use GETClient to verify the initial value
        String output = runGETClient("stationTest");
        assertTrue(output.contains("air_temp: 13.3"), "Initial air_temp should be 13.3");

        // Step 4: Modify the data file to change air_temp to 15
        weatherData.put("air_temp", "15");
        createTestDataFile(dataFilePath, weatherData);

        // Wait for the ContentServer to update the data
        startContentServerWithCustomFile(dataFilePath, 1);
        Thread.sleep(5000);  // Wait for the update interval

        // Step 5: Verify that GETClient retrieves the updated air_temp value
        String updatedOutput = runGETClient("stationTest");
        assertTrue(updatedOutput.contains("air_temp: 15"), "Updated air_temp should be 15");

        // Step 6: Delete the test data file
        Files.deleteIfExists(Paths.get(dataFilePath));
    }

    /**
     * Tests Lamport clock synchronization between multiple ContentServers and GETClient.
     * Verifies that updates are handled in the correct order and GETClient receives the expected Lamport clock value.
     */
    @Test
    public void testLamportClock() throws IOException, InterruptedException {
        // Step 1: Create initial data and start first ContentServer
        Map<String, String> weatherData1 = new LinkedHashMap<>();
        weatherData1.put("id", "stationLamport");
        weatherData1.put("temperature", "20");

        createTestDataFile(1, weatherData1);
        startContentServer(1, -1);  // Continuous sending for the first server

        // Wait for initial data to be sent
        Thread.sleep(5000);

        // Step 2: Capture Lamport clock after the first update
        int lamportClock1 = getLamportClockFromAggregationServer("stationLamport");

        // Step 3: Start second ContentServer after a delay
        Thread.sleep(5000);

        Map<String, String> weatherData2 = new LinkedHashMap<>();
        weatherData2.put("id", "stationLamport");
        weatherData2.put("temperature", "25");

        createTestDataFile(2, weatherData2);
        startContentServer(2, -1);  // Continuous sending for the second server

        // Wait for the second update
        Thread.sleep(5000);

        // Step 4: Capture the updated Lamport clock after second update
        int lamportClock2 = getLamportClockFromAggregationServer("stationLamport");
        assertTrue(lamportClock2 > lamportClock1, "Lamport clock should increase after second update");

        // Step 5: Use GETClient to verify Lamport clock synchronization
        int lamportClockClient = runGETClientAndGetLamportClock("stationLamport");
        assertEquals(lamportClock2 + 2, lamportClockClient, "GETClient should receive the latest Lamport clock value");
    }

    // Helper methods

    /**
     * Creates a test data file for a ContentServer using a unique index.
     * The file is named using the prefix followed by the index (e.g., test_weather_data_1.txt).
     * This method uses the `createTestDataFile` helper to write the weather data to the file.
     *
     * @param index         The unique index number for the test data file.
     * @param weatherData   A map containing weather data fields and their respective values.
     */
    private void createTestDataFile(int index, Map<String, String> weatherData) throws IOException {
        String fileName = TEST_DATA_FILE_PREFIX + index + ".txt";  // Construct the file name
        createTestDataFile(fileName, weatherData);  // Call helper to create the file
    }

    /**
     * Creates a test data file with the specified file name and weather data.
     * Writes each key-value pair from the `weatherData` map to the file, formatted as `key: value`.
     *
     * @param fileName      The name of the file to be created.
     * @param weatherData   A map containing weather data fields and their respective values.
     */
    private void createTestDataFile(String fileName, Map<String, String> weatherData) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : weatherData.entrySet()) {
            lines.add(entry.getKey() + ": " + entry.getValue());  // Format each entry as `key: value`
        }
        Files.write(Paths.get(fileName), lines, StandardCharsets.UTF_8);  // Write lines to file
    }

    /**
     * Starts a ContentServer instance using the test data file corresponding to the given index.
     * The ContentServer will continuously send the weather data to the AggregationServer.
     *
     * @param index         The index corresponding to the test data file (e.g., test_weather_data_1.txt).
     * @param iterations    The number of iterations to send the data. Use -1 for continuous sending.
     */
    private void startContentServer(int index, int iterations) {
        String fileName = TEST_DATA_FILE_PREFIX + index + ".txt";  // Construct the file name
        startContentServerWithCustomFile(fileName, iterations);    // Start the ContentServer with the given file
    }

    /**
     * Starts a ContentServer in a new thread using the specified data file and iterations.
     * The ContentServer will periodically send the data from the file to the AggregationServer.
     *
     * @param fileName      The path to the data file used by the ContentServer.
     * @param iterations    The number of iterations to send the data. Use -1 for continuous sending.
     */
    private void startContentServerWithCustomFile(String fileName, int iterations) {
        Thread contentServerThread = new Thread(() -> {
            try {
                ContentServer.sendWeatherData(SERVER_URL, fileName, iterations);  // Send data using ContentServer
            } catch (Exception e) {
                // Handle exceptions if the ContentServer encounters an issue
            }
        });
        contentServerThread.start();  // Start the ContentServer thread
        contentServerThreads.add(contentServerThread);  // Add to the list of active ContentServer threads
    }

    /**
     * Runs the GETClient with the specified station ID and captures the output from System.out.
     *
     * @param stationId     The ID of the station to retrieve data for.
     * @return              The captured output from the GETClient as a string.
     */
    private String runGETClient(String stationId) throws IOException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();  // Create output stream for capturing output
        PrintStream originalOut = System.out;  // Store the original System.out
        System.setOut(new PrintStream(outContent));  // Redirect System.out to capture output

        GETClient.main(new String[]{SERVER_URL, stationId});  // Run GETClient with the server URL and station ID

        System.setOut(originalOut);  // Restore the original System.out
        return outContent.toString(StandardCharsets.UTF_8.name());  // Convert captured output to a string
    }

    /**
     * Retrieves the Lamport clock value for a specific station from the AggregationServer.
     *
     * @param stationId     The ID of the station to retrieve the Lamport clock value for.
     * @return              The Lamport clock value as an integer.
     */
    private int getLamportClockFromAggregationServer(String stationId) throws IOException {
        String urlStr = SERVER_URL + "/weather.json?station=" + stationId;  // Construct the request URL
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        connection.setRequestMethod("GET");  // Set the request method to GET

        connection.setRequestProperty("X-Lamport-Clock", "0");  // Send initial Lamport clock value in request

        int responseCode = connection.getResponseCode();  // Get the response code
        assertEquals(200, responseCode, "Expected 200 OK from AggregationServer");  // Verify response code

        String lamportClockStr = connection.getHeaderField("X-Lamport-Clock");  // Retrieve Lamport clock from header
        assertNotNull(lamportClockStr, "Lamport clock header should not be null");  // Ensure the header is not null

        int lamportClock;
        try {
            lamportClock = Integer.parseInt(lamportClockStr);  // Parse Lamport clock value
        } catch (NumberFormatException e) {
            fail("Invalid Lamport clock value received from AggregationServer");  // Handle parsing error
            return -1;  // Return -1 in case of error (unreachable due to fail)
        }

        connection.disconnect();  // Disconnect the connection
        return lamportClock;  // Return the parsed Lamport clock value
    }

    /**
     * Runs the GETClient and retrieves the Lamport clock value from the response for a specific station.
     *
     * @param stationId     The ID of the station to retrieve data for.
     * @return              The Lamport clock value as an integer.
     */
    private int runGETClientAndGetLamportClock(String stationId) throws IOException {
        String urlStr = SERVER_URL + "/weather.json?station=" + stationId;  // Construct the request URL
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        connection.setRequestMethod("GET");  // Set the request method to GET

        connection.setRequestProperty("X-Lamport-Clock", "0");  // Send initial Lamport clock value in request

        int responseCode = connection.getResponseCode();  // Get the response code
        assertEquals(200, responseCode, "Expected 200 OK from AggregationServer");  // Verify response code

        String lamportClockStr = connection.getHeaderField("X-Lamport-Clock");  // Retrieve Lamport clock from header
        assertNotNull(lamportClockStr, "Lamport clock header should not be null");  // Ensure the header is not null

        int lamportClock;
        try {
            lamportClock = Integer.parseInt(lamportClockStr);  // Parse Lamport clock value
        } catch (NumberFormatException e) {
            fail("Invalid Lamport clock value received from AggregationServer");  // Handle parsing error
            return -1;  // Return -1 in case of error (unreachable due to fail)
        }

        // Optional: Read and discard the response body
        InputStreamReader isr = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder responseBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            responseBody.append(line).append("\n");
        }

        connection.disconnect();  // Disconnect the connection
        return lamportClock;  // Return the parsed Lamport clock value
    }
}