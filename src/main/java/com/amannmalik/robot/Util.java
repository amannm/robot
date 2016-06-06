package com.amannmalik.robot;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;

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
}
