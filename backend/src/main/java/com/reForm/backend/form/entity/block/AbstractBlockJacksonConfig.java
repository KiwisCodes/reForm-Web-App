package com.reForm.backend.form.entity.block;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

// Registers AbstractBlockDeserializer via module binding (exact-class only), not the
// @JsonDeserialize class-level annotation (inherited by subclasses — see AbstractBlock's comment
// for why that broke StaticBlock's own dispatch). Spring Boot auto-detects JacksonModule beans and
// registers them onto the application's shared ObjectMapper.
@Configuration
public class AbstractBlockJacksonConfig {

    @Bean
    public JacksonModule abstractBlockModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(AbstractBlock.class, new AbstractBlockDeserializer());
        return module;
    }
}
