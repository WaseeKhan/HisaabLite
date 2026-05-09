package com.expygen;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.web.resources.add-mappings=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStaticResourceMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCssStillLoadsWhenDefaultMappingsAreDisabled() throws Exception {
        mockMvc.perform(get("/admin/css/admin-base.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void adminJsStillLoadsWhenDefaultMappingsAreDisabled() throws Exception {
        mockMvc.perform(get("/admin/js/admin-ui.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
    }
}
