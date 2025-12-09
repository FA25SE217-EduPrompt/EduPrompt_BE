package SEP490.EduPrompt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Duration;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final RedisTemplate<String, String> redisTemplate;
    private final SpringTemplateEngine templateEngine;
//    private final GeminiClientService service;

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

    @GetMapping("/preview/verification")
    public String previewVerificationEmail(
            @RequestParam(defaultValue = "Test User") String name,
            @RequestParam(defaultValue = "justtestingemailfromlol_dontaskwhy:)") String verificationLink
    ) {
        Context context = new Context();
        context.setVariable("appName", "EduPrompt");
        context.setVariable("name", name);
        context.setVariable("verificationLink", verificationLink);

        return templateEngine.process("account-verification-email", context);
    }

//    @GetMapping("/get-file")
//    public String testGetFile(@RequestParam String fileName) {
//        String name = fileName == null ? "files/r2zn0sr9aeg3" : fileName;
//        File file = service.getFile(name);
//        return file.toJson();
//    }
//
//    @PostMapping("/store")
//    public String createStore() {
//        FileSearchStore store = service.createPromptStore("eduprompt");
//        return store.toJson();
//    }
//    @PostMapping("/import-file")
//    public String importFileToStore(@RequestParam String fileName) {
//        String name = fileName == null ? "files/r2zn0sr9aeg3" : fileName;
//        return service.importFileToStore(name);
//    }
}
