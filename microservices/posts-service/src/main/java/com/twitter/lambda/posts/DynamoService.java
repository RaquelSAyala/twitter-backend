package com.twitter.lambda.posts;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * DynamoDB helper for the posts-service.
 *
 * DynamoDB table expected:
 *   Table name  : twitter-posts  (or override with DYNAMODB_TABLE env var)
 *   Primary key : id  (String)
 *   Attributes  : content, author, authorId, createdAt, streamId
 *
 * The AWS SDK automatically uses the Lambda execution role credentials.
 * No access keys are needed in the code — set up the IAM role for the Lambda
 * with AmazonDynamoDBFullAccess (or a scoped policy).
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
     * Saves a post to DynamoDB. All fields are stored as String attributes.
     */
    public void savePost(Post post) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id",        str(post.getId()));
        item.put("content",   str(post.getContent()));
        item.put("author",    str(post.getAuthor()));
        item.put("authorId",  str(post.getAuthorId()));
        item.put("createdAt", str(post.getCreatedAt()));
        item.put("streamId",  str(post.getStreamId()));

        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    private AttributeValue str(String value) {
        return AttributeValue.builder().s(value != null ? value : "").build();
    }
}
