package com.mario.backend.unittest.face;

import com.mario.backend.common.exception.ApiException;
import com.mario.backend.common.http.HttpClientException;
import com.mario.backend.common.http.HttpClientService;
import com.mario.backend.face.dto.FaceResponse;
import com.mario.backend.face.entity.FaceFeature;
import com.mario.backend.face.entity.FaceImage;
import com.mario.backend.face.repository.FaceFeatureRepository;
import com.mario.backend.face.repository.FaceImageRepository;
import com.mario.backend.face.service.FaceService;
import com.mario.backend.face.service.IdempotencyService;
import com.mario.backend.face.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static com.mario.backend.testutil.TestConstants.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceServiceTest {

    @Mock private FaceFeatureRepository faceFeatureRepository;
    @Mock private FaceImageRepository faceImageRepository;
    @Mock private MinioService minioService;
    @Mock private HttpClientService httpClientService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private FaceService faceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(faceService, "faceRecognitionServiceUrl", "http://localhost:5000");
    }

    @Test
    void registerFace_success() {
        String imageHash = "abc123";
        when(idempotencyService.computeImageHash(SAMPLE_IMAGE_BASE64)).thenReturn(imageHash);
        when(faceImageRepository.existsByUserIdAndImageHash(USER_ID, imageHash)).thenReturn(false);
        when(httpClientService.post(anyString(), anyMap())).thenReturn(
                "{\"code\":\"0000\",\"message\":\"Success\",\"data\":{\"encoding\":\"encoded\",\"algorithmReg\":\"facenet_mobilenet\"}}");
        when(minioService.uploadImage(eq(USER_ID), eq(SAMPLE_IMAGE_BASE64))).thenReturn("1/image.jpg");
        when(minioService.getBucketName()).thenReturn("face-images");
        when(faceFeatureRepository.save(any(FaceFeature.class))).thenAnswer(inv -> inv.getArgument(0));
        when(faceImageRepository.save(any(FaceImage.class))).thenAnswer(inv -> inv.getArgument(0));

        FaceResponse response = faceService.registerFace(USER_ID, SAMPLE_IMAGE_BASE64);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("0000");
        verify(faceFeatureRepository).save(any(FaceFeature.class));
        verify(faceImageRepository).save(any(FaceImage.class));
    }

    @Test
    void registerFace_duplicateImage_throws() {
        when(idempotencyService.computeImageHash(SAMPLE_IMAGE_BASE64)).thenReturn("dup-hash");
        when(faceImageRepository.existsByUserIdAndImageHash(USER_ID, "dup-hash")).thenReturn(true);

        assertThatThrownBy(() -> faceService.registerFace(USER_ID, SAMPLE_IMAGE_BASE64))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FACE_ALREADY_REGISTERED");
    }

    @Test
    void registerFace_externalServiceUnavailable_throws() {
        when(idempotencyService.computeImageHash(SAMPLE_IMAGE_BASE64)).thenReturn("hash");
        when(faceImageRepository.existsByUserIdAndImageHash(USER_ID, "hash")).thenReturn(false);
        when(httpClientService.post(anyString(), anyMap()))
                .thenThrow(new HttpClientException("url", "timeout"));

        assertThatThrownBy(() -> faceService.registerFace(USER_ID, SAMPLE_IMAGE_BASE64))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void recognizeFace_noCandidates_returnsNoFaces() {
        when(faceFeatureRepository.findAllByStatusAndAlgorithmReg(
                FaceFeature.FaceStatus.active, "facenet_mobilenet"))
                .thenReturn(List.of());

        FaceResponse response = faceService.recognizeFace(USER_ID, SAMPLE_IMAGE_BASE64);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("5002");
    }

    @Test
    void recognizeFace_withCandidates_callsSearch() {
        FaceFeature candidate = FaceFeature.builder()
                .userId(2L)
                .featureVector("encodedVector")
                .algorithmReg("facenet_mobilenet")
                .status(FaceFeature.FaceStatus.active)
                .build();
        when(faceFeatureRepository.findAllByStatusAndAlgorithmReg(
                FaceFeature.FaceStatus.active, "facenet_mobilenet"))
                .thenReturn(List.of(candidate));
        when(httpClientService.post(anyString(), anyMap())).thenReturn(
                "{\"code\":\"0000\",\"message\":\"Match found\",\"data\":{\"matches\":[{\"userId\":\"2\",\"distance\":0.5,\"matched\":true}]}}");

        FaceResponse response = faceService.recognizeFace(USER_ID, SAMPLE_IMAGE_BASE64);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Match found");
        verify(httpClientService).post(contains("/api/v1/face/search"), anyMap());
    }

    @Test
    void deleteFace_success_deactivatesFeature() {
        FaceFeature feature = FaceFeature.builder().userId(USER_ID).status(FaceFeature.FaceStatus.active).build();
        when(faceFeatureRepository.findByUserIdAndStatus(USER_ID, FaceFeature.FaceStatus.active))
                .thenReturn(Optional.of(feature));
        when(faceFeatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FaceResponse response = faceService.deleteFace(USER_ID);

        assertThat(response.getSuccess()).isTrue();
        assertThat(feature.getStatus()).isEqualTo(FaceFeature.FaceStatus.inactive);
        // No external HTTP call should be made for delete
        verifyNoInteractions(httpClientService);
    }

    @Test
    void isRegistered_exists_returnsTrue() {
        when(faceFeatureRepository.existsByUserIdAndStatus(USER_ID, FaceFeature.FaceStatus.active)).thenReturn(true);

        FaceResponse response = faceService.isRegistered(USER_ID);
        assertThat(response.getIsRegistered()).isTrue();
    }

    @Test
    void isRegistered_notExists_returnsFalse() {
        when(faceFeatureRepository.existsByUserIdAndStatus(USER_ID, FaceFeature.FaceStatus.active)).thenReturn(false);

        FaceResponse response = faceService.isRegistered(USER_ID);
        assertThat(response.getIsRegistered()).isFalse();
    }
}
