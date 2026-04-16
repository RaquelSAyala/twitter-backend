package com.twitter.app.controller;

import com.twitter.app.dto.BackendInstanceResponse;
import com.twitter.app.dto.CreatePostRequest;
import com.twitter.app.dto.ErrorResponse;
import com.twitter.app.dto.PostResponse;
import com.twitter.app.model.Post;
import com.twitter.app.repository.PostRepository;
import com.twitter.app.model.Stream;
import com.twitter.app.model.User;
import com.twitter.app.repository.StreamRepository;
import com.twitter.app.repository.UserRepository;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Twitter API", description = "Endpoints for sharing and viewing posts")
public class PostController {

    private static final String GLOBAL_STREAM_NAME = "GLOBAL_STREAM";
    private static final String FALLBACK_USERNAME = "default_user";
    private static final String BACKEND_INSTANCE_ID = UUID.randomUUID().toString();

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StreamRepository streamRepository;

    private String usernameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return null;
    }

    private String normalizeUsername(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        if (value.contains("@")) {
            value = value.substring(0, value.indexOf('@'));
        }

        if (value.contains("|")) {
            return null;
        }

        return value;
    }

    private String resolveAuthorName(User user, String fallbackAuth0Id) {
        if (user == null && fallbackAuth0Id != null) {
            user = userRepository.findByAuth0Id(fallbackAuth0Id).orElse(null);
        }

        if (user != null) {
            String username = usernameFromEmail(user.getEmail());
            if (username != null && !username.isBlank()) {
                return username;
            }

            if (user.getNickname() != null && !user.getNickname().isBlank()
                    && !FALLBACK_USERNAME.equalsIgnoreCase(user.getNickname().trim())) {
                return user.getNickname();
            }

            if (user.getAuth0Id() != null && user.getAuth0Id().contains("|")) {
                return user.getAuth0Id().substring(user.getAuth0Id().indexOf('|') + 1);
            }
        }

        if (fallbackAuth0Id != null && fallbackAuth0Id.contains("@")) {
            return fallbackAuth0Id.substring(0, fallbackAuth0Id.indexOf('@'));
        }

        if (fallbackAuth0Id != null && fallbackAuth0Id.contains("|")) {
            return fallbackAuth0Id.substring(fallbackAuth0Id.indexOf('|') + 1);
        }

        return fallbackAuth0Id;
    }

    private User resolveUser(Jwt jwt) {
        String auth0Id = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nickname = jwt.getClaimAsString("nickname");
        String picture = jwt.getClaimAsString("picture");

        String username = usernameFromEmail(email);
        if (username != null && !username.isBlank()) {
            nickname = username;
        }

        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseGet(() -> User.builder()
                        .auth0Id(auth0Id)
                        .createdAt(LocalDateTime.now())
                        .build());

        user.setEmail(email);
        user.setNickname(nickname);
        user.setPicture(picture);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    private Stream resolveGlobalStream() {
        Stream stream = streamRepository.findByName(GLOBAL_STREAM_NAME)
                .orElseGet(() -> Stream.builder()
                        .name(GLOBAL_STREAM_NAME)
                        .createdAt(LocalDateTime.now())
                        .build());

        stream.setUpdatedAt(LocalDateTime.now());
        return streamRepository.save(stream);
    }

    private PostResponse toPostResponse(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .author(post.getAuthor())
                .authorId(post.getAuthorId())
                .createdAt(post.getCreatedAt())
                .build();
    }

    @GetMapping({"/posts", "/stream"})
        @Operation(summary = "Get all posts", description = "Returns the global stream of posts ordered by date descending", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Posts retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PostResponse.class))))
    })
    public List<PostResponse> getStream() {
        Stream stream = resolveGlobalStream();
        List<Post> posts = postRepository.findAllByStreamIdOrderByCreatedAtDesc(stream.getId());
        posts.forEach(post -> {
            String existingAuthor = normalizeUsername(post.getAuthor());
            if (existingAuthor != null && !FALLBACK_USERNAME.equalsIgnoreCase(existingAuthor)) {
                post.setAuthor(existingAuthor);
            } else {
                post.setAuthor(resolveAuthorName(post.getUser(), post.getAuthorId()));
            }
        });
        return posts.stream().map(this::toPostResponse).toList();
    }

    @GetMapping("/instance")
        @Operation(summary = "Get backend instance", description = "Returns an instance identifier that changes each backend restart", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Instance id retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BackendInstanceResponse.class)))
    })
    public BackendInstanceResponse getBackendInstance() {
        return BackendInstanceResponse.builder().instanceId(BACKEND_INSTANCE_ID).build();
    }

    @PostMapping("/posts")
    @Operation(summary = "Create a new post", description = "Allows an authenticated user to create a post (max 140 chars)", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Post created successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ResponseEntity<?> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Post payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreatePostRequest.class))
            )
            @Valid @RequestBody CreatePostRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        String content = body.getContent();
        if (content == null || content.length() > 140) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Content must be between 1 and 140 characters."));
        }

        User user = resolveUser(jwt);
        Stream stream = resolveGlobalStream();
        String requestedUsername = normalizeUsername(body.getUsername());

        Post post = Post.builder()
                .content(content)
                .author(requestedUsername != null ? requestedUsername : resolveAuthorName(user, jwt.getSubject()))
                .authorId(jwt.getSubject())
                .user(user)
                .stream(stream)
                .createdAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(toPostResponse(postRepository.save(post)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info", description = "Returns information about the authenticated user", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authenticated user profile",
                content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public User getMe(@AuthenticationPrincipal Jwt jwt) {
        return resolveUser(jwt);
    }
}
