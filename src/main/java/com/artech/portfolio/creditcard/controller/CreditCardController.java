package com.artech.portfolio.creditcard.controller;

import com.artech.portfolio.creditcard.dto.CreditCardBillRequest;
import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;
import com.artech.portfolio.creditcard.service.CreditCardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * MPC_V1 - the credit card popup's API. Every call returns the refreshed month,
 * so the popup and the planner never disagree about a figure; ownership and all
 * cycle arithmetic live in the service.
 */
@RestController
@RequestMapping("/api/credit-cards")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    /** Every card with the billing cycle that ends in ?month=yyyy-MM. */
    @GetMapping
    public CreditCardMonthView month(
            @RequestParam(value = "month", required = false) String month) {
        return creditCardService.month(month);
    }

    /** Add a card. */
    @PostMapping
    public CreditCardMonthView create(@RequestBody CreditCardRequest request,
                                      @RequestParam(value = "month", required = false) String month) {
        creditCardService.saveCard(request);
        return creditCardService.month(month);
    }

    /** Edit a card - its name and its billing-cycle day. */
    @PutMapping("/{id}")
    public CreditCardMonthView update(@PathVariable("id") Long id,
                                      @RequestBody CreditCardRequest request,
                                      @RequestParam(value = "month", required = false) String month) {
        request.setId(id);
        creditCardService.saveCard(request);
        return creditCardService.month(month);
    }

    /** Remove a card together with its bills and purchases. */
    @DeleteMapping("/{id}")
    public CreditCardMonthView delete(@PathVariable("id") Long id,
                                      @RequestParam(value = "month", required = false) String month) {
        creditCardService.deleteCard(id);
        return creditCardService.month(month);
    }

    /** Enter the bill amount of one billing cycle. */
    @PutMapping("/{id}/bills")
    public CreditCardMonthView saveBill(@PathVariable("id") Long id,
                                        @RequestBody CreditCardBillRequest request,
                                        @RequestParam(value = "month", required = false) String month) {
        creditCardService.saveBill(id, request);
        return creditCardService.month(month);
    }

    /** Capture a purchase; its date decides the cycle it is billed in. */
    @PostMapping("/{id}/purchases")
    public CreditCardMonthView addPurchase(
            @PathVariable("id") Long id,
            @RequestBody CreditCardPurchaseRequest request,
            @RequestParam(value = "month", required = false) String month) {
        creditCardService.addPurchase(id, request);
        return creditCardService.month(month);
    }

    @DeleteMapping("/purchases/{purchaseId}")
    public CreditCardMonthView deletePurchase(
            @PathVariable("purchaseId") Long purchaseId,
            @RequestParam(value = "month", required = false) String month) {
        creditCardService.deletePurchase(purchaseId);
        return creditCardService.month(month);
    }

    /** Validation errors surface as a clean 400, a foreign card as 404. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() == null ? "Request failed" : ex.getMessage()));
    }
}
