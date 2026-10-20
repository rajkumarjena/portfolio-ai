package com.artech.portfolio.client.controller;

import com.artech.portfolio.client.dto.ClientSearchResult;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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

    /**
     * BONDBP_V5 #4.b: type-ahead behind the "Add Family Member" drop-down.
     * Matches client name OR PAN; rejects queries shorter than 3 characters.
     * forClientId = the client whose panel is open - himself and his current
     * family members are excluded from the results.
     */
    @GetMapping("/search")
    public List<ClientSearchResult> searchClients(@RequestParam String q,
                                                  @RequestParam(required = false) Long forClientId) {
        return clientService.searchClients(q, forClientId);
    }

    /**
     * BONDBP_V5 #4.d "Add": confirm the association of a previously searched
     * and selected registered client as family member of {id}.
     * Body: {"memberId": <client id>}
     */
    @PostMapping("/{id}/family-members")
    public Client addFamilyMember(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long memberId = body == null ? null : body.get("memberId");
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memberId is required");
        }
        return clientService.addFamilyMember(id, memberId);
    }

    /**
     * BONDBP_V5 #4.d "Delete": de-associate a family member that was added earlier.
     */
    @DeleteMapping("/{id}/family-members/{memberId}")
    public Client removeFamilyMember(@PathVariable Long id, @PathVariable Long memberId) {
        return clientService.removeFamilyMember(id, memberId);
    }

    /**
     * Server-side validation reasons (search minimum length, self/duplicate
     * association, contact formats, ...) return {"message": ...} so the
     * UI - current or future Angular/React - can show exactly what failed.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }
}
