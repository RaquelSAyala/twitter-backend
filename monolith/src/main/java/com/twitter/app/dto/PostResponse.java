package com.twitter.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@Schema(name = "PostResponse", description = "Post returned by the API")
public class PostResponse {

    @Schema(description = "Post identifier", example = "1")
    Long id;

    @Schema(description = "Post content", example = "Hola mundo desde el monolito")
    String content;

    @Schema(description = "Public author username", example = "juan-juan")
    String author;

    @Schema(description = "Author technical id from identity provider", example = "auth0|69e145c3a4f938eedcebcdb8")
    String authorId;

    @Schema(description = "Post creation datetime", example = "2026-04-16T21:15:00")
    LocalDateTime createdAt;
}
