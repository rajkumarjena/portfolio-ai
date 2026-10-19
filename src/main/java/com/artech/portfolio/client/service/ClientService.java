package com.artech.portfolio.client.service;

import com.artech.portfolio.client.dto.ClientSearchResult;
import com.artech.portfolio.client.entity.Client;

import java.util.List;

public interface ClientService {
    Client saveClient(Client client);

    List<Client> getAllClients();

    Client getClientById(Long id);

    Client getClientByPan(String pan);

    // Deliberately no deleteClient: clients are never deleted (CLIENT.md rule)
    Client updateClient(Long id, Client clientDetails);

    /** BONDBP_V5 #4.b: type-ahead for "Add Family Member" (min 3 chars, name or PAN). */
    List<ClientSearchResult> searchClients(String query, Long forClientId);

    /** BONDBP_V5 #4.d "Add": associate an existing registered client as family member. */
    Client addFamilyMember(Long clientId, Long memberClientId);

    /** BONDBP_V5 #4.d "Delete": de-associate a family member added earlier. */
    Client removeFamilyMember(Long clientId, Long memberClientId);
}
