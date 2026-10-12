package com.artech.portfolio.client.controller;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.service.ClientService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public Client saveClient(@RequestBody Client client) {
        return clientService.saveClient(client);
    }

    @GetMapping
    public List<Client> getAllClients() {
        return clientService.getAllClients();
    }

    @GetMapping("/{id}")
    public Client getClientById(@PathVariable Long id) {
        return clientService.getClientById(id);
    }

    // A client is identified by his PAN number
    @GetMapping("/pan/{pan}")
    public Client getClientByPan(@PathVariable String pan) {
        return clientService.getClientByPan(pan);
    }

    // Name and PAN cannot be changed; new mobiles/emails/bank accounts are appended
    @PutMapping("/{id}")
    public Client updateClient(@PathVariable Long id,
                               @RequestBody Client clientDetails) {
        return clientService.updateClient(id, clientDetails);
    }

    // Deliberately NO delete endpoint: client configuration is never deleted (CLIENT.md rule 3)
}
