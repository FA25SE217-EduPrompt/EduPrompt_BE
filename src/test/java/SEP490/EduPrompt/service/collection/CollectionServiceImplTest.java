package SEP490.EduPrompt.service.collection;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Group;
import SEP490.EduPrompt.model.Tag;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {
    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionTagRepository collectionTagRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private CollectionServiceImpl collectionService;
    private UserPrincipal userPrincipal;
    private User user;
    private UUID collectionId;
    private UUID userId;
    private UUID schoolId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        collectionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        // Use uppercase role to match UserPrincipal.getRole() behavior
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.TEACHER.name())
                .schoolId(schoolId)
                .build();
        user = User.builder()
                .id(userId)
                .schoolId(schoolId)
                .build();
    }

    //================================================================//
    //====================CREATE COLLECTION===========================//
    @Test
    void createCollection_Success_PublicVisibility_Teacher() {
        // Arrange
        UUID tagId = UUID.randomUUID(); // Create a valid tag ID
        CreateCollectionRequest request = new CreateCollectionRequest(
                "Test Collection",
                "Description",
                Visibility.PUBLIC.name(),
                List.of(tagId), // Provide a non-null list with a tag ID
                null // groupId can be null for public visibility
        );
        Tag tag = Tag.builder().id(tagId).type("category").value("test").build();
        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .description(request.description())
                .visibility(request.visibility())
                .createdBy(userId)
                .updatedBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .user(user)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tagRepository.findAllById(request.tags())).thenReturn(List.of(tag));
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);
        when(collectionTagRepository.saveAll(anyList())).thenReturn(List.of());

        // Act
        CreateCollectionResponse response = collectionService.createCollection(request, userPrincipal);

        // Assert
        assertNotNull(response);
        assertEquals(request.name(), response.getName());
        assertEquals(request.description(), response.getDescription());
        assertEquals(request.visibility(), response.getVisibility());
        assertEquals(1, response.getTags().size());
        verify(collectionRepository).save(any(Collection.class));
        verify(collectionTagRepository).saveAll(anyList());
    }

    @Test
    void createCollection_GroupVisibility_NonMember_ThrowsAccessDenied() {
        // Arrange
        CreateCollectionRequest request = new CreateCollectionRequest(
                "Test Collection",
                "Description",
                Visibility.GROUP.name(),
                List.of(UUID.randomUUID()),
                groupId
        );
        Group group = Group.builder().id(groupId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, "active")).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> collectionService.createCollection(request, userPrincipal));
    }

    @Test
    void createCollection_InvalidTag_ThrowsResourceNotFound() {
        // Arrange
        CreateCollectionRequest request = new CreateCollectionRequest(
                "Test Collection",
                "Description",
                Visibility.PUBLIC.name(),
                List.of(UUID.randomUUID()), // Provide a non-empty list with an invalid tag ID
                null // groupId can be null for public visibility
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tagRepository.findAllById(request.tags())).thenReturn(List.of()); // Simulate no tags found

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> collectionService.createCollection(request, userPrincipal));
    }

    //================================================================//
    //====================UPDATE COLLECTION===========================//
    @Test
    void updateCollection_Success_SystemAdmin() {
        // Arrange
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role("SYSTEM_ADMIN") // Uppercase role
                .schoolId(schoolId)
                .build();
        UUID collectionId = UUID.randomUUID();
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "Updated Collection",
                "Updated Description",
                Visibility.PUBLIC.name(),
                List.of(UUID.randomUUID()),
                groupId
        );
        Collection collection = Collection.builder()
                .id(collectionId)
                .name("Old Collection")
                .description("Old Description")
                .visibility(Visibility.PRIVATE.name())
                .createdBy(UUID.randomUUID()) // Different user
                .updatedBy(userId)
                .isDeleted(false)
                .build();
        Tag tag = Tag.builder().id(request.tags().get(0)).type("category").value("updated").build();

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(userPrincipal, collection)).thenReturn(true); // Admin bypass
        when(tagRepository.findAllById(request.tags())).thenReturn(List.of(tag));
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);

        // Act
        UpdateCollectionResponse response = collectionService.updateCollection(collectionId, request, userPrincipal);

        // Assert
        assertNotNull(response);
        assertEquals(request.name(), response.getName());
        assertEquals(request.description(), response.getDescription());
        assertEquals(request.visibility(), response.getVisibility());
        verify(collectionTagRepository).deleteByCollectionId(collectionId);
        verify(collectionTagRepository).saveAll(anyList());
    }

    @Test
    void updateCollection_NonOwner_Teacher_ThrowsAccessDenied() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "Updated Collection",
                null,
                Visibility.PUBLIC.name(),
                List.of(UUID.randomUUID()),
                groupId
        );
        Collection collection = Collection.builder()
                .id(collectionId)
                .createdBy(UUID.randomUUID()) // Different user
                .isDeleted(false)
                .build();

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(userPrincipal, collection)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> collectionService.updateCollection(collectionId, request, userPrincipal));
    }

    //================================================================//
    //====================DELETE COLLECTION===========================//
    @Test
    void softDeleteCollection_Success_Owner() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        Collection collection = Collection.builder()
                .id(collectionId)
                .createdBy(userId)
                .isDeleted(false)
                .build();

        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);

        // Act
        collectionService.softDeleteCollection(collectionId, userPrincipal);

        // Assert
        verify(collectionRepository).save(argThat(col -> col.getIsDeleted() && col.getDeletedBy() == user));
    }

    @Test
    void softDeleteCollection_NonOwner_SchoolAdmin_Success() {
        // Arrange
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role("SCHOOL_ADMIN") // Uppercase role
                .schoolId(schoolId)
                .build();
        UUID collectionId = UUID.randomUUID();
        Collection collection = Collection.builder()
                .id(collectionId)
                .createdBy(UUID.randomUUID()) // Different user
                .isDeleted(false)
                .build();

        when(collectionRepository.findByIdAndIsDeletedFalse(collectionId)).thenReturn(Optional.of(collection));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);

        // Act
        collectionService.softDeleteCollection(collectionId, userPrincipal);

        // Assert
        verify(collectionRepository).save(any(Collection.class));
    }

    //================================================================//
    //======================LIST COLLECTION===========================//
    @Test
    void listMyCollections_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .name("My Collection")
                .description("Desc")
                .visibility(Visibility.PRIVATE.name())
                .createdBy(userId)
                .createdAt(Instant.now())
                .isDeleted(false)
                .build();
        Page<Collection> page = new PageImpl<>(List.of(collection), pageable, 1);

        when(collectionRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);
        when(collectionTagRepository.findByCollectionId(any(UUID.class))).thenReturn(List.of());

        // Act
        PageCollectionResponse response = collectionService.listMyCollections(userPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("My Collection", response.content().get(0).name());
    }

    @Test
    void listPublicCollections_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .name("Public Collection")
                .visibility(Visibility.PUBLIC.name())
                .createdAt(Instant.now())
                .isDeleted(false)
                .build();
        Page<Collection> page = new PageImpl<>(List.of(collection), pageable, 1);

        when(collectionRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc("public", pageable)).thenReturn(page);
        when(collectionTagRepository.findByCollectionId(any(UUID.class))).thenReturn(List.of());

        // Act
        PageCollectionResponse response = collectionService.listPublicCollections(pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Public Collection", response.content().get(0).name());
    }

    @Test
    void listAllCollections_Success_SchoolAdmin() {
        // Arrange
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role("SCHOOL_ADMIN") // Uppercase role
                .schoolId(schoolId)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .name("Collection")
                .visibility(Visibility.PUBLIC.name())
                .createdAt(Instant.now())
                .isDeleted(false)
                .build();
        Page<Collection> page = new PageImpl<>(List.of(collection), pageable, 1);

        when(collectionRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable)).thenReturn(page);
        when(collectionTagRepository.findByCollectionId(any(UUID.class))).thenReturn(List.of());

        // Act
        PageCollectionResponse response = collectionService.listAllCollections(userPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    void listAllCollections_Teacher_ThrowsAccessDenied() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> collectionService.listAllCollections(userPrincipal, pageable));
    }

    @Test
    void listAllCollectionsForAdmin_Success_SystemAdmin() {
        // Arrange
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.SYSTEM_ADMIN.name()) // Uppercase role
                .schoolId(schoolId)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .name("Deleted Collection")
                .visibility(Visibility.PUBLIC.name())
                .createdAt(Instant.now())
                .isDeleted(true)
                .build();
        Page<Collection> page = new PageImpl<>(List.of(collection), pageable, 1);

        when(collectionRepository.findAll(pageable)).thenReturn(page);
        when(collectionTagRepository.findByCollectionId(any(UUID.class))).thenReturn(List.of());

        // Act
        PageCollectionResponse response = collectionService.listAllCollectionsForAdmin(userPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    void listAllCollectionsForAdmin_SchoolAdmin_ThrowsAccessDenied() {
        // Arrange
        userPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.SCHOOL_ADMIN.name()) // Uppercase role
                .schoolId(schoolId)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> collectionService.listAllCollectionsForAdmin(userPrincipal, pageable));
    }
}

