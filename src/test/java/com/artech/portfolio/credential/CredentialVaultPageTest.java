package com.artech.portfolio.credential;

import com.artech.portfolio.auth.dto.CurrentUserView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRED_V1 - the page is a Thymeleaf template, so a mistake in it would only
 * surface when a signed-in user opened it. Rendering it here proves the shared
 * layout fragment resolves (including its @{...} links) and that all three
 * panels are on the page.
 */
@SpringBootTest
class CredentialVaultPageTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void theVaultPageRendersEveryPanel() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("currentUser", new CurrentUserView("Test User", "ABCDE1234F", "USER", false));

        MockServletContext servletContext = new MockServletContext();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, Locale.getDefault(), variables);

        String html = templateEngine.process("app-credential-vault", context);

        assertTrue(html.contains("Credential Vault"), "the page title");
        assertTrue(html.contains("id=\"vaultSetupPanel\""), "Create Vault panel");
        assertTrue(html.contains("id=\"vaultLockPanel\""), "Unlock Vault panel");
        assertTrue(html.contains("id=\"vaultMainPanel\""), "credential listing panel");
        assertTrue(html.contains("id=\"vaultVerifyModal\""), "Master Password verification dialog");
        assertTrue(html.contains("/js/modules/credential-vault.js"), "page script");
        assertFalse(html.contains("${"), "every Thymeleaf expression was resolved");
    }
}
