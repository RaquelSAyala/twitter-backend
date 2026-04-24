package com.twitter.lambda.stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for GET /api/posts  and  GET /api/stream  (public — no auth required)
 *
 * This microservice replaces the monolith's PostController#getStream() endpoint.
 * It reads all posts from DynamoDB and returns them sorted newest-first.
 *
 * Environment variables required (set in AWS Lambda console):
 *   AWS_REGION       → us-east-1  (set automatically by Lambda)
 *   DYNAMODB_TABLE   → twitter-posts  (optional, defaults to "twitter-posts")
 *   CORS_ALLOWED_ORIGINS → https://your-s3-bucket.s3.amazonaws.com (or * for dev)
 *
 * Handler to configure in AWS Lambda:
 *   com.twitter.lambda.stream.StreamHandler::handleRequest
 */
public class StreamHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper  mapper        = new ObjectMapper();
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
            List<Post> posts = dynamoService.getAllPostsSortedDesc();

            context.getLogger().log("GET /api/stream – returning " + posts.size() + " posts");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(posts));

        } catch (Exception e) {
            context.getLogger().log("StreamHandler error: " + e.getClass().getSimpleName() + " – " + e.getMessage());
            return errorResponse(500, "Internal server error", headers);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
