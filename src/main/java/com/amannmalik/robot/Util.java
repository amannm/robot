package com.amannmalik.robot;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Amann Malik
 */
class Util {

    static Instant convertTimestamp(String ts) {
        String[] split = ts.split("\\.");
        long epochSeconds = Long.parseLong(split[0]);
        long epochNanoFraction = Long.parseLong(split[1]) * 1000;
        return Instant.ofEpochSecond(epochSeconds, epochNanoFraction);
    }

    static JsonObject fetchResource(String locationString) throws IOException {
        URL url = tryConstructUrl(locationString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        return handleJsonResponse(connection);
    }

    static JsonObject postResource(String locationString, Map<String, String> headers, JsonObject payload) throws IOException {


        byte[] body = getJsonBytes(payload);
        URL url = tryConstructUrl(locationString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        headers.forEach(connection::setRequestProperty);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        connection.setDoOutput(true);
        try (OutputStream outputStream = connection.getOutputStream()) {
            try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
                dataOutputStream.write(body);
            }
        }
        return handleJsonResponse(connection);
    }

    static byte[] getJsonBytes(JsonObject payload) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = Json.createWriter(stringWriter);
        jsonWriter.writeObject(payload);
        byte[] body = stringWriter.toString().getBytes(StandardCharsets.UTF_8);
        jsonWriter.close();
        return body;
    }

    static JsonObject fetchResource(String locationString, Map<String, String> headers) throws IOException {
        URL url = tryConstructUrl(locationString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        headers.forEach(connection::setRequestProperty);
        connection.setDoInput(true);
        return handleJsonResponse(connection);

    }

    private static JsonObject handleJsonResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        switch (responseCode) {
            case 200:
                try (InputStream inputStream = connection.getInputStream()) {
                    try (JsonReader parser = Json.createReader(inputStream)) {
                        return parser.readObject();
                    }
                }
            default:
                throw new RuntimeException("invalid response " + responseCode);
        }
    }

    private static URL tryConstructUrl(String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


}
