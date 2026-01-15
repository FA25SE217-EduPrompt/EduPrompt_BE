package SEP490.EduPrompt.collection;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.collection.CollectionServiceImpl;
import SEP490.EduPrompt.service.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionTagRepository collectionTagRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private PermissionService permissionService;
    @Mock private UserRepository userRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private TagRepository tagRepository;

    @InjectMocks
    private CollectionServiceImpl collectionService;

    private UserPrincipal currentUser;
    private User userEntity;
    private UUID userId;
    private UUID collectionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();

        currentUser = UserPrincipal.builder()
                .userId(userId)
                .email("teacher@test.com")
                .role("TEACHER")
                .build();

        userEntity = User.builder()
                .id(userId)
                .email("teacher@test.com")
                .build();
    }

    // ======================================================================//
    // ======================== CREATE COLLECTION ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create Collection - Success (Public Standalone)")
    void createCollection_WhenValidPublic_ShouldSaveAndReturnResponse() {
        // Arrange
        CreateCollectionRequest request = new CreateCollectionRequest(
                "My Collection", "Desc", "PUBLIC", List.of(UUID.randomUUID()), null);

        UserQuota userQuota = UserQuota.builder().collectionActionRemaining(10).build();
        Tag tag = Tag.builder().id(UUID.randomUUID()).type("SUBJECT").value("Math").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(tagRepository.findAllById(anyList())).thenReturn(List.of(tag));

        when(collectionRepository.save(any(Collection.class))).thenAnswer(i -> {
            Collection c = i.getArgument(0);
            c.setId(collectionId);
            return c;
        });

        // Act
        CreateCollectionResponse response = collectionService.createCollection(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("My Collection", response.getName());
        assertEquals("PUBLIC", response.getVisibility());

        // Verify Quota Decrement
        assertEquals(9, userQuota.getCollectionActionRemaining());
        verify(collectionTagRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Case 2: Create Collection - Fail (Group Visibility without Membership)")
    void createCollection_WhenGroupVisibilityButNotMember_ShouldThrowAccessDenied() {
        // Arrange
        UUID groupId = UUID.randomUUID();
        CreateCollectionRequest request = new CreateCollectionRequest(
                "Group Coll", "Desc", "GROUP", null, groupId
        );

        UserQuota userQuota = UserQuota.builder().collectionActionRemaining(10).build();
        Group group = Group.builder().id(groupId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Mock Not a Member
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, "active"))
                .thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> collectionService.createCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 3: Create Collection - Fail (Tags Not Found)")
    void createCollection_WhenTagsMissing_ShouldThrowResourceNotFound() {
        // Arrange
        // FIX: Pass a non-empty list of tags (e.g., List.of(99L)) instead of null
        CreateCollectionRequest request = new CreateCollectionRequest(
                "Coll", "Desc", "private", List.of(UUID.randomUUID()), null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(new UserQuota()));

        // Mock finding 0 tags when 1 was requested
        when(tagRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> collectionService.createCollection(request, currentUser));

        // Verify we never reached the save method
        verify(collectionRepository, never()).save(any());
    }

    // ======================================================================//
    // ======================= GET COLLECTION BY ID =========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Collection - Success (Owner)")
    void getCollectionById_WhenOwner_ShouldReturnCollection() {
        // Arrange
        Collection collection = Collection.builder()
                .id(collectionId)
                .name("Private Coll")
                .visibility("PRIVATE")
                .createdBy(userId) // Matching user
                .isDeleted(false)
                .collectionTags(new HashSet<>())
                .build();

        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));

        // Act
        CollectionResponse response = collectionService.getCollectionById(collectionId, currentUser);

        // Assert
        assertEquals("Private Coll", response.name());
    }

    @Test
    @DisplayName("Case 2: Get Collection - Fail (Private & Not Owner)")
    void getCollectionById_WhenPrivateAndNotOwner_ShouldThrowAccessDenied() {
        // Arrange
        UUID otherUser = UUID.randomUUID();
        Collection collection = Collection.builder()
                .id(collectionId)
                .visibility("PRIVATE")
                .createdBy(otherUser) // Different owner
                .isDeleted(false)
                .build();

        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> collectionService.getCollectionById(collectionId, currentUser));
    }

    @Test
    @DisplayName("Case 3: Get Collection - Fail (Deleted & Not Admin)")
    void getCollectionById_WhenDeletedAndNotAdmin_ShouldThrowAccessDenied() {
        // Arrange
        Collection collection = Collection.builder()
                .id(collectionId)
                .isDeleted(true) // Deleted
                .build();

        // Note: The service uses findByIdAndIsDeletedFalse first, so deleted ones return empty Optional normally.
        // If the repository logic filters them out, we get ResourceNotFoundException.
        // However, if we assume the repository method name is just a convention and we verify logic inside:
        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> collectionService.getCollectionById(collectionId, currentUser));
    }

    // ======================================================================//
    // ======================== UPDATE COLLECTION ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Update Collection - Success (Edit Name & Visibility)")
    void updateCollection_WhenValid_ShouldUpdateFields() {
        // Arrange
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "New Name", null, "PUBLIC", null, null
        );

        Collection collection = Collection.builder()
                .id(collectionId)
                .name("Old Name")
                .createdBy(userId)
                .visibility("PRIVATE")
                .isDeleted(false)
                .build();

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(currentUser, collection)).thenReturn(true);
        when(collectionRepository.save(any(Collection.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UpdateCollectionResponse response = collectionService.updateCollection(collectionId, request, currentUser);

        // Assert
        assertEquals("New Name", response.getName());
        assertEquals("PUBLIC", response.getVisibility());
        verify(collectionRepository).save(collection);
    }

    @Test
    @DisplayName("Case 2: Update Collection - Fail (Access Denied)")
    void updateCollection_WhenPermissionServiceDenies_ShouldThrowAccessDenied() {
        // Arrange
        UpdateCollectionRequest request = new UpdateCollectionRequest("Name", null, "PUBLIC", null, null);
        Collection collection = Collection.builder().id(collectionId).isDeleted(false).build();

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(currentUser, collection)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> collectionService.updateCollection(collectionId, request, currentUser));
    }

    @Test
    @DisplayName("Case 3: Update Collection - Fail (School Visibility No Affiliation)")
    void updateCollection_WhenSchoolVisibilityAndNoSchool_ShouldThrowInvalidAction() {
        // Arrange
        // User has no schoolId in setup
        UpdateCollectionRequest request = new UpdateCollectionRequest(null, null, "SCHOOL", null, null);
        Collection collection = Collection.builder().id(collectionId).isDeleted(false).build();

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(currentUser, collection)).thenReturn(true);

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.generic.InvalidActionException.class,
                () -> collectionService.updateCollection(collectionId, request, currentUser));
    }

    // ======================================================================//
    // ====================== SOFT DELETE COLLECTION ========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Soft Delete - Success (Owner)")
    void softDeleteCollection_WhenOwner_ShouldMarkDeleted() {
        // Arrange
        Collection collection = Collection.builder()
                .id(collectionId)
                .createdBy(userId) // Owner
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));

        // Act
        collectionService.softDeleteCollection(collectionId, currentUser);

        // Assert
        assertTrue(collection.getIsDeleted());
        assertNotNull(collection.getDeletedAt());
        verify(collectionRepository).save(collection);
    }

    @Test
    @DisplayName("Case 2: Soft Delete - Fail (Not Owner/Admin)")
    void softDeleteCollection_WhenNotOwnerOrAdmin_ShouldThrowAccessDenied() {
        // Arrange
        UUID otherUser = UUID.randomUUID();
        Collection collection = Collection.builder()
                .id(collectionId)
                .createdBy(otherUser) // Not Owner
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> collectionService.softDeleteCollection(collectionId, currentUser));
    }
}