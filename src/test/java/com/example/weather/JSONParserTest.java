package com.example.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JSONParser class.
 */
public class JSONParserTest {

    private Map<String, String> sampleMap;
    private List<Map<String, String>> sampleMapList;

    @BeforeEach
    public void setUp() {
        // Initialize a sample map for testing
        sampleMap = new LinkedHashMap<>();
        sampleMap.put("id", "station1");
        sampleMap.put("temperature", "25");
        sampleMap.put("humidity", "60");

        // Initialize a sample list of maps for testing
        sampleMapList = new ArrayList<>();
        sampleMapList.add(sampleMap);

        Map<String, String> secondMap = new LinkedHashMap<>();
        secondMap.put("id", "station2");
        secondMap.put("temperature", "28");
        secondMap.put("humidity", "70");
        sampleMapList.add(secondMap);
    }

    /**
     * Test converting a single Map to a JSON string.
     */
    @Test
    public void testConvertToJsonFromMap() {
        String expectedJson = "{\n" +
                "    \"id\": \"station1\",\n" +
                "    \"temperature\": 25,\n" +
                "    \"humidity\": 60\n" +
                "}";

        String jsonResult = JSONParser.convertToJson(sampleMap);
        assertEquals(expectedJson, jsonResult, "JSON string should match the expected format.");
    }

    /**
     * Test converting a list of Maps to a JSON array string.
     */
    @Test
    public void testConvertToJsonFromList() {
        String expectedJsonArray = "[\n" +
                "{\n" +
                "    \"id\": \"station1\",\n" +
                "    \"temperature\": 25,\n" +
                "    \"humidity\": 60\n" +
                "},\n" +
                "{\n" +
                "    \"id\": \"station2\",\n" +
                "    \"temperature\": 28,\n" +
                "    \"humidity\": 70\n" +
                "}\n" +
                "]";

        String jsonArrayResult = JSONParser.convertToJson(sampleMapList);
        assertEquals(expectedJsonArray, jsonArrayResult, "JSON array string should match the expected format.");
    }

    /**
     * Test converting a valid JSON string to a Map.
     */
    @Test
    public void testConvertToMap() {
        String json = "{ \"id\": \"station1\", \"temperature\": 25, \"humidity\": 60 }";
        Map<String, String> result = JSONParser.convertToMap(json);

        assertEquals("station1", result.get("id"));
        assertEquals("25", result.get("temperature"));
        assertEquals("60", result.get("humidity"));
    }

    /**
     * Test converting an invalid JSON string to a Map (expecting an exception).
     */
    @Test
    public void testConvertToMapInvalidJson() {
        String invalidJson = "{ \"id\": \"station1\", \"temperature\": 25, \"humidity\": }";
        assertThrows(IllegalArgumentException.class, () -> JSONParser.convertToMap(invalidJson),
                "Invalid JSON should throw an IllegalArgumentException.");
    }

    /**
     * Test converting an empty JSON object string to a Map (expecting an exception).
     */
    @Test
    public void testConvertToMapEmptyJson() {
        String emptyJson = "{}";
        assertThrows(IllegalArgumentException.class, () -> JSONParser.convertToMap(emptyJson),
                "Empty JSON object should throw an IllegalArgumentException.");
    }

    /**
     * Test converting a JSON array string to a list of Maps.
     */
    @Test
    public void testConvertToMapList() {
        String jsonArray = "[{\"id\": \"station1\", \"temperature\": 25}, {\"id\": \"station2\", \"temperature\": 28}]";
        List<Map<String, String>> resultList = JSONParser.convertToMapList(jsonArray);

        assertEquals(2, resultList.size(), "The JSON array should contain 2 objects.");
        assertEquals("station1", resultList.get(0).get("id"));
        assertEquals("25", resultList.get(0).get("temperature"));
        assertEquals("station2", resultList.get(1).get("id"));
        assertEquals("28", resultList.get(1).get("temperature"));
    }

    /**
     * Test converting an invalid JSON array string to a list of Maps (expecting an exception).
     */
    @Test
    public void testConvertToMapListInvalidJson() {
        String invalidJsonArray = "[{\"id\": \"station1\", \"temperature\": 25}, {\"id\": \"station2\", }";
        assertThrows(IllegalArgumentException.class, () -> JSONParser.convertToMapList(invalidJsonArray),
                "Invalid JSON array should throw an IllegalArgumentException.");
    }

    /**
     * Test converting a Map to JSON with numeric values.
     */
    @Test
    public void testConvertToJsonWithNumericValues() {
        Map<String, String> numericMap = new LinkedHashMap<>();
        numericMap.put("id", "station1");
        numericMap.put("temperature", "25");
        numericMap.put("pressure", "1015");

        String expectedJson = "{\n" +
                "    \"id\": \"station1\",\n" +
                "    \"temperature\": 25,\n" +
                "    \"pressure\": 1015\n" +
                "}";

        String jsonResult = JSONParser.convertToJson(numericMap);
        assertEquals(expectedJson, jsonResult, "JSON string should handle numeric values correctly.");
    }

    /**
     * Test handling of special characters and escape sequences in JSON strings.
     */
    @Test
    public void testEscapeString() {
        String input = "This \"string\" needs \\ escaping!";
        String expectedEscapedString = "This \\\"string\\\" needs \\\\ escaping!";
        String result = JSONParser.escapeString(input);
        assertEquals(expectedEscapedString, result, "The escape sequences should be correctly applied.");
    }

    /**
     * Test handling of unescaping special characters in JSON strings.
     */
    @Test
    public void testUnescapeString() {
        String input = "This \\\"string\\\" needs \\\\ escaping!";
        String expectedUnescapedString = "This \"string\" needs \\ escaping!";
        String result = JSONParser.unescapeString(input);
        assertEquals(expectedUnescapedString, result, "The escape sequences should be correctly removed.");
    }
}