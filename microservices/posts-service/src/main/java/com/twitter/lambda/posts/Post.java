package com.twitter.lambda.posts;

/**
 * Represents a post stored in DynamoDB.
 * Mirrors the monolith's Post entity but without JPA/Hibernate annotations.
 */
public class Post {

    private String id;
    private String content;
    private String author;
    private String authorId;
    private String createdAt;   // ISO-8601 string (e.g. "2024-04-20T19:00:00Z")
    private String streamId;    // Always "GLOBAL" for the single public stream

    public Post() {}

    public Post(String id, String content, String author, String authorId,
                String createdAt, String streamId) {
        this.id        = id;
        this.content   = content;
        this.author    = author;
        this.authorId  = authorId;
        this.createdAt = createdAt;
        this.streamId  = streamId;
    }

    public String getId()        { return id; }
    public String getContent()   { return content; }
    public String getAuthor()    { return author; }
    public String getAuthorId()  { return authorId; }
    public String getCreatedAt() { return createdAt; }
    public String getStreamId()  { return streamId; }

    public void setId(String id)               { this.id = id; }
    public void setContent(String content)     { this.content = content; }
    public void setAuthor(String author)       { this.author = author; }
    public void setAuthorId(String authorId)   { this.authorId = authorId; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setStreamId(String streamId)   { this.streamId = streamId; }
}
