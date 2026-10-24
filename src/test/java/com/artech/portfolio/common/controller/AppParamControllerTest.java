package com.artech.portfolio.common.controller;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.common.repository.AppParamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADMIN_V6 #3/#5: client_id is mandatory and always the signed-in client's id,
 * never a value from the request body; #2: getConfig() serves the cached list.
 */
class AppParamControllerTest {

    private AppParamRepository appParamRepository;
    private CurrentUserService currentUserService;
    private AppParamController controller;

    @BeforeEach
    void setUp() {
        appParamRepository = mock(AppParamRepository.class);
        currentUserService = mock(CurrentUserService.class);
        controller = new AppParamController(appParamRepository, currentUserService);
    }

    @Test
    void saveBindsTheSignedInClientIdIgnoringTheRequestBody() {
        Client me = new Client();
        me.setId(7L);
        when(currentUserService.requireClient()).thenReturn(me);
        when(appParamRepository.save(any(AppParamConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AppParamConfig payload = new AppParamConfig();
        payload.setInvestmentType("fd");
        payload.setParamName("bank");
        payload.setParamValue("Kotak Bank");
        payload.setClientId(999L); // forged client id in the body must be ignored
        payload.setId(42L);        // a body id must not turn a save into an update

        AppParamConfig saved = controller.saveBond(payload);

        assertEquals(7L, saved.getClientId(),
                "ADMIN_V6 #5: client_id is the logged-in client's id");
        assertEquals(null, saved.getId());
        verify(appParamRepository).save(payload);
    }

    @Test
    void getConfigServesTheCachedListWithoutARepositoryHit() {
        AppParamConfig row = new AppParamConfig();
        row.setClientId(7L);
        row.setInvestmentType("bond");
        row.setParamName("issuer");
        when(appParamRepository.findAll()).thenReturn(List.of(row));

        controller.refreshConfig();

        List<AppParamConfig> config = controller.getConfig();

        assertEquals(1, config.size());
        assertEquals("issuer", config.get(0).getParamName());
    }
}
