package com.twitter.lambda.stream;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DynamoDB helper for the stream-service.
 * Reads all posts from the twitter-posts table and returns them sorted by createdAt DESC.
 */
public class DynamoService {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoService() {
        String region = System.getenv("AWS_REGION");
        this.tableName = System.getenv("DYNAMODB_TABLE") != null
                ? System.getenv("DYNAMODB_TABLE")
                : "twitter-posts";

        this.client = DynamoDbClient.builder()
                .region(Region.of(region != null ? region : "us-east-1"))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

    /**
     * Returns all posts from DynamoDB, sorted by createdAt descending (newest first).
     * Uses a full table Scan — acceptable for an academic project with limited data.
     */
    public List<Post> getAllPostsSortedDesc() {
        ScanResponse response = client.scan(ScanRequest.builder()
                .tableName(tableName)
                .build());

        return response.items().stream()
                .map(this::toPost)
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    private Post toPost(Map<String, AttributeValue> item) {
        Post post = new Post();
        post.setId(str(item, "id"));
        post.setContent(str(item, "content"));
        post.setAuthor(str(item, "author"));
        post.setAuthorId(str(item, "authorId"));
        post.setCreatedAt(str(item, "createdAt"));
        post.setStreamId(str(item, "streamId"));
        return post;
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }
}
