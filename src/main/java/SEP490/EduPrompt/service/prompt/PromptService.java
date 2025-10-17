package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.service.auth.UserPrincipal;

public interface PromptService {

    PromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    PromptResponse createPromptInCollection(CreatePromptRequest dto, UserPrincipal currentUser);

}
