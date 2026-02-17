package com.mario.backend.face.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mario.backend.common.exception.ApiException;
import com.mario.backend.common.exception.ErrorCode;
import com.mario.backend.common.http.ExternalServiceResponse;
import com.mario.backend.common.http.HttpClientException;
import com.mario.backend.common.http.HttpClientService;
import com.mario.backend.common.http.NonRetryableHttpException;
import com.mario.backend.logging.annotation.Traceable;
import com.mario.backend.face.dto.FaceResponse;
import com.mario.backend.face.entity.FaceFeature;
import com.mario.backend.face.entity.FaceImage;
import com.mario.backend.face.repository.FaceFeatureRepository;
import com.mario.backend.face.repository.FaceImageRepository;
import com.mario.backend.users.entity.User;
import com.mario.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceService {

    private static final String DEFAULT_DET_ALGORITHM = "retinaface_mobilenet";
    private static final String DEFAULT_REG_ALGORITHM = "facenet_mobilenet";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FaceFeatureRepository faceFeatureRepository;
    private final FaceImageRepository faceImageRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final HttpClientService httpClientService;
    private final IdempotencyService idempotencyService;

    @Value("${face-recognition.service-url:http://face-recognition-service:5000}")
    private String faceRecognitionServiceUrl;

    @Traceable("face.registerFace")
    @Transactional
    public FaceResponse registerFace(Long userId, String imageData) {
        String imageHash = idempotencyService.computeImageHash(imageData);

        if (faceImageRepository.existsByUserIdAndImageHash(userId, imageHash)) {
            throw new ApiException(ErrorCode.FACE_ALREADY_REGISTERED);
        }

        try {
            IdempotencyService.setCurrentKey(imageHash);

            // Call face-ai-service /api/v1/face/encode (stateless)
            String url = faceRecognitionServiceUrl + "/api/v1/face/encode";

            ExternalServiceResponse response = new ExternalServiceResponse(httpClientService.post(url, Map.of(
                    "imageBase64", imageData,
                    "algorithmDet", DEFAULT_DET_ALGORITHM,
                    "algorithmReg", DEFAULT_REG_ALGORITHM
            )));

            if (response.isSuccess()) {
                JsonNode data = response.getData();
                String encoding = data != null && data.has("encoding")
                        ? data.get("encoding").asText()
                        : null;
                String algorithmReg = data != null && data.has("algorithmReg")
                        ? data.get("algorithmReg").asText()
                        : DEFAULT_REG_ALGORITHM;

                if (encoding != null) {
                    FaceFeature faceFeature = FaceFeature.builder()
                            .userId(userId)
                            .featureVector(encoding)
                            .algorithmReg(algorithmReg)
                            .status(FaceFeature.FaceStatus.active)
                            .build();
                    faceFeatureRepository.save(faceFeature);
                }

                String objectName = minioService.uploadImage(userId, imageData);
                FaceImage faceImage = FaceImage.builder()
                        .userId(userId)
                        .imagePath(objectName)
                        .bucketName(minioService.getBucketName())
                        .objectName(objectName)
                        .imageHash(imageHash)
                        .build();
                faceImageRepository.save(faceImage);

                return FaceResponse.builder()
                        .success(true)
                        .code("0000")
                        .message(response.getMessage())
                        .userId(userId)
                        .build();
            } else {
                throw new ApiException(ErrorCode.FACE_REGISTRATION_FAILED, response.getMessage());
            }
        } catch (ApiException e) {
            throw e;
        } catch (NonRetryableHttpException e) {
            log.error("External service rejected face registration for userId={}: status={}, message={}",
                    userId, e.getHttpStatusCode(), e.getMessage());
            throw new ApiException(ErrorCode.FACE_REGISTRATION_FAILED, e.getMessage());
        } catch (HttpClientException e) {
            log.error("External service unavailable during face registration for userId={}: {}", userId, e.getMessage());
            throw new ApiException(ErrorCode.EXTERNAL_SERVICE_RETRY_EXHAUSTED, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to register face for userId={}: {}", userId, e.getMessage());
            throw new ApiException(ErrorCode.FACE_REGISTRATION_FAILED, "Failed to register face: " + e.getMessage());
        } finally {
            IdempotencyService.clearCurrentKey();
        }
    }

    @Traceable("face.recognizeFace")
    public FaceResponse recognizeFace(Long userId, String imageData) {
        try {
            String algorithmReg = DEFAULT_REG_ALGORITHM;

            // Load all active candidates encoded with the same algorithm from MySQL
            List<FaceFeature> candidates = faceFeatureRepository
                    .findAllByStatusAndAlgorithmReg(FaceFeature.FaceStatus.active, algorithmReg);

            if (candidates.isEmpty()) {
                return FaceResponse.builder()
                        .success(false)
                        .code("5002")
                        .message("No registered faces found")
                        .userId(userId)
                        .build();
            }

            List<Map<String, String>> candidateList = candidates.stream()
                    .map(f -> Map.of(
                            "userId", String.valueOf(f.getUserId()),
                            "encoding", f.getFeatureVector()
                    ))
                    .toList();

            // Call face-ai-service /api/v1/face/search (stateless)
            String url = faceRecognitionServiceUrl + "/api/v1/face/search";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("imageBase64", imageData);
            requestBody.put("algorithmDet", DEFAULT_DET_ALGORITHM);
            requestBody.put("algorithmReg", algorithmReg);
            requestBody.put("candidates", candidateList);

            ExternalServiceResponse response = new ExternalServiceResponse(
                    httpClientService.post(url, requestBody));

            JsonNode enrichedData = enrichMatchesWithUserInfo(response.getData());

            return FaceResponse.builder()
                    .success(response.isSuccess())
                    .message(response.getMessage())
                    .userId(userId)
                    .code(response.getCode())
                    .data(enrichedData)
                    .build();
        } catch (NonRetryableHttpException e) {
            log.error("External service rejected face recognition for userId={}: status={}, message={}",
                    userId, e.getHttpStatusCode(), e.getMessage());
            throw new ApiException(ErrorCode.FACE_RECOGNITION_FAILED, e.getMessage());
        } catch (HttpClientException e) {
            log.error("External service unavailable during face recognition for userId={}: {}", userId, e.getMessage());
            throw new ApiException(ErrorCode.EXTERNAL_SERVICE_RETRY_EXHAUSTED, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to recognize face for userId={}: {}", userId, e.getMessage());
            throw new ApiException(ErrorCode.FACE_RECOGNITION_FAILED, "Failed to recognize face: " + e.getMessage());
        }
    }

    @Traceable("face.deleteFace")
    @Transactional
    public FaceResponse deleteFace(Long userId) {
        // No external service call needed — just mark inactive in MySQL
        faceFeatureRepository.findByUserIdAndStatus(userId, FaceFeature.FaceStatus.active)
                .ifPresent(feature -> {
                    feature.setStatus(FaceFeature.FaceStatus.inactive);
                    faceFeatureRepository.save(feature);
                });

        return FaceResponse.builder()
                .success(true)
                .message("Face data deleted successfully")
                .userId(userId)
                .code("0000")
                .build();
    }

    private JsonNode enrichMatchesWithUserInfo(JsonNode data) {
        if (data == null || !data.has("matches")) {
            return data;
        }

        JsonNode matchesNode = data.get("matches");
        if (!matchesNode.isArray() || matchesNode.isEmpty()) {
            return data;
        }

        // Filter to matched entries only (prevent leaking unmatched user info)
        List<JsonNode> matchedOnly = new java.util.ArrayList<>();
        for (JsonNode match : matchesNode) {
            if (match.has("matched") && match.get("matched").asBoolean()) {
                matchedOnly.add(match);
            }
        }

        // Collect userIds from matched entries
        List<Long> userIds = new java.util.ArrayList<>();
        for (JsonNode match : matchedOnly) {
            if (match.has("userId")) {
                try {
                    userIds.add(Long.parseLong(match.get("userId").asText()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid userId in match: {}", match.get("userId").asText());
                }
            }
        }

        // Batch fetch users
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // Build enriched matches array (matched only)
        ArrayNode enrichedMatches = OBJECT_MAPPER.createArrayNode();
        for (JsonNode match : matchedOnly) {
            ObjectNode enrichedMatch = OBJECT_MAPPER.createObjectNode();
            enrichedMatch.put("userId", match.get("userId").asText());
            enrichedMatch.put("distance", match.get("distance").asDouble());
            enrichedMatch.put("matched", true);

            try {
                Long matchUserId = Long.parseLong(match.get("userId").asText());
                User user = userMap.get(matchUserId);
                if (user != null) {
                    enrichedMatch.put("firstName", user.getFirstName());
                    enrichedMatch.put("lastName", user.getLastName());
                }
            } catch (NumberFormatException ignored) {
                // userId wasn't a valid number, skip enrichment
            }

            enrichedMatches.add(enrichedMatch);
        }

        // Build enriched data node
        ObjectNode enrichedData = OBJECT_MAPPER.createObjectNode();
        enrichedData.set("matches", enrichedMatches);
        if (data.has("query_encoding")) {
            enrichedData.put("query_encoding", data.get("query_encoding").asText());
        }
        return enrichedData;
    }

    @Traceable("face.isRegistered")
    public FaceResponse isRegistered(Long userId) {
        boolean registered = faceFeatureRepository.existsByUserIdAndStatus(userId, FaceFeature.FaceStatus.active);

        return FaceResponse.builder()
                .success(true)
                .isRegistered(registered)
                .userId(userId)
                .build();
    }
}
