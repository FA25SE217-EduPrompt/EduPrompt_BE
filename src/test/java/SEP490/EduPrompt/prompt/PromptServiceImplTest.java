package SEP490.EduPrompt.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import SEP490.EduPrompt.service.prompt.PromptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptRepository promptRepository;
    @Mock private PromptViewLogRepository promptViewLogRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PromptTagRepository promptTagRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private PromptServiceImpl promptService;

    @Captor
    private ArgumentCaptor<Prompt> promptCaptor;

    @Captor
    private ArgumentCaptor<List<PromptTag>> promptTagCaptor;

    @Captor
    private ArgumentCaptor<UserQuota> userQuotaCaptor;

    // --- Helper Data ---
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID schoolId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final String EMAIL = "teacher@test.com";

    private UserPrincipal currentUser;
    private User user;
    private UserQuota userQuota;

    @BeforeEach
    void setUp() {
        // Common setup for UserPrincipal and User entity
        currentUser = UserPrincipal.builder()
                .userId(userId)
                .email(EMAIL)
                .role("TEACHER")
                .schoolId(schoolId)
                .build();

        user = User.builder()
                .id(userId)
                .email(EMAIL)
                .firstName("Test")
                .lastName("User")
                .build();

        userQuota = UserQuota.builder()
                .userId(userId)
                .promptActionRemaining(10)
                .quotaResetDate(Instant.now().plusSeconds(3600))
                .build();
    }

    // ======================================================================//
    // ====================== CREATE STANDALONE PROMPT ======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create Standalone - Success with valid data and tags")
    void createStandalonePrompt_WhenValidRequest_ShouldSavePromptAndDecrementQuota() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Math Prompt")
                .visibility("PUBLIC")
                .tagIds(List.of(UUID.randomUUID()))
                .build();

        Tag tag = Tag.builder()
                .id(UUID.randomUUID())
                .type("SUBJECT")
                .value("Math")
                .build();

        // 1. Mock User & Quota Lookup
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        // 2. Mock Permissions
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // 3. Mock Tag Lookup
        when(tagRepository.findAllById(request.getTagIds())).thenReturn(List.of(tag));

        // 4. Mock Save (Return prompt with ID)
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> {
            Prompt p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Act
        DetailPromptResponse response = promptService.createStandalonePrompt(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Math Prompt", response.getTitle());
        assertEquals("PUBLIC", response.getVisibility());

        // Verify Quota Decrement
        verify(userQuotaRepository).save(userQuotaCaptor.capture());
        assertEquals(9, userQuotaCaptor.getValue().getPromptActionRemaining());

        // Verify Prompt Save
        verify(promptRepository).save(promptCaptor.capture());
        Prompt savedPrompt = promptCaptor.getValue();
        assertEquals(userId, savedPrompt.getCreatedBy());
        assertNull(savedPrompt.getCollection()); // Standalone

        // Verify Tag Save
        verify(promptTagRepository).saveAll(promptTagCaptor.capture());
        assertFalse(promptTagCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("Case 2: Create Standalone - Fail - User Not Found")
    void createStandalonePrompt_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));

        verify(promptRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 3: Create Standalone - Fail - No Subscription (Quota Missing)")
    void createStandalonePrompt_WhenNoQuota_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 4: Create Standalone - Fail - Permission Denied")
    void createStandalonePrompt_WhenPermissionDenied_ShouldThrowAccessDeniedException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        when(permissionService.canCreatePrompt(currentUser)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Create Standalone - Fail - Quota Exceeded")
    void createStandalonePrompt_WhenQuotaExceeded_ShouldThrowQuotaExceededException() {
        // Arrange
        userQuota.setPromptActionRemaining(0); // Set to 0

        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Act & Assert
        assertThrows(QuotaExceededException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Create Standalone - Fail - Invalid Visibility (GROUP)")
    void createStandalonePrompt_WhenVisibilityGroup_ShouldThrowInvalidInputException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("GROUP")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 7: Create Standalone - Fail - Invalid Visibility (SCHOOL without Id)")
    void createStandalonePrompt_WhenVisibilitySchoolAndNoSchoolId_ShouldThrowInvalidInputException() {
        // Arrange
        UserPrincipal userWithoutSchool = UserPrincipal.builder()
                .userId(userId)
                .schoolId(null) // Null School ID
                .build();

        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("SCHOOL")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(userWithoutSchool)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createStandalonePrompt(request, userWithoutSchool));
    }

    @Test
    @DisplayName("Case 8: Create Standalone - Fail - Tags Not Found")
    void createStandalonePrompt_WhenTagsMissing_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("PUBLIC")
                .tagIds(List.of(UUID.randomUUID()))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Return empty list for tags
        when(tagRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    // ======================================================================//
    // ==================== CREATE PROMPT IN COLLECTION =====================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create In Collection - Success - Valid Collection & Perms")
    void createPromptInCollection_WhenValid_ShouldSavePromptLinkedToCollection() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PRIVATE")
                .title("Collection Prompt")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .user(user)
                .isDeleted(false)
                .build();

        // 1. Mock User (getReferenceById) & Quota
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        // 2. Mock Permissions
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // 3. Mock Collection Lookup
        when(collectionRepository.findByIdAndUserId(collectionId, userId))
                .thenReturn(Optional.of(collection));

        // 4. Mock Save
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> {
            Prompt p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Act
        DetailPromptResponse response = promptService.createPromptInCollection(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Collection Prompt", response.getTitle());

        // Verify Permission Check for Visibility
        verify(permissionService).validateCollectionVisibility(collection, "PRIVATE");

        // Verify Save with Collection
        verify(promptRepository).save(promptCaptor.capture());
        assertEquals(collection, promptCaptor.getValue().getCollection());
    }

    @Test
    @DisplayName("Case 2: Create In Collection - Fail - Collection Not Found")
    void createPromptInCollection_WhenCollectionNotFound_ShouldThrowException() {
        // Arrange
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(UUID.randomUUID())
                .visibility("PRIVATE")
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        when(collectionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 3: Create In Collection - Fail - Collection Deleted")
    void createPromptInCollection_WhenCollectionDeleted_ShouldThrowInvalidActionException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PRIVATE")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .isDeleted(true)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(InvalidActionException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 4: Create In Collection - Fail - Group Visibility & Not Member")
    void createPromptInCollection_WhenGroupVisibilityButNotMember_ShouldThrowAccessDenied() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("GROUP")
                .build();

        Group group = Group.builder()
                .id(groupId)
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .group(group)
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Mock Membership Check -> False
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Create In Collection - Fail - School Visibility Mismatch")
    void createPromptInCollection_WhenSchoolMismatch_ShouldThrowInvalidInputException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("SCHOOL")
                .build();

        User otherUser = User.builder()
                .schoolId(UUID.randomUUID()) // Different School ID
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .user(otherUser) // Owned by someone in diff school
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Create In Collection - Fail - Collection Visibility Violation")
    void createPromptInCollection_WhenCollectionVisibilityPreventsAction_ShouldThrowException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PUBLIC")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Mock Validation Exception from Service
        doThrow(new IllegalArgumentException("Collection is Private"))
                .when(permissionService).validateCollectionVisibility(collection, "PUBLIC");

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    // ======================================================================//
    // =========================== GET MY PROMPTS ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get My Prompts - Success - Returns Prompts With Tags")
    void getMyPrompts_WhenPromptsExistWithTags_ShouldReturnMappedData() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        // Setup User and Collection for mapping check
        User promptOwner = User.builder().firstName("John").lastName("Doe").build();
        Collection collection = Collection.builder().name("Math Coll").build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Calculus 101")
                .instruction("Solve integrals")
                .userId(userId)
                .user(promptOwner)
                .collection(collection)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Tag tag = Tag.builder().id(UUID.randomUUID()).type("SUBJECT").value("Math").build();
        PromptTag promptTag = PromptTag.builder()
                .prompt(prompt)
                .tag(tag)
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        // Mocks
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(List.of(promptTag));

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        // Verify Field Mapping
        DetailPromptResponse result = response.getContent().get(0);
        assertEquals(promptId, result.getId());
        assertEquals("Calculus 101", result.getTitle());
        assertEquals("John Doe", result.getFullName());
        assertEquals("Math Coll", result.getCollectionName());

        // Verify Tags Mapping
        assertEquals(1, result.getTags().size());
        assertEquals("Math", result.getTags().get(0).getValue());
    }

    @Test
    @DisplayName("Case 2: Get My Prompts - Success - No Prompts Found (Empty Page)")
    void getMyPrompts_WhenNoPrompts_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Return empty page
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(Page.empty());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());

        // Verify tag repo was NOT called (optimization check) or called with empty list
        // Depending on impl details, usually stream maps to empty list.
        // If the code calls findByPromptIdIn(emptyList), verification is:
        verify(promptTagRepository).findByPromptIdIn(Collections.emptyList());
    }

    @Test
    @DisplayName("Case 3: Get My Prompts - Success - Prompts Exist But Have No Tags")
    void getMyPrompts_WhenPromptsHaveNoTags_ShouldReturnEmptyTagList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Untitled")
                .userId(userId)
                .user(user)
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);

        // Return empty tags list
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(Collections.emptyList());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getContent().size());

        DetailPromptResponse result = response.getContent().get(0);
        assertNotNull(result.getTags());
        assertTrue(result.getTags().isEmpty());
    }

    @Test
    @DisplayName("Case 4: Get My Prompts - Success - Handle Null User and Collection (Edge Case)")
    void getMyPrompts_WhenPromptUserAndCollectionNull_ShouldHandleGracefully() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        // Prompt with NULL User and NULL Collection
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Orphan Prompt")
                .userId(userId)
                .user(null)      // <--- Null
                .collection(null) // <--- Null
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(Collections.emptyList());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        DetailPromptResponse result = response.getContent().get(0);

        // Verify fallback logic
        assertEquals("Unknown", result.getFullName()); // Should default to "Unknown"
        assertNull(result.getCollectionName());        // Should be null
    }

    // ======================================================================//
    // ====================== GET NON PRIVATE PROMPTS =======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Non-Private - Success - Returns Allowed Prompts")
    void getNonPrivatePrompts_WhenPromptsExistAndAuthorized_ShouldReturnList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // 1. Create Prompts with different visibilities
        Prompt publicPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("Public Prompt")
                .visibility(Visibility.PUBLIC.name())
                .build();

        Prompt schoolPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("School Prompt")
                .visibility(Visibility.SCHOOL.name())
                .build();

        List<Prompt> dbPrompts = List.of(publicPrompt, schoolPrompt);
        Page<Prompt> promptPage = new PageImpl<>(dbPrompts);

        // 2. Mock DB Retrieval (Specification is used, so we match any spec)
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // 3. Mock Permission Service (In-memory filtering)
        // Public should be allowed
        when(permissionService.canFilterPrompt(publicPrompt, currentUser)).thenReturn(true);
        // School prompt allowed (assume same school)
        when(permissionService.canFilterPrompt(schoolPrompt, currentUser)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getContent().size()); // Both returned
        assertEquals("Public Prompt", response.getContent().get(0).getTitle());
        assertEquals("School Prompt", response.getContent().get(1).getTitle());
    }

    @Test
    @DisplayName("Case 2: Get Non-Private - Success - Filters Out Unauthorized Prompts")
    void getNonPrivatePrompts_WhenSomeUnauthorized_ShouldFilterThemOut() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        Prompt publicPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .visibility(Visibility.PUBLIC.name())
                .build();

        Prompt differentSchoolPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .visibility(Visibility.SCHOOL.name())
                .build();

        List<Prompt> dbPrompts = List.of(publicPrompt, differentSchoolPrompt);
        Page<Prompt> promptPage = new PageImpl<>(dbPrompts);

        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // Mock Permission Service: Allow Public, Deny Different School
        when(permissionService.canFilterPrompt(publicPrompt, currentUser)).thenReturn(true);
        when(permissionService.canFilterPrompt(differentSchoolPrompt, currentUser)).thenReturn(false);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertEquals(1, response.getContent().size()); // Only 1 returned
        assertEquals(publicPrompt.getId(), response.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Case 3: Get Non-Private - Success - Empty Database Result")
    void getNonPrivatePrompts_WhenNoPromptsInDB_ShouldReturnEmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertTrue(response.getContent().isEmpty());
        // Verify permission service was never called since list was empty
        verify(permissionService, never()).canFilterPrompt(any(), any());
    }

    // ======================================================================//
    // ========================= GET PROMPT BY ID ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Prompt By ID - Success - Valid Prompt with Tags")
    void getPromptById_WhenFoundAndAuthorized_ShouldReturnDetailsWithTags() {
        // Arrange
        UUID promptId = UUID.randomUUID();

        // Mock User & Collection for mapping
        User owner = User.builder().firstName("Alice").lastName("Smith").build();
        Collection collection = Collection.builder().name("Physics Set").build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Detailed Prompt")
                .instruction("Do this")
                .isDeleted(false)
                .user(owner)
                .collection(collection)
                .build();

        Tag tag = Tag.builder().id(UUID.randomUUID()).type("LEVEL").value("Hard").build();
        PromptTag promptTag = PromptTag.builder().tag(tag).build();

        // 1. Mock Find
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // 2. Mock Access Check (Success)
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);

        // 3. Mock Tags
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(promptTag));

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals(promptId, response.getId());
        assertEquals("Detailed Prompt", response.getTitle());
        assertEquals("Alice Smith", response.getFullName());
        assertEquals("Physics Set", response.getCollectionName());

        // Check Tags
        assertEquals(1, response.getTags().size());
        assertEquals("Hard", response.getTags().get(0).getValue());
    }

    @Test
    @DisplayName("Case 2: Get Prompt By ID - Fail - Prompt Not Found In DB")
    void getPromptById_WhenIdNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, currentUser));
    }

    @Test
    @DisplayName("Case 3: Get Prompt By ID - Fail - Prompt Soft Deleted (Standard User)")
    void getPromptById_WhenDeletedAndUserNotAdmin_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(true) // <--- Deleted
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // Not Admin
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, currentUser));

        // Ensure message implies deletion or not found
        assertTrue(ex.getMessage().contains("deleted"));
    }

    @Test
    @DisplayName("Case 4: Get Prompt By ID - Success - Prompt Soft Deleted (System Admin)")
    void getPromptById_WhenDeletedButUserIsAdmin_ShouldReturnPrompt() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Deleted Prompt")
                .isDeleted(true) // <--- Deleted
                .user(user)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // IS Admin
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(true);

        // Access Check Mock
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);

        // Tags Mock
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(Collections.emptyList());

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Deleted Prompt", response.getTitle());
    }

    @Test
    @DisplayName("Case 5: Get Prompt By ID - Fail - Access Denied (Service Check Fails)")
    void getPromptById_WhenPermissionServiceDenies_ShouldThrowAccessDeniedException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(false)
                .visibility(Visibility.PRIVATE.name())
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // Mock permission check failing
        doThrow(new AccessDeniedException("This prompt is private"))
                .when(permissionService).validatePromptAccess(prompt, currentUser);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.getPromptById(promptId, currentUser));
    }

    @Test
    @DisplayName("Case 6: Get Prompt By ID - Success - Null User/Collection Handling")
    void getPromptById_WhenPromptHasNullRelations_ShouldHandleGracefully() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Orphan Prompt")
                .user(null)         // Null User
                .collection(null)   // Null Collection
                .isDeleted(false)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(Collections.emptyList());

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertEquals("Unknown", response.getFullName());
        assertNull(response.getCollectionName());
    }
}
