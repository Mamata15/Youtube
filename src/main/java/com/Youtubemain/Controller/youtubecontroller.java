package com.Youtubemain.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.Youtubemain.DTO.userDetails;
import com.Youtubemain.service.youtubeservice;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/post/file")
public class youtubecontroller {

	@Autowired
    private youtubeservice youtubeService;

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        String authorizationUrl = youtubeService.getAuthorizationUrl();
        Map<String, String> response = new HashMap<>();
        response.put("authorizationUrl", authorizationUrl);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/getChannelDetails")
    public ResponseEntity<userDetails> getChannelDetails(@RequestParam("code") String code) {
        try {
            String accessToken = youtubeService.getAccessToken(code);
            userDetails channelDetails = youtubeService.getChannelDetails(accessToken);
            youtubeService.saveOrUpdateChannelDetails(accessToken, channelDetails);
            return new ResponseEntity<>(channelDetails, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/youtube")
    public ResponseEntity<String> uploadVideoWithDescription(
            @RequestParam("channelName") String channelName,
            @RequestParam("file") MultipartFile videoFile,
            @RequestParam("description") String description) {
        try {
            if (channelName == null || channelName.isEmpty()) {
                return ResponseEntity.badRequest().body("Channel name is required");
            }
            ResponseEntity<String> uploadResponse = youtubeService.uploadVideo(channelName, videoFile, description);
            return ResponseEntity.ok().body(uploadResponse.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading video: " + e.getMessage());
        }
    }
}