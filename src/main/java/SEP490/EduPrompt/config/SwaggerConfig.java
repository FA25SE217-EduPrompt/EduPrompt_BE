package SEP490.EduPrompt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${open.api.title}") String titleDocument,
            @Value("${open.api.version}") String versionDocument,
            @Value("${open.api.description}") String descriptionDocument,
            @Value("${open.api.server.url}") String serverUrl,
            @Value("${open.api.server.description}") String serverDescription
    ) {
        return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components().addSecuritySchemes("Bearer Authentication", createAPIKeyScheme())).info(new Info()
                        .title(titleDocument)
                        .version(versionDocument)
                        .description(descriptionDocument)
                        .license(new License()
                                .name("Backend")
                                .url("https://github.com/somethinn/EduPrompt_BE")) // that's mine  :D
                ).servers(List.of(new Server()
                        .url(serverUrl)
                        .description(serverDescription)));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }

    @Bean
    public GroupedOpenApi groupedOpenApi(
            @Value("${open.api.group}") String group,
            @Value("${open.api.scan-package}") String scanPackage
    ) {
        return GroupedOpenApi.builder()
                .group(group)
                .packagesToScan(scanPackage)
                .build();
    }

}
