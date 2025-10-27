package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.generic.ExceptionCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
public class QuotaExceededException extends BaseException {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private final QuotaType quotaType;
    private final Instant resetDate;
    private final Integer remainingQuota;

    public QuotaExceededException(QuotaType quotaType, Instant resetDate, Integer remainingQuota) {
        super(
                ExceptionCode.QUOTA_EXCEEDED.name(),
                String.format("%s quota exceeded. Resets at %s. Remaining: %d",
                        quotaType.name(),
                        FORMATTER.format(resetDate),
                        remainingQuota),
                HttpStatus.TOO_MANY_REQUESTS
        );
        this.quotaType = quotaType;
        this.resetDate = resetDate;
        this.remainingQuota = remainingQuota;
    }
}

