package com.twitter.lambda.stream;

/**
 * Represents a post read from DynamoDB.
 * Same structure as posts-service Post, kept separate to keep services independent.
 */
public class Post {

    private String id;
    private String content;
    private String author;
    private String authorId;
    private String createdAt;
    private String streamId;

    public Post() {}

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
