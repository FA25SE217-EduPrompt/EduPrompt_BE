package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.search.FileSearchStoreResponse;
import SEP490.EduPrompt.dto.response.search.GroundingChunk;
import SEP490.EduPrompt.dto.response.search.ImportOperationResponse;
import SEP490.EduPrompt.service.search.GeminiClientService;
import com.google.genai.Pager;
import com.google.genai.types.Document;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/gemini")
@RequiredArgsConstructor
@Tag(name = "Gemini Admin", description = "Admin APIs for managing Gemini File Search")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class GeminiAdminController {

    private final GeminiClientService geminiClientService;

    @Operation(summary = "Create File Search Store", description = "Create a new store for indexing documents")
    @PostMapping("/stores")
    public ResponseDto<FileSearchStoreResponse> createStore(@RequestParam String displayName) {
        return ResponseDto.success(geminiClientService.createFileSearchStore(displayName));
    }

    @Operation(summary = "Get Store Details", description = "Get metadata for a file search store")
    @GetMapping("/stores/{storeId}")
    public ResponseDto<FileSearchStoreResponse> getStore(@PathVariable String storeId) {
        String fullStoreName = storeId.startsWith("fileSearchStores/") ? storeId : "fileSearchStores/" + storeId;
        return ResponseDto.success(geminiClientService.getFileSearchStore(fullStoreName));
    }

    @Operation(summary = "Delete Store", description = "Delete a file search store and all its documents")
    @DeleteMapping("/stores/{storeId}")
    public ResponseDto<Void> deleteStore(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "false") boolean force) {
        String fullStoreName = storeId.startsWith("fileSearchStores/") ? storeId : "fileSearchStores/" + storeId;
        geminiClientService.deleteFileSearchStore(fullStoreName, force);
        return ResponseDto.success(null);
    }

    @Operation(summary = "List Documents", description = "List all documents in a store")
    @GetMapping("/stores/{storeId}/documents")
    public ResponseDto<Pager<Document>> listDocuments(@PathVariable String storeId) {
        String fullStoreName = storeId.startsWith("fileSearchStores/") ? storeId : "fileSearchStores/" + storeId;
        return ResponseDto.success(geminiClientService.listDocumentsInStore(fullStoreName));
    }

    @Operation(summary = "Get Document", description = "Get document metadata")
    @GetMapping("/documents/{documentId}")
    public ResponseDto<Document> getDocument(@PathVariable String documentId) {
        return ResponseDto.success(geminiClientService.getDocument(documentId));
    }

    @Operation(summary = "Delete Document", description = "Delete a specific document")
    @DeleteMapping("/documents/{documentId}")
    public ResponseDto<Void> deleteDocument(@PathVariable String documentId) {
        geminiClientService.deleteDocument(documentId);
        return ResponseDto.success(null);
    }

    @Operation(summary = "Poll Operation", description = "Check status of long-running operation")
    @GetMapping("/operations/{operationName}")
    public ResponseDto<ImportOperationResponse> pollOperation(@PathVariable String operationName) {
        return ResponseDto.success(geminiClientService.pollOperation(operationName));
    }

    @Operation(summary = "Test Search", description = "Debug endpoint to test raw search against Gemini")
    @PostMapping("/stores/{storeId}/search")
    public ResponseDto<List<GroundingChunk>> testSearch(
            @PathVariable String storeId,
            @RequestBody Map<String, String> payload) {
        String fullStoreName = storeId.startsWith("fileSearchStores/") ? storeId : "fileSearchStores/" + storeId;
        String query = payload.get("query");
        int limit = Integer.parseInt(payload.getOrDefault("limit", "10"));

        return ResponseDto.success(geminiClientService.searchDocuments(fullStoreName, query, limit));
    }
}
