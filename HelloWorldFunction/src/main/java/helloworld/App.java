package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USERNAME = System.getenv("DB_USERNAME");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        if (input == null) {
            return createResponse(400, "{\"message\":\"Bad Request: Input is null\"}");
        }

        String path = input.getPath();
        String method = input.getHttpMethod();
        Map<String, String> pathParameters = input.getPathParameters();
        String pageContents = null;
        try {
            pageContents = this.getPageContents("https://checkip.amazonaws.com");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        context.getLogger().log("Received path: " + path + "\nMethod: " + method);

        try {
            //equalsIgnoreCase prevents Case Insensitivity, Code Safety(avoid bugs) and Clear Intent (request should be processed only for the GET)
            if ("GET".equalsIgnoreCase(method) && "/showAllPersons".equals(path)) {
                return createResponse(200, fetchAllPersonsFromDatabase());
            } else if ("GET".equalsIgnoreCase(method) && "/hi".equals(path)) {
                return createResponse(200, String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents));
            } else if ("PUT".equalsIgnoreCase(method) && pathParameters != null && pathParameters.containsKey("person_id")) {
                return handlePutRequest(input, pathParameters.get("person_id"));
            } else if ("POST".equalsIgnoreCase(method) && "/person".equals(path)) {
                return handlePostRequest(input);
            } else if ("DELETE".equalsIgnoreCase(method) && pathParameters != null && pathParameters.containsKey("person_id")) {
                return handleDeleteRequest(pathParameters.get("person_id"));
            } else {
                return createResponse(404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            return createResponse(500, "{\"message\":\"Internal Server Error\"}");
        }
    }

    //Fetching all the person from the database
    private String fetchAllPersonsFromDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String query = "SELECT * FROM person_entity";
            List<PersonEntity> persons = new ArrayList<>();

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PersonEntity person = new PersonEntity();
                    person.setId(rs.getLong("id"));
                    person.setName(rs.getString("name"));
                    person.setAge(rs.getInt("age"));
                    person.setProfession(rs.getString("profession"));
                    persons.add(person);
                }
            }

            return new ObjectMapper().writeValueAsString(persons);
        }
    }

    //Creating a new person
    private APIGatewayProxyResponseEvent handlePostRequest(APIGatewayProxyRequestEvent input) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String requestBody = input.getBody();
//            Map<String, Object> body = new ObjectMapper().readValue(requestBody, Map.class);
            // Deserialize with TypeReference to maintain type safety
            Map<String, Object> body = new ObjectMapper().readValue(
                    requestBody,
                    new TypeReference<>() {
                    }
            );
            String name = (String) body.get("name");
            Integer age = (Integer) body.get("age");
            String profession = (String) body.get("profession");

            //RETURNING id clause is specific to databases like PostgreSQL. This clause fetches the value of the id column that the database auto-increments.
            String insertQuery = "INSERT INTO person_entity (name, age, profession) VALUES (?, ?, ?) RETURNING id";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setString(1, name);
                stmt.setInt(2, age);
                stmt.setString(3, profession);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long id = rs.getLong("id");
                    return createResponse(201, "{\"message\":\"Person created successfully\", \"id\":\"" + id + "\"}");
                }
            }
        } catch (Exception e) {
            return createResponse(500, "{\"message\":\"Internal Server Error\"}");
        }
        return createResponse(400, "{\"message\":\"Unable to create person\"}");
    }

    //Updating the person
    private APIGatewayProxyResponseEvent handlePutRequest(APIGatewayProxyRequestEvent input, String personId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String requestBody = input.getBody();
//            Map<String, Object> body = new ObjectMapper().readValue(requestBody, Map.class);
            // Deserialize with TypeReference to maintain type safety
            Map<String, Object> body = new ObjectMapper().readValue(
                    requestBody,
                    new TypeReference<Map<String, Object>>() {}
            );

            String name = (String) body.get("name");
            Integer age = (Integer) body.get("age");
            String profession = (String) body.get("profession");

            String updateQuery = "UPDATE person_entity SET name = ?, age = ?, profession = ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                stmt.setString(1, name);
                stmt.setInt(2, age);
                stmt.setString(3, profession);
                stmt.setLong(4, Long.parseLong(personId));

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    return createResponse(200, "{\"message\":\"Person updated successfully\"}");
                } else {
                    return createResponse(404, "{\"message\":\"Person not found\"}");
                }
            }
        } catch (Exception e) {
            return createResponse(500, "{\"message\":\"Internal Server Error\"}");
        }
    }

    //Deleting the person
    private APIGatewayProxyResponseEvent handleDeleteRequest(String personId) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String deleteQuery = "DELETE FROM person_entity WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setLong(1, Long.parseLong(personId));

                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted > 0) {
                    return createResponse(200, "{\"message\":\"Person deleted successfully\"}");
                } else {
                    return createResponse(404, "{\"message\":\"Person not found\"}");
                }
            }
        } catch (Exception e) {
            return createResponse(500, "{\"message\":\"Internal Server Error\"}");
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }

    private String getPageContents(String address) throws IOException {
        URL url = new URL(address);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
