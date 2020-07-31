package com.barneyb.covid;

import com.barneyb.covid.hopkins.DashboardBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${covid-report.output.pretty-print}")
    boolean outputPrettyPrint;

    @Value("${covid-report.output.dir}")
    Path outputDir;

    private SimpleModule jsonLocalDateModule() {
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
        return module;
    }

    @Bean
    JsonSerializer<Double> jsonDoubleSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNumber(String.format("%.2f", value));
            }
        };
    }

    private SimpleModule jsonHopkinsModule() {
        val module = new SimpleModule("HopkinsModule");
        val ds = jsonDoubleSerializer();
        module.addSerializer(Double.class, ds);
        module.addSerializer(double.class, ds);
        module.addSerializer(double[].class, new JsonSerializer<>() {
            @Override
            public void serialize(double[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartArray(value.length);
                for (double d : value) {
                    ds.serialize(d, gen, serializers);
                }
                gen.writeEndArray();
            }
        });
        module.addSerializer(DashboardBuilder.Segment.class, new JsonSerializer<>() {
            @Override
            public void serialize(DashboardBuilder.Segment value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartArray(3);
                gen.writeString(value.getName());
                gen.writeNumber(value.getPop());
                ds.serialize(value.getDelta(), gen, serializers);
                gen.writeEndArray();
            }
        });
        return module;
    }

    @Bean
    public ObjectMapper objectMapper() {
        val mapper = new ObjectMapper();
        mapper.registerModule(jsonLocalDateModule());
        mapper.registerModule(jsonHopkinsModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        return mapper;
    }

    @Bean
    public ObjectWriter objectWriter() {
        return outputPrettyPrint
            ? objectMapper().writerWithDefaultPrettyPrinter()
            : objectMapper().writer();
    }

    @Bean
    @Qualifier("worldwide")
    public Store worldwideStore() {
	    return new Store(outputDir.resolve("database-ww.json"));
    }

    @Bean
    @Qualifier("us")
    public Store usStore() {
        return new Store(outputDir.resolve("database-us.json"));
    }

    @Bean
    @Qualifier("or")
    public Store orStore() {
        return new Store(outputDir.resolve("database-or.json"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CovidReportApplication.class, args);
    }

}
