package com.example.weather;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JSONParser {
    // Convert a single Map to JSON string
    public static String convertToJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        int entryCount = 0;
        int size = data.size();
        
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            json.append("    \"").append(key).append("\": ");
            
            if (isNumeric(value)) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }

            entryCount++;
            if (entryCount < size) {
                json.append(",");
            }
            json.append("\n");
        }
    
        json.append("}");
        return json.toString();
    }

    // Convert a List of Maps to JSON string
    public static String convertToJson(List<Map<String, String>> dataList) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < dataList.size(); i++) {
            json.append(convertToJson(dataList.get(i)));
            if (i < dataList.size() - 1) {
                json.append(",\n");
            }
        }
        
        json.append("\n]");
        return json.toString();
    }   
    
    // Convert the JSON string back to a Map
    public static Map<String, String> convertToMap(String json) {
        // Remove curly braces and split
        json = json.substring(1, json.length() - 1);
        String[] keyValuePairs = json.split(",(?=\\s*\"\\w+\"\\s*:)");

        // Create a new map to store the key-value pairs
        Map<String, String> data = new HashMap<>();

        for (String pair : keyValuePairs) {
            // Split each key-value pair by the first colon
            String[] keyValue = pair.split(":", 2);

            // Clean up the key and value strings by removing quotes and whitespace
            String key = keyValue[0].trim().replace("\"", "");
            String value = keyValue[1].trim();

            // If value is a string, remove surrounding quotes
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            // Add the key-value pair to the map
            data.put(key, value);
        }

        return data;
    }

    // Convert the JSON string to a List of Maps
    public static List<Map<String, String>> convertToMapList(String json) {
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Invalid JSON array format");
        }
        
        json = json.substring(1, json.length() - 1);
        String[] objects = json.split("\\},\\s*\\{");
        
        List<Map<String, String>> result = new ArrayList<>();
        for (String obj : objects) {
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            result.add(convertToMap(obj));
        }
        
        return result;
    }

    // Check if a string is numeric
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
