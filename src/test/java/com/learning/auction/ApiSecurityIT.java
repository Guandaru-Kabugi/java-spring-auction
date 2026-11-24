package com.learning.auction;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.learning.auction.support.IntegrationTest;

/** Checks the HTTP security rules with fake JWTs (no Keycloak needed). */
@AutoConfigureMockMvc
class ApiSecurityIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void browsingIsPublic() throws Exception {
        mvc.perform(get("/api/auctions/active")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void writingRequiresAToken() throws Exception {
        mvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreateAListing() throws Exception {
        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(jwtFor("seller-api")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nintendo Switch","category":"GAMING","condition":"USED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nintendo Switch"));
    }

    @Test
    void invalidBodyReturnsProblemDetail() throws Exception {
        mvc.perform(post("/api/listings")
                        .with(jwt().jwt(jwtFor("seller-invalid")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"GAMING\",\"condition\":\"USED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void adminEndpointsNeedAdminRole() throws Exception {
        mvc.perform(get("/api/admin/anything").with(jwt().jwt(jwtFor("plain-user"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/anything")
                        .with(jwt().jwt(jwtFor("admin-user")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
