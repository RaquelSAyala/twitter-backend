package com.twitter.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "CreatePostRequest", description = "Payload to create a new post")
public class CreatePostRequest {

    @NotBlank
    @Size(max = 140)
    @Schema(description = "Post content", example = "Hola mundo desde el monolito", maxLength = 140)
    private String content;

    @Schema(description = "Preferred public username (text before @). Optional.", example = "juan-juan")
    private String username;
}
