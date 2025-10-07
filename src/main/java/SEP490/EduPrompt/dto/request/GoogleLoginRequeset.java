package SEP490.EduPrompt.dto.request;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
public class GoogleLoginRequeset {
    private String tokenId;
}
