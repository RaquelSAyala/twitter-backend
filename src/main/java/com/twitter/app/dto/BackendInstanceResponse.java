package com.twitter.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "BackendInstanceResponse", description = "Current backend runtime instance")
public class BackendInstanceResponse {

    @Schema(description = "Unique identifier generated at app startup", example = "c6ef2ae1-b4ef-4ab5-b57f-f6cbd95da62c")
    String instanceId;
}
