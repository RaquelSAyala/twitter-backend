package com.twitter.app.controller;

import com.twitter.app.model.Post;
import com.twitter.app.repository.PostRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Twitter API", description = "Endpoints for sharing and viewing posts")
public class PostController {

    private final PostRepository postRepository;

    @GetMapping({"/posts", "/stream"})
    @Operation(summary = "Get all posts", description = "Returns the global stream of posts ordered by date descending")
    public List<Post> getStream() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/posts")
    @Operation(summary = "Create a new post", description = "Allows an authenticated user to create a post (max 140 chars)", 
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> createPost(@RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt jwt) {
        String content = body.get("content");
        if (content == null || content.length() > 140) {
            return ResponseEntity.badRequest().body("Content must be between 1 and 140 characters.");
        }

        Post post = Post.builder()
                .content(content)
                .author(jwt.getClaimAsString("nickname") != null ? jwt.getClaimAsString("nickname") : jwt.getSubject())
                .authorId(jwt.getSubject())
                .createdAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(postRepository.save(post));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info", description = "Returns information about the authenticated user", 
               security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, Object> getMe(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", jwt.getSubject());
        claims.put("nickname", jwt.getClaimAsString("nickname"));
        claims.put("email", jwt.getClaimAsString("email"));
        claims.put("picture", jwt.getClaimAsString("picture"));
        return claims;
    }
}
