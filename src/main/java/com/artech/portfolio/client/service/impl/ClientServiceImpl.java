package com.artech.portfolio.client.service.impl;

import com.artech.portfolio.client.dto.ClientSearchResult;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.client.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClientServiceImpl implements ClientService {

    /**
     * BONDBP_V5 #4.b: the backend is only hit once the user has typed at least
     * three characters in the search drop-down.
     */
    private static final int MIN_CLIENT_SEARCH_CHARS = 3;

    /** BONDBP_V5 #5: cap so the type-ahead stays cheap on a huge client table. */
    private static final int MAX_CLIENT_SEARCH_RESULTS = 20;

    private final ClientRepository clientRepository;

    public ClientServiceImpl(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public Client saveClient(Client client) {
        normalize(client);
        validate(client);

        // A client is identified by PAN - PAN must be unique
        if (!clientRepository.findByPan(client.getPan()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A client with PAN " + client.getPan() + " already exists");
        }

        // BONDBP_V5 #4: family members are associations with other registered
        // clients and are managed exclusively through the /family-members
        // endpoints - never accepted from a generic save payload.
        client.setFamilyMembers(new ArrayList<>());
        bindChildren(client);
        return clientRepository.save(client);
    }

    @Override
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    @Override
    public Client getClientById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found with id " + id));
    }

    @Override
    public Client getClientByPan(String pan) {
        List<Client> clients = clientRepository.findByPan(normalizePan(pan));
        if (clients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Client not found with PAN " + pan);
        }
        return clients.get(0);
    }

    @Override
    public Client updateClient(Long id, Client clientDetails) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found with id " + id));

        // Name and PAN are immutable - incoming values are ignored (CLIENT.md rule)
        // New mobile numbers / emails / bank accounts are appended; existing ones are kept.
        if (clientDetails.getMobileNumbers() != null) {
            clientDetails.getMobileNumbers().stream()
                    .filter(m -> m.getId() == null)
                    .forEach(client::addMobile);
        }
        if (clientDetails.getEmails() != null) {
            clientDetails.getEmails().stream()
                    .filter(e -> e.getId() == null)
                    .forEach(client::addEmail);
        }
        if (clientDetails.getBankAccounts() != null) {
            clientDetails.getBankAccounts().stream()
                    .filter(b -> b.getId() == null)
                    .forEach(client::addBankAccount);
        }
        // BONDBP_V5 #4: incoming familyMembers are deliberately NOT bound here.
        // Associations are only created/removed through addFamilyMember /
        // removeFamilyMember so every rule (existing client, no self, no
        // duplicate) is enforced server-side in one place.

        return clientRepository.save(client);
    }

    @Override
    public List<ClientSearchResult> searchClients(String query, Long forClientId) {
        String q = query == null ? "" : query.trim();
        // BONDBP_V5 #4.b - enforced on the server, not only in the browser.
        if (q.length() < MIN_CLIENT_SEARCH_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum " + MIN_CLIENT_SEARCH_CHARS + " characters required to search clients");
        }
        List<Client> matches = clientRepository.searchByNameOrPan(
                q, forClientId, MAX_CLIENT_SEARCH_RESULTS);
        return matches.stream().map(this::toSearchResult).toList();
    }

    @Override
    public Client addFamilyMember(Long clientId, Long memberClientId) {
        Client owner = getClientById(clientId);
        Client member = clientRepository.findById(memberClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found with id " + memberClientId));

        // BONDBP_V5 #4.a: the member must be an existing registered client and
        // cannot be the client himself.
        if (owner.getId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A client cannot be their own family member");
        }
        boolean alreadyAssociated = owner.getFamilyMembers().stream()
                .anyMatch(link -> link.getMember() != null
                        && link.getMember().getId().equals(member.getId()));
        if (alreadyAssociated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    member.getName() + " is already a family member of " + owner.getName());
        }

        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        owner.addFamilyMember(link); // also binds the owner back-reference
        return clientRepository.save(owner);
    }

    @Override
    public Client removeFamilyMember(Long clientId, Long memberClientId) {
        Client owner = getClientById(clientId);
        ClientFamilyMember link = owner.getFamilyMembers().stream()
                .filter(l -> l.getMember() != null && l.getMember().getId().equals(memberClientId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client " + memberClientId + " is not a family member of " + owner.getName()));
        owner.getFamilyMembers().remove(link); // orphanRemoval deletes the row
        return clientRepository.save(owner);
    }

    private ClientSearchResult toSearchResult(Client client) {
        ClientSearchResult result = new ClientSearchResult();
        result.setId(client.getId());
        result.setName(client.getName());
        result.setPan(client.getPan());
        if (client.getEmails() != null) {
            client.getEmails().forEach(e -> result.getEmails().add(e.getEmail()));
        }
        if (client.getMobileNumbers() != null) {
            client.getMobileNumbers().forEach(m -> result.getMobileNumbers().add(m.getMobileNumber()));
        }
        return result;
    }

    private void normalize(Client client) {
        if (client.getName() != null) {
            client.setName(client.getName().trim());
        }
        client.setPan(normalizePan(client.getPan()));
    }

    private String normalizePan(String pan) {
        return pan == null ? null : pan.trim().toUpperCase();
    }

    private void validate(Client client) {
        if (client.getName() == null || client.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client name is required");
        }
        if (client.getPan() == null || client.getPan().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAN is required");
        }
    }

    private void bindChildren(Client client) {
        // Crucial Mapping Hook: explicitly bind the parent instance to every incoming child.
        // The children arrive already collected inside the parent's own lists (Jackson
        // deserialization), so we only set the back-reference here - re-adding them through
        // the add* helpers would structurally modify the list being iterated and throw
        // ConcurrentModificationException.
        if (client.getMobileNumbers() != null) {
            client.getMobileNumbers().forEach(mobile -> mobile.setClient(client));
        }
        if (client.getEmails() != null) {
            client.getEmails().forEach(email -> email.setClient(client));
        }
        if (client.getBankAccounts() != null) {
            client.getBankAccounts().forEach(bankAccount -> bankAccount.setClient(client));
        }
    }
}
