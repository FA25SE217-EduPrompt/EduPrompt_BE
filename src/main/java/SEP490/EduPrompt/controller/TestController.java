package SEP490.EduPrompt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/redis")
    public String testRedis() {
        redisTemplate.opsForValue().set("test-key", "test-value", Duration.ofMinutes(1));
        String value = redisTemplate.opsForValue().get("test-key");
        return "Redis finally working! Fuck it we ball! : " + value;
    }

    @GetMapping
    public String test() {
        return "Lord Tri Nguyen";
    }


}
