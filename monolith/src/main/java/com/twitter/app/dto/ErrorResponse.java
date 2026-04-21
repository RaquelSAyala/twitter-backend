package com.twitter.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
@Schema(name = "ErrorResponse", description = "Error payload")
public class ErrorResponse {

    @Schema(description = "Error message", example = "Content must be between 1 and 140 characters.")
    String message;
}
