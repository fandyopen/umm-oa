package com.seal.oa;

import com.seal.oa.compat.CompatController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompatControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompatController()).build();

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void missingEndpointsNeverReportSuccess(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/missing/record"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(header().string("X-UMM-OA-Compatible", "fallback"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/missing/list", "/missing/tree", "/missing/export", "/missing/page", "/system/dept/missing"})
    void missingListsAndExportsDoNotPretendToBeEmptyData(String path) throws Exception {
        mvc.perform(request(HttpMethod.GET, path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.rows").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
