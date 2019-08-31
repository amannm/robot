package systems.cauldron.service.robot;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

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

    private static byte[] getJsonBytes(JsonObject payload) {
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
        headers.forEach(connection::setRequestProperty);
        connection.setRequestMethod("GET");
        return handleJsonResponse(connection);

    }

    private static JsonObject handleJsonResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        switch (responseCode) {
            case 200:
                try (JsonReader parser = Json.createReader(connection.getInputStream())) {
                    JsonObject jsonObject = parser.readObject();
                    if (jsonObject == null) {
                        throw new RuntimeException("empty response");
                    }
                    return jsonObject;
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
