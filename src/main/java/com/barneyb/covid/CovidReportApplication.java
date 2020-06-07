package com.barneyb.covid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

@SpringBootApplication
@ComponentScan(excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Store.class))
public class CovidReportApplication {

	public static void main(String[] args) {
		SpringApplication.run(CovidReportApplication.class, args);
	}

	@Bean
    public ObjectMapper objectMapper() {
        val module = new SimpleModule("LocalDateModule");
        module.addSerializer(LocalDate.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(LocalDate.class, new JsonDeserializer<>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return LocalDate.parse(p.getText());
            }
        });
        val mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        return mapper;
    }

    @Bean
    @Qualifier("us")
    public Store usStore() {
	    return new Store(Path.of("database-us.json"));
    }

    @Bean
    @Qualifier("worldwide")
    public Store worldwideStore() {
	    return new Store(Path.of("database-ww.json"));
    }

}
