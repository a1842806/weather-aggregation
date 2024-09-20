package com.example.weather;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;

// import java.io.BufferedReader;
// import java.io.InputStreamReader;
// import java.io.StringReader;
// import java.net.HttpURLConnection;
// import java.net.URL;
// import java.net.URI;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;

public class GETClientTest {

    // @Mock
    // private HttpURLConnection mockConnection;

    // @InjectMocks
    // private GETClient getClient;

    // @BeforeEach
    // public void setUp() throws Exception {
    //     // Initialize Mockito annotations
    //     MockitoAnnotations.openMocks(this);
    // }

    // // Test the Lamport clock increments when sending a request
    // @Test
    // public void testLamportClockIncrement() throws Exception {
    //     // Mock the URL and HttpURLConnection behavior
    //     URI uri = new URI("http", null, "example.com", -1, "/weather", null, null);
    //     URL url = uri.toURL();
    //     when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
    //     when(mockConnection.getInputStream()).thenReturn(new BufferedReader(new StringReader("{}")).getInputStream());

    //     // Spy on the class to intercept method calls
    //     GETClient spyClient = spy(GETClient.class);
    //     doNothing().when(spyClient).parseAndDisplayWeatherData(anyString());

    //     // Send the request and validate Lamport clock increment
    //     spyClient.sendGETRequest(url.toString());

    //     // Verify that the Lamport clock has been incremented (it starts at 0)
    //     verify(spyClient, times(1)).incrementLamportClock();
    // }

    // // Test handling of a valid HTTP response with weather data
    // @Test
    // public void testParseAndDisplayWeatherData() throws Exception {
    //     // Mock the input response stream (simulating JSON response)
    //     String jsonResponse = "{\"temperature\":\"25.4\",\"humidity\":\"80\"}";
    //     BufferedReader mockReader = new BufferedReader(new StringReader(jsonResponse));

    //     when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
    //     when(mockConnection.getInputStream()).thenReturn(mockReader);

    //     // Spy on the GETClient class to verify behavior
    //     GETClient spyClient = spy(GETClient.class);
    //     doNothing().when(spyClient).parseAndDisplayWeatherData(anyString());

    //     // Send the GET request and verify response parsing
    //     spyClient.sendGETRequest("http://example.com/weather");

    //     // Verify that the parsing method is called with the correct JSON response
    //     verify(spyClient, times(1)).parseAndDisplayWeatherData(jsonResponse);
    // }

    // // Test handling of a failed HTTP GET request (e.g., 404 Not Found)
    // @Test
    // public void testFailedGETRequest() throws Exception {
    //     when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

    //     GETClient spyClient = spy(GETClient.class);

    //     // Attempt to send the request and check that the error message is logged
    //     spyClient.sendGETRequest("http://example.com/weather");

    //     // Verify that no response parsing occurs since the request failed
    //     verify(spyClient, never()).parseAndDisplayWeatherData(anyString());
    // }

    // // Test that the request URL is correctly built
    // @Test
    // public void testRequestUrlFormatting() {
    //     String serverUrl = "example.com";
    //     String stationId = "123";

    //     // Check that the URL is correctly formatted with stationId
    //     String expectedUrl = "http://example.com/weather?station=123";
    //     String result = GETClient.buildRequestUrl(serverUrl, stationId);
    //     assertEquals(expectedUrl, result);
    // }
}
