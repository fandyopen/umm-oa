package com.seal.oa.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class HrWorkLocations {
    public record Province(String name, List<String> cities) {}
    private final List<Province> provinces;

    public HrWorkLocations(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("hr-work-locations.json").getInputStream()) {
            provinces = mapper.readValue(input, new TypeReference<List<Province>>() {});
        }
    }

    public List<Province> options() { return provinces; }

    public void validate(String province, String city) {
        if (provinces.stream().noneMatch(p -> p.name().equals(province) && p.cities().contains(city)))
            throw new ApiException(400, "请选择有效的工作地省份和所属城市");
    }
}
