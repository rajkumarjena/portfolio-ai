package com.artech.portfolio.client.service;

import com.artech.portfolio.client.entity.Client;

import java.util.List;

public interface ClientService {
    Client saveClient(Client client);

    List<Client> getAllClients();

    Client getClientById(Long id);

    Client getClientByPan(String pan);

    // Deliberately no deleteClient: clients are never deleted (CLIENT.md rule)
    Client updateClient(Long id, Client clientDetails);
}
