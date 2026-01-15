package SEP490.EduPrompt.prompt;

import SEP490.EduPrompt.dto.request.prompt.GeneratePromptFromFileRequest;
import SEP490.EduPrompt.dto.response.prompt.ClientPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.GeneratePromptFromFileResponse;
import SEP490.EduPrompt.enums.PromptTask;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.client.AiProviderException;
import SEP490.EduPrompt.exception.generic.InvalidFileException;
import SEP490.EduPrompt.service.ai.AiClientService;
import SEP490.EduPrompt.service.ai.PromptGenerationServiceImpl;
import SEP490.EduPrompt.service.ai.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptGenerationServiceImplTest {

    @Mock private QuotaService quotaService;
    @Mock private AiClientService aiClientService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PromptGenerationServiceImpl promptGenerationService;

    private UUID userId;
    private GeneratePromptFromFileRequest request;
    private MultipartFile mockFile;

    // Valid JSON string representing the expected structure from AI
    private final String VALID_JSON = """
            {
              "instruction": "Analyze this file",
              "context": "Financial report context",
              "input_example": "Raw text data",
              "output_format": "JSON summary",
              "constraints": "No markdown"
            }
            """;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockFile = mock(MultipartFile.class);

        // Mock Request DTO
        request = mock(GeneratePromptFromFileRequest.class);
        when(request.promptTask()).thenReturn(PromptTask.LESSON_PLAN);
        when(request.customInstruction()).thenReturn("Make it short");
    }

    @Test
    @DisplayName("Case 1: Generate Success - Valid File & AI Response (Happy Path)")
    void generatePromptFromFile_WhenValid_ShouldReturnResponse() throws IOException {
        // Arrange
        // 1. Mock File Inputs
        when(mockFile.getOriginalFilename()).thenReturn("test.txt");
        when(mockFile.getContentType()).thenReturn("text/plain");
        when(mockFile.getSize()).thenReturn(1024L);
        doNothing().when(mockFile).transferTo(any(java.io.File.class));

        // 2. Mock Gemini Upload
        when(aiClientService.uploadFileToGemini(any(), anyString(), anyString()))
                .thenReturn(mock(File.class));

        // 3. Mock AI Generation
        ClientPromptResponse aiResponse = ClientPromptResponse.builder()
                .model("gemini-1.5-flash")
                .content(VALID_JSON) // Now uses snake_case keys
                .totalTokens(150)
                .build();

        when(aiClientService.generatePromptFromFileContext(any(), any(), any(), any()))
                .thenReturn(aiResponse);

        // Act
        GeneratePromptFromFileResponse response = promptGenerationService.generatePromptFromFile(userId, mockFile, request);

        // Assert
        assertNotNull(response);
        assertEquals("Analyze this file", response.instruction());
        assertEquals("Financial report context", response.context());

        // Verify business logic
        verify(quotaService).validateAndDecrementQuota(eq(userId), eq(QuotaType.TEST), anyInt(), eq(true));
        verify(redisTemplate).convertAndSend(eq("file:upload"), anyString());
    }

    @Test
    @DisplayName("Case 2: Generate Failure - AI Provider Error (Refunds Quota)")
    void generatePromptFromFile_WhenAiFails_ShouldRefundQuotaAndThrowException() throws IOException {
        // Arrange
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        doNothing().when(mockFile).transferTo(any(java.io.File.class));

        when(aiClientService.uploadFileToGemini(any(), any(), any())).thenReturn(mock(File.class));

        // AI Service Throws Exception
        when(aiClientService.generatePromptFromFileContext(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Gemini API Unavailable"));

        // Act & Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> promptGenerationService.generatePromptFromFile(userId, mockFile, request));

        assertTrue(ex.getMessage().contains("Failed to generate prompt"));

        // Verify Full Refund (Default max tokens)
        verify(quotaService).refundQuotaAsync(eq(userId), eq(QuotaType.TEST), eq(AiClientService.DEFAULT_MAX_TOKEN));
    }

    @Test
    @DisplayName("Case 3: Generate Failure - Invalid JSON from AI (Refunds Quota)")
    void generatePromptFromFile_WhenAiReturnsBadJson_ShouldThrowException() throws IOException {
        // Arrange
        when(mockFile.getOriginalFilename()).thenReturn("data.csv");
        doNothing().when(mockFile).transferTo(any(java.io.File.class));
        when(aiClientService.uploadFileToGemini(any(), any(), any())).thenReturn(mock(File.class));

        // AI returns malformed JSON
        ClientPromptResponse badResponse = ClientPromptResponse.builder()
                .model("gemini-1.5-flash")
                .content("I'm sorry, I cannot parse this file into the requested JSON format.")
                .totalTokens(50)
                .build();

        when(aiClientService.generatePromptFromFileContext(any(), any(), any(), any()))
                .thenReturn(badResponse);

        // Act & Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> promptGenerationService.generatePromptFromFile(userId, mockFile, request));

        assertTrue(ex.getMessage().contains("Failed to parse AI response"));

        // Verify Refund
        verify(quotaService).refundQuotaAsync(eq(userId), eq(QuotaType.TEST), anyInt());
    }
}
