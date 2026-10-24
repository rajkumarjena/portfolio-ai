package com.artech.portfolio.client.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.dto.ClientSearchResult;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientEmail;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.client.entity.ClientMobile;
import com.artech.portfolio.client.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BONDBP_V5 #4: a family member is always an EXISTING registered client.
 *   4.b  the type-ahead hits the backend only from 3 characters (name or PAN),
 *   4.d  Add / Delete associate and de-associate, never typed-in details.
 * All rules live server-side (#5) so any front-end inherits them.
 */
class ClientFamilyMemberTest {

    private ClientRepository clientRepository;
    private CurrentUserService currentUserService;
    private ClientServiceImpl clientService;

    private Client owner;
    private Client member;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        currentUserService = mock(CurrentUserService.class);
        clientService = new ClientServiceImpl(clientRepository, currentUserService);

        owner = new Client();
        owner.setId(1L);
        owner.setName("Ramesh Kumar");
        owner.setPan("ABCDE1234F");

        member = new Client();
        member.setId(2L);
        member.setName("Sunita Kumar");
        member.setPan("QRSXY5678Z");
    }

    // ------------------------------------------------------------- 4.b search

    @Test
    void searchRejectsQueriesShorterThanThreeCharacters() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> clientService.searchClients("ab", 1L));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("Minimum 3 characters required to search clients", ex.getReason());
        verify(clientRepository, never()).searchByNameOrPan(any(), any(), anyInt());
    }

    @Test
    void searchTrimsQueryPassesOwnerAndCapsResultPage() {
        when(clientRepository.searchByNameOrPan(eq("kum"), eq(1L), eq(20)))
                .thenReturn(List.of(member));

        List<ClientSearchResult> results = clientService.searchClients("  kum  ", 1L);

        verify(clientRepository).searchByNameOrPan(eq("kum"), eq(1L), eq(20));
        assertEquals(1, results.size());
    }

    @Test
    void searchResultCarriesNamePanAndContactDetailsForTheReadOnlyPanel() {
        ClientEmail email = new ClientEmail();
        email.setEmail("sunita@example.com");
        member.setEmails(new ArrayList<>(List.of(email)));
        ClientMobile mobile = new ClientMobile();
        mobile.setMobileNumber("9876543210");
        member.setMobileNumbers(new ArrayList<>(List.of(mobile)));
        when(clientRepository.searchByNameOrPan(eq("sunita"), any(), anyInt()))
                .thenReturn(List.of(member));

        ClientSearchResult result = clientService.searchClients("sunita", 1L).get(0);

        assertEquals(2L, result.getId());
        assertEquals("Sunita Kumar", result.getName());
        assertEquals("QRSXY5678Z", result.getPan());
        assertEquals(List.of("sunita@example.com"), result.getEmails());
        assertEquals(List.of("9876543210"), result.getMobileNumbers());
    }

    // --------------------------------------------------------- 4.d add/delete

    @Test
    void addAssociatesAnExistingRegisteredClient() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(member));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client saved = clientService.addFamilyMember(1L, 2L);

        assertEquals(1, saved.getFamilyMembers().size());
        ClientFamilyMember link = saved.getFamilyMembers().get(0);
        assertEquals(2L, link.getMember().getId());
        assertSame(owner, link.getClient(), "owner back-reference is bound");
        verify(clientRepository).save(owner);
    }

    @Test
    void addRejectsSelfAssociation() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> clientService.addFamilyMember(1L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("A client cannot be their own family member", ex.getReason());
        assertTrue(owner.getFamilyMembers().isEmpty());
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void addRejectsDuplicateAssociation() {
        ClientFamilyMember existing = new ClientFamilyMember();
        existing.setMember(member);
        owner.addFamilyMember(existing);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(member));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> clientService.addFamilyMember(1L, 2L));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(1, owner.getFamilyMembers().size(), "no duplicate link added");
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void addRejectsUnknownMemberThatIsNotRegistered() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> clientService.addFamilyMember(1L, 99L));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void removeDeAssociatesAnEarlierAddedFamilyMember() {
        ClientFamilyMember existing = new ClientFamilyMember();
        existing.setMember(member);
        owner.addFamilyMember(existing);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client saved = clientService.removeFamilyMember(1L, 2L);

        assertTrue(saved.getFamilyMembers().isEmpty(), "orphanRemoval drops the link");
        verify(clientRepository).save(owner);
    }

    @Test
    void removeRejectsClientThatIsNotAFamilyMember() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> clientService.removeFamilyMember(1L, 2L));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
        verify(clientRepository, never()).save(any(Client.class));
    }

    // --------------------------------------- ADMIN_V5 #1 client-view scoping

    @Test
    void adminSeesEveryClientOnTheClientViewScreen() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(clientRepository.findAll()).thenReturn(List.of(owner, member));

        List<Client> visible = clientService.getAllClients();

        assertEquals(2, visible.size());
        verify(clientRepository).findAll();
    }

    @Test
    void userOrSupervisorSeesOnlyThemselvesOnTheClientViewScreen() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.currentClient()).thenReturn(owner);

        List<Client> visible = clientService.getAllClients();

        assertEquals(List.of(owner), visible, "ADMIN_V5 #1.a: self only");
        verify(clientRepository, never()).findAll();
    }

    // ------------------------------------------ generic save/update stay safe

    @Test
    void saveClientDropsClientSuppliedFamilyMembers() {
        ClientFamilyMember supplied = new ClientFamilyMember();
        supplied.setMember(member); // even a well-formed link must not sneak in
        Client newClient = new Client();
        newClient.setName("New Person");
        newClient.setPan("NEWPA1111A");
        newClient.addFamilyMember(supplied);
        when(clientRepository.findByPan(any())).thenReturn(List.of());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client saved = clientService.saveClient(newClient);

        assertTrue(saved.getFamilyMembers().isEmpty(),
                "associations are created only through POST /family-members");
    }

    @Test
    void updateClientIgnoresIncomingFamilyMembersAndKeepsExistingOnes() {
        ClientFamilyMember existing = new ClientFamilyMember();
        existing.setMember(member);
        owner.addFamilyMember(existing);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client incoming = new Client();
        incoming.setName("Ramesh Kumar");           // immutable anyway
        incoming.setPan("ABCDE1234F");
        ClientFamilyMember bogus = new ClientFamilyMember();
        incoming.addFamilyMember(bogus);

        Client updated = clientService.updateClient(1L, incoming);

        assertEquals(1, updated.getFamilyMembers().size(), "pre-existing association kept");
        assertSame(member, updated.getFamilyMembers().get(0).getMember());
    }
}
