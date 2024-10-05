package com.example.weather;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap; // Use LinkedHashMap to preserve order
import java.util.List;

public class JSONParser {
    /**
     * Converts a single Map to a JSON string.
     *
     * @param data The Map containing key-value pairs.
     * @return A JSON-formatted string.
     */
    public static String convertToJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        int entryCount = 0;
        int size = data.size();

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            json.append("    \"").append(escapeString(key)).append("\": ");

            if (isNumeric(value)) {
                json.append(value);
            } else {
                json.append("\"").append(escapeString(value)).append("\"");
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

    /**
     * Converts a List of Maps to a JSON array string.
     *
     * @param dataList The List containing Maps of key-value pairs.
     * @return A JSON-formatted array string.
     */
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

    /**
     * Converts a JSON string into a Map.
     *
     * @param json The JSON string to convert.
     * @return A Map containing the key-value pairs.
     * @throws IllegalArgumentException If the JSON is invalid.
     */
    public static Map<String, String> convertToMap(String json) {
        json = json.trim();
        if (json.equals("{}")) {
            throw new IllegalArgumentException("Empty JSON object");
        }
        // Remove curly braces and validate format
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        } else {
            throw new IllegalArgumentException("Invalid JSON object format");
        }

        // Check for empty JSON object
        if (json.isEmpty()) {
            throw new IllegalArgumentException("Empty JSON object");
        }

        // Check for trailing comma
        if (json.endsWith(",")) {
            throw new IllegalArgumentException("Invalid JSON object: trailing comma");
        }

        String[] keyValuePairs = splitKeyValuePairs(json);

        Map<String, String> data = new LinkedHashMap<>();
        for (String pair : keyValuePairs) {
            // Split each key-value pair by the first colon
            String[] keyValue = pair.split(":", 2);

            if (keyValue.length != 2) {
                throw new IllegalArgumentException("Invalid key-value pair in JSON: " + pair);
            }

            // Clean up the key and value strings
            String key = unescapeString(stripQuotes(keyValue[0].trim()));
            String valuePart = keyValue[1].trim();

            String value;
            if (valuePart.startsWith("\"")) {
                // Value is a string
                if (!valuePart.endsWith("\"")) {
                    throw new IllegalArgumentException("Unterminated string value in JSON: " + valuePart);
                }
                value = unescapeString(stripQuotes(valuePart));
            } else {
                // Value is numeric or boolean
                value = valuePart.split(",")[0].trim();
                if (!isNumeric(value)) {
                    throw new IllegalArgumentException("Invalid numeric value in JSON: " + valuePart);
                }
            }

            data.put(key, value);
        }

        return data;
    }

    /**
     * Splits the JSON string into key-value pairs, considering possible commas inside strings.
     *
     * @param json The JSON string without outer braces.
     * @return An array of key-value pair strings.
     * @throws IllegalArgumentException If the JSON is invalid.
     */
    private static String[] splitKeyValuePairs(String json) {
        List<String> pairs = new ArrayList<>();
        boolean insideString = false;
        StringBuilder currentPair = new StringBuilder();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '\"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                insideString = !insideString;
            }

            if (!insideString && c == ',') {
                // Check if currentPair is empty (which indicates a trailing comma)
                if (currentPair.toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid JSON object: Empty key-value pair detected");
                }
                pairs.add(currentPair.toString().trim());
                currentPair.setLength(0);
            } else {
                currentPair.append(c);
            }
        }
        if (currentPair.length() > 0) {
            pairs.add(currentPair.toString().trim());
        } else {
            // If currentPair is empty after the loop, it indicates a trailing comma
            throw new IllegalArgumentException("Invalid JSON object: Trailing comma detected");
        }

        return pairs.toArray(new String[0]);
    }

    /**
     * Converts a JSON array string into a List of Maps.
     *
     * @param json The JSON array string to convert.
     * @return A List of Maps containing key-value pairs.
     */
    public static List<Map<String, String>> convertToMapList(String json) {
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Invalid JSON array format");
        }

        json = json.substring(1, json.length() - 1).trim();

        List<Map<String, String>> result = new ArrayList<>();

        // Split the JSON array into individual JSON objects
        int braceLevel = 0;
        StringBuilder currentObject = new StringBuilder();
        boolean insideString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            // Handle string literals to avoid counting braces inside strings
            if (c == '\"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                insideString = !insideString;
            }

            if (!insideString) {
                if (c == '{') {
                    braceLevel++;
                } else if (c == '}') {
                    braceLevel--;
                }
            }

            currentObject.append(c);

            if (braceLevel == 0 && currentObject.length() > 0 && !currentObject.toString().trim().isEmpty()) {
                // End of a JSON object
                String objectString = currentObject.toString().trim();

                // Skip commas and whitespace at the start of the object
                if (objectString.startsWith(",")) {
                    objectString = objectString.substring(1).trim();
                }

                if (!objectString.isEmpty()) {
                    result.add(convertToMap(objectString));
                }

                currentObject.setLength(0); // Reset the StringBuilder
            }
        }

        return result;
    }

    /**
     * Checks if a string is numeric.
     *
     * @param str The string to check.
     * @return True if the string represents a number, false otherwise.
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Escapes special characters in a JSON string.
     *
     * @param str The string to escape.
     * @return The escaped string.
     */
    public static String escapeString(String str) {
        // Proper escaping of special characters
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes special characters in a JSON string.
     *
     * @param str The string to unescape.
     * @return The unescaped string.
     */
    public static String unescapeString(String str) {
        // Unescaping of special characters
        StringBuilder sb = new StringBuilder();
        boolean isEscape = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (isEscape) {
                switch (c) {
                    case '\"':
                        sb.append('\"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        // Unicode escape
                        if (i + 4 < str.length()) {
                            String hex = str.substring(i + 1, i + 5);
                            try {
                                int code = Integer.parseInt(hex, 16);
                                sb.append((char) code);
                                i += 4;
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Invalid unicode escape sequence: \\u" + hex);
                            }
                        } else {
                            throw new IllegalArgumentException("Incomplete unicode escape sequence at end of string");
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid escape sequence: \\" + c);
                }
                isEscape = false;
            } else if (c == '\\') {
                isEscape = true;
            } else {
                sb.append(c);
            }
        }
        if (isEscape) {
            throw new IllegalArgumentException("Invalid escape sequence at end of string");
        }
        return sb.toString();
    }

    /**
     * Removes surrounding quotes from a string.
     *
     * @param str The string to strip quotes from.
     * @return The string without surrounding quotes.
     */
    private static String stripQuotes(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}