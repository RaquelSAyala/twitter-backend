package com.twitter.lambda.posts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda handler for POST /api/posts
 *
 * This microservice replaces the monolith's PostController#createPost() endpoint.
 * It validates the Auth0 JWT, creates a post, and saves it to DynamoDB.
 *
 * Environment variables required (set in AWS Lambda console):
 *   AUTH0_DOMAIN         → dev-zis6hlg4u4uwjsxd.us.auth0.com
 *   AUTH0_AUDIENCE       → https://twitter-api/
 *   AWS_REGION           → us-east-1  (set automatically by Lambda)
 *   DYNAMODB_TABLE       → twitter-posts  (optional, defaults to "twitter-posts")
 *   CORS_ALLOWED_ORIGINS → https://your-s3-bucket.s3.amazonaws.com
 *
 * Handler to configure in AWS Lambda:
 *   com.twitter.lambda.posts.PostsHandler::handleRequest
 *
 * Request body (JSON):
 *   { "content": "Hello world!", "username": "optional-display-name" }
 */
public class PostsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper  mapper       = new ObjectMapper();
    private final JwtValidator  jwtValidator = new JwtValidator();
    private final DynamoService dynamoService = new DynamoService();

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
            // 1. Validate JWT
            String authHeader = getHeader(input, "Authorization");
            if (authHeader == null || authHeader.isBlank()) {
                return errorResponse(401, "Missing Authorization header", headers);
            }
            DecodedJWT jwt = jwtValidator.validate(authHeader);

            // 2. Parse request body
            String body = input.getBody();
            if (body == null || body.isBlank()) {
                return errorResponse(400, "Request body is required", headers);
            }
            JsonNode bodyNode = mapper.readTree(body);

            // 3. Validate content
            String content = bodyNode.has("content") ? bodyNode.get("content").asText() : null;
            if (content == null || content.isBlank()) {
                return errorResponse(400, "Field 'content' is required", headers);
            }
            if (content.length() > 140) {
                return errorResponse(400, "Content must be 140 characters or fewer", headers);
            }

            // 4. Resolve author name from JWT (same logic as monolith)
            String author = resolveAuthor(jwt, bodyNode);

            // 5. Build and save the post
            Post post = new Post(
                    UUID.randomUUID().toString(),
                    content,
                    author,
                    jwt.getSubject(),
                    Instant.now().toString(),
                    "GLOBAL"
            );
            dynamoService.savePost(post);

            context.getLogger().log("POST /api/posts – created post: " + post.getId()
                    + " by " + post.getAuthorId());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(post));

        } catch (IllegalArgumentException | com.auth0.jwt.exceptions.JWTVerificationException e) {
            context.getLogger().log("Auth error: " + e.getMessage());
            return errorResponse(401, "Unauthorized: " + e.getMessage(), headers);
        } catch (Exception e) {
            context.getLogger().log("PostsHandler error: " + e.getClass().getSimpleName() + " – " + e.getMessage());
            return errorResponse(500, "Internal server error", headers);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolves a display name for the author.
     * Priority: optional 'username' field in body → email prefix → nickname → subject.
     */
    private String resolveAuthor(DecodedJWT jwt, JsonNode body) {
        // Optional username field from the request body
        if (body.has("username")) {
            String requested = body.get("username").asText("").trim();
            if (!requested.isBlank() && !requested.contains("|")) {
                return requested.contains("@")
                        ? requested.substring(0, requested.indexOf('@'))
                        : requested;
            }
        }

        // Email prefix  (e.g.  john@example.com → john)
        String email = jwt.getClaim("email").asString();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }

        // Auth0 nickname
        String nickname = jwt.getClaim("nickname").asString();
        if (nickname != null && !nickname.isBlank() && !nickname.contains("|")) {
            return nickname;
        }

        // Last resort: everything after the pipe in the subject (e.g. auth0|12345 → 12345)
        String sub = jwt.getSubject();
        if (sub != null && sub.contains("|")) {
            return sub.substring(sub.indexOf('|') + 1);
        }

        return sub;
    }

    /** Case-insensitive header lookup. */
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
        h.put("Access-Control-Allow-Methods", "POST,OPTIONS");
        h.put("Content-Type", "application/json");
        return h;
    }
}
