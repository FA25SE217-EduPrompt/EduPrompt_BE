package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.CreateTagBatchRequest;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.model.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TagService {
    List<TagResponse> createBatch(CreateTagBatchRequest request);

    Page<TagResponse> filterTags(List<String> types, Pageable pageable);

    List<Tag> findAllByIdIn(List<java.util.UUID> ids);

}
