package SEP490.EduPrompt.service.collection;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CollectionService {

    CreateCollectionResponse createCollection(CreateCollectionRequest req, UserPrincipal currentUser);

    UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest request, UserPrincipal currentUser);

    void softDeleteCollection(UUID id, UserPrincipal currentUser);

    PageCollectionResponse listMyCollections(UserPrincipal currentUser, Pageable pageable);

    PageCollectionResponse listPublicCollections(Pageable pageable);

    PageCollectionResponse listAllCollections(UserPrincipal currentUser, Pageable pageable);

    PageCollectionResponse listAllCollectionsForAdmin(UserPrincipal currentUser, Pageable pageable);

    long countMyCollections(UserPrincipal currentUser);

}
