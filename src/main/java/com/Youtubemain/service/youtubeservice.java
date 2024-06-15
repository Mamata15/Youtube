package com.Youtubemain.service;

import com.Youtubemain.DTO.userDetails;
import com.Youtubemain.Entity.UserRepo;
import com.Youtubemain.Entity.youtubeEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class youtubeservice {

    @Value("${youtube.client-id}")
    private String clientId;

    @Value("${youtube.client-secret}")
    private String clientSecret;

    @Value("${youtube.redirect-uri}")
    private String redirectUri;

    @Value("${youtube.token-uri}")
    private String tokenUri;

    @Value("${youtube.channel-details-uri}")
    private String channelDetailsUri;

    @Value("${youtube.auth-uri}")
    private String authUri;

    @Value("${youtube.scope}")
    private String scope;

    private final RestTemplate restTemplate;
    private final UserRepo userRepository;

    public youtubeservice(UserRepo userRepository) {
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
    }

    public String getAuthorizationUrl() {
        return UriComponentsBuilder.fromHttpUrl(authUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .toUriString();
    }

    public String getAccessToken(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", authorizationCode);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(tokenUri, HttpMethod.POST, requestEntity, (Class<Map<String, Object>>)(Class<?>)Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("access_token")) {
                    return (String) responseBody.get("access_token");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Failed to retrieve access token");
    }


    public userDetails getChannelDetails(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        String url = "https://www.googleapis.com/youtube/v3/channels?mine=true&part=snippet,statistics&access_token=" + accessToken;

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            String responseBody = response.getBody();
            youtubeEntity channelDetails = parseChannelDetails(responseBody);
            return convertToDTO(channelDetails, "YouTube connected successfully!!!");
        } else {
            throw new RuntimeException("Failed to retrieve channel details. Status code: " + response.getStatusCode());
        }
    }

    public ResponseEntity<String> uploadVideo(String channelName, MultipartFile videoFile, String description) {
        List<youtubeEntity> channels = userRepository.findByChannelName(channelName);
        if (channels.isEmpty()) {
            return ResponseEntity.badRequest().body("Access token not found for the given channel name");
        }

        String accessToken = channels.get(channels.size() - 1).getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + accessToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource videoResource;
        try {
            videoResource = new ByteArrayResource(videoFile.getBytes()) {
                @Override
                public String getFilename() {
                    return videoFile.getOriginalFilename();
                }
            };
            body.add("file", videoResource);
            
            body.add("description", description);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to read the video file");
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String uploadUrl = "https://www.googleapis.com/upload/youtube/v3/videos?part=snippet";

        ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
            String errorMessage = "Error uploading video: 403 Forbidden: " + response.getBody();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorMessage);
        } else if (response.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.ok().body("Video uploaded successfully");
        } else {
            return ResponseEntity.status(response.getStatusCode()).body("Failed to upload video: " + response.getStatusCode() + ": " + response.getBody());
        }
    }

    public void saveOrUpdateChannelDetails(String accessToken, userDetails channelDetails) {
        List<youtubeEntity> existingChannels = userRepository.findByChannelName(channelDetails.getChannelName());
        if (!existingChannels.isEmpty()) {
            userRepository.deleteAll(existingChannels);
        }
        youtubeEntity entity = new youtubeEntity();
        entity.setAccessToken(accessToken);
        entity.setChannelName(channelDetails.getChannelName());
        entity.setSubscriberCount(channelDetails.getSubscriberCount());
        entity.setChannelImageUrl(channelDetails.getChannelImageUrl());
        userRepository.save(entity);
    }

    private youtubeEntity parseChannelDetails(String channelDetailsJson) {
        ObjectMapper objectMapper = new ObjectMapper();
        youtubeEntity channel = new youtubeEntity();
        try {
            JsonNode rootNode = objectMapper.readTree(channelDetailsJson);
            JsonNode itemsNode = rootNode.path("items").get(0);
            JsonNode snippetNode = itemsNode.path("snippet");
            channel.setChannelName(snippetNode.path("title").asText());
            channel.setSubscriberCount(itemsNode.path("statistics").path("subscriberCount").asInt());
            channel.setChannelImageUrl(snippetNode.path("thumbnails").path("default").path("url").asText());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return channel;
    }

    private userDetails convertToDTO(youtubeEntity channelDetails, String message) {
        userDetails dto = new userDetails();
        dto.setChannelName(channelDetails.getChannelName());
        dto.setSubscriberCount(channelDetails.getSubscriberCount());
        dto.setChannelImageUrl(channelDetails.getChannelImageUrl());
        dto.setMessage(message); 
        return dto;
    }
}