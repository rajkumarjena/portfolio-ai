package com.artech.portfolio.client.service.impl;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.client.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ClientServiceImpl implements ClientService {
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

        return clientRepository.save(client);
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
