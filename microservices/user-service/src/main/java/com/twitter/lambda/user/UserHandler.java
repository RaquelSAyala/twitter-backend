package com.twitter.lambda.user;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for GET /api/me
 *
 * This microservice replaces the monolith's PostController#getMe() endpoint.
 * It validates the Auth0 JWT and returns the user's profile claims.
 * No database access is needed — all info comes from the token itself.
 *
 * Environment variables required (set in AWS Lambda console):
 *   AUTH0_DOMAIN   → dev-zis6hlg4u4uwjsxd.us.auth0.com
 *   AUTH0_AUDIENCE → https://twitter-api/
 *   CORS_ALLOWED_ORIGINS → https://your-s3-bucket.s3.amazonaws.com (or * for dev)
 *
 * Handler to configure in AWS Lambda:
 *   com.twitter.lambda.user.UserHandler::handleRequest
 */
public class UserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtValidator jwtValidator = new JwtValidator();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = corsHeaders();

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(input.getHttpMethod())) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody("");
        }

        try {
            // Get Authorization header (API Gateway may lowercase it)
            String authHeader = getHeader(input, "Authorization");

            if (authHeader == null || authHeader.isBlank()) {
                return errorResponse(401, "Missing Authorization header", headers);
            }

            DecodedJWT jwt = jwtValidator.validate(authHeader);

            // Extract user profile claims from the JWT
            Map<String, Object> user = new HashMap<>();
            user.put("id",       jwt.getSubject());
            user.put("email",    jwt.getClaim("email").asString());
            user.put("nickname", jwt.getClaim("nickname").asString());
            user.put("picture",  jwt.getClaim("picture").asString());

            context.getLogger().log("GET /api/me – user: " + jwt.getSubject());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(user));

        } catch (Exception e) {
            context.getLogger().log("UserHandler error: " + e.getMessage());
            return errorResponse(401, "Unauthorized: " + e.getMessage(), headers);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Case-insensitive header lookup (API Gateway may lowercase headers). */
    private String getHeader(APIGatewayProxyRequestEvent input, String name) {
        if (input.getHeaders() == null) return null;
        String value = input.getHeaders().get(name);
        if (value == null) value = input.getHeaders().get(name.toLowerCase());
        return value;
    }

    private APIGatewayProxyResponseEvent errorResponse(int status, String message,
                                                        Map<String, String> headers) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(Map.of("error", message)));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(headers)
                    .withBody("{\"error\":\"" + message + "\"}");
        }
    }

    private Map<String, String> corsHeaders() {
        String origins = System.getenv("CORS_ALLOWED_ORIGINS");
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin",  origins != null ? origins : "*");
        h.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        h.put("Access-Control-Allow-Methods", "GET,OPTIONS");
        h.put("Content-Type", "application/json");
        return h;
    }
}
