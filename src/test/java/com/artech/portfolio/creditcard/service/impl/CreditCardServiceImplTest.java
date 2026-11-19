package com.artech.portfolio.creditcard.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.creditcard.dto.CreditCardBillRequest;
import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;
import com.artech.portfolio.creditcard.dto.CreditCardView;
import com.artech.portfolio.creditcard.entity.CreditCard;
import com.artech.portfolio.creditcard.entity.CreditCardBill;
import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import com.artech.portfolio.creditcard.repository.CreditCardBillRepository;
import com.artech.portfolio.creditcard.repository.CreditCardPurchaseRepository;
import com.artech.portfolio.creditcard.repository.CreditCardRepository;
import com.artech.portfolio.creditcard.util.CreditCardConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MPC_V1 credit cards:
 *   #1  a cycle ends on the card's billing day and starts the day after the
 *       previous cycle ended (month end when the day is shorter),
 *   #2  the expense is planned in the month the cycle ends,
 *   #3  the entered bill wins and the purchases fill in until then,
 *   #4  a card of another client is never found.
 */
class CreditCardServiceImplTest {

    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

    private CreditCardRepository cardRepository;
    private CreditCardBillRepository billRepository;
    private CreditCardPurchaseRepository purchaseRepository;
    private CurrentUserService currentUserService;
    private CreditCardServiceImpl service;

    private Client owner;
    private final List<CreditCard> cards = new ArrayList<>();
    private final List<CreditCardBill> bills = new ArrayList<>();
    private final List<CreditCardPurchase> purchases = new ArrayList<>();
    private long nextCardId = 1;
    private long nextBillId = 1;
    private long nextPurchaseId = 1;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CreditCardRepository.class);
        billRepository = mock(CreditCardBillRepository.class);
        purchaseRepository = mock(CreditCardPurchaseRepository.class);
        currentUserService = mock(CurrentUserService.class);

        owner = new Client();
        owner.setId(7L);
        owner.setPan("OWNER0002B");
        when(currentUserService.requireClient()).thenReturn(owner);

        when(cardRepository.findByClientIdOrderByNameAsc(anyLong())).thenAnswer(inv -> cards);
        when(cardRepository.findByIdAndClientId(anyLong(), anyLong())).thenAnswer(inv -> cards.stream()
                .filter(card -> card.getId().equals(inv.getArgument(0))
                        && card.getClient() != null
                        && card.getClient().getId().equals(inv.getArgument(1)))
                .findFirst());
        when(cardRepository.findByClientIdAndNameIgnoreCase(anyLong(), anyString()))
                .thenAnswer(inv -> cards.stream()
                        .filter(card -> card.getName().equalsIgnoreCase(inv.getArgument(1)))
                        .findFirst());
        when(cardRepository.save(any(CreditCard.class))).thenAnswer(inv -> {
            CreditCard card = inv.getArgument(0);
            if (card.getId() == null) {
                card.setId(nextCardId++);
                cards.add(card);
            }
            return card;
        });

        when(billRepository.findByCardIdAndCycleEnd(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> bills.stream()
                        .filter(bill -> bill.getCard().getId().equals(inv.getArgument(0))
                                && bill.getCycleEnd().equals(inv.getArgument(1)))
                        .findFirst());
        when(billRepository.save(any(CreditCardBill.class))).thenAnswer(inv -> {
            CreditCardBill bill = inv.getArgument(0);
            bills.removeIf(existing -> existing.getCard().getId().equals(bill.getCard().getId())
                    && existing.getCycleEnd().equals(bill.getCycleEnd()));
            if (bill.getId() == null) {
                bill.setId(nextBillId++);
            }
            bills.add(bill);
            return bill;
        });

        when(purchaseRepository.findByCardIdAndPurchaseDateBetweenOrderByPurchaseDateAsc(
                anyLong(), any(LocalDate.class), any(LocalDate.class))).thenAnswer(inv -> purchases.stream()
                        .filter(purchase -> purchase.getCard().getId().equals(inv.getArgument(0)))
                        .filter(purchase -> !purchase.getPurchaseDate().isBefore(inv.getArgument(1))
                                && !purchase.getPurchaseDate().isAfter(inv.getArgument(2)))
                        .sorted(Comparator.comparing(CreditCardPurchase::getPurchaseDate))
                        .toList());
        when(purchaseRepository.save(any(CreditCardPurchase.class))).thenAnswer(inv -> {
            CreditCardPurchase purchase = inv.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId(nextPurchaseId++);
            }
            purchases.add(purchase);
            return purchase;
        });

        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new CreditCardServiceImpl(cardRepository, billRepository, purchaseRepository,
                currentUserService, clock);
    }

    // ------------------------------------------------------------- helpers

    private CreditCard card(String name, int billingCycleDay) {
        CreditCard card = new CreditCard();
        card.setId(nextCardId++);
        card.setClient(owner);
        card.setName(name);
        card.setBillingCycleDay(billingCycleDay);
        cards.add(card);
        return card;
    }

    private CreditCardBillRequest bill(String cycleEnd, double amount) {
        CreditCardBillRequest request = new CreditCardBillRequest();
        request.setCycleEnd(cycleEnd);
        request.setAmount(amount);
        return request;
    }

    private void purchase(CreditCard card, String date, double amount) {
        CreditCardPurchase purchase = new CreditCardPurchase();
        purchase.setId(nextPurchaseId++);
        purchase.setCard(card);
        purchase.setPurchaseDate(LocalDate.parse(date));
        purchase.setAmount(amount);
        purchases.add(purchase);
    }

    private static CreditCardView only(CreditCardMonthView view) {
        assertEquals(1, view.getCards().size());
        return view.getCards().get(0);
    }

    // ------------------------------------------------------- billing cycles

    @Test
    void aCycleEndsOnTheBillingDayAndStartsTheDayAfterThePreviousOne() {
        card("HDFC Millennia", 5);

        CreditCardView view = only(service.month("2026-11"));

        assertEquals(LocalDate.of(2026, 11, 5), view.getCycleEnd());
        assertEquals(LocalDate.of(2026, 10, 6), view.getCycleStart());
        assertEquals("6 Oct 2026 - 5 Nov 2026", view.getCycleLabel());
    }

    @Test
    void aCycleDayPastTheMonthEndEndsOnTheLastDay() {
        card("ICICI", 31);

        CreditCardView view = only(service.month("2026-02"));

        assertEquals(LocalDate.of(2026, 2, 28), view.getCycleEnd());
        assertEquals(LocalDate.of(2026, 2, 1), view.getCycleStart());
    }

    // ------------------------------------------------ #2/#3 the planned figure

    @Test
    void theEnteredBillIsPlannedInTheMonthItsCycleEnds() {
        CreditCard card = card("HDFC Millennia", 5);
        purchase(card, "2026-10-20", 3000);
        service.saveBill(card.getId(), bill("2026-11-05", 45230));

        CreditCardMonthView view = service.month("2026-11");
        CreditCardView cardView = only(view);

        assertTrue(cardView.isBillEntered());
        assertEquals(45230.0, cardView.getBillAmount());
        assertEquals(45230.0, cardView.getAmount(), "the bill of the November cycle");
        assertEquals(CreditCardConstants.AMOUNT_BILL, cardView.getAmountSource());
        assertEquals(45230.0, view.getTotal());
        assertEquals(1, cardView.getPurchases().size());

        // A purchase dated 20 October belongs to the cycle ending 5 November, not to October.
        assertEquals(0.0, service.monthTotal(owner.getId(), LocalDate.of(2026, 10, 1)));
        assertEquals(45230.0, service.monthTotal(owner.getId(), NOVEMBER));
    }

    @Test
    void withoutABillThePurchasesOfTheCycleArePlanned() {
        CreditCard card = card("HDFC Millennia", 5);
        purchase(card, "2026-10-20", 3000);
        purchase(card, "2026-11-02", 2000);
        purchase(card, "2026-11-14", 9000); // after the 5 November cycle end

        CreditCardMonthView view = service.month("2026-11");
        CreditCardView cardView = only(view);

        assertFalse(cardView.isBillEntered());
        assertEquals(5000.0, cardView.getPurchaseTotal());
        assertEquals(5000.0, cardView.getAmount());
        assertEquals(CreditCardConstants.AMOUNT_PURCHASES, cardView.getAmountSource());
        assertEquals(5000.0, service.monthTotal(owner.getId(), NOVEMBER),
                "the later purchase is billed in December");
    }

    @Test
    void aCardWithoutAnyFigurePlansNothing() {
        card("Axis", 12);

        CreditCardMonthView view = service.month("2026-11");

        assertEquals(0.0, only(view).getAmount());
        assertEquals(CreditCardConstants.AMOUNT_NONE, only(view).getAmountSource());
        assertEquals(0.0, view.getTotal());
    }

    // ------------------------------------------------------------- writing

    @Test
    void theBillingCycleDayCanBeChangedLater() {
        CreditCard card = card("HDFC Millennia", 5);
        service.saveBill(card.getId(), bill("2026-11-05", 1000));

        CreditCardRequest change = new CreditCardRequest();
        change.setId(card.getId());
        change.setName("HDFC Millennia");
        change.setBillingCycleDay(10);
        service.saveCard(change);

        assertEquals(10, only(service.month("2026-11")).getBillingCycleDay());
        assertEquals(LocalDate.of(2026, 11, 10), only(service.month("2026-11")).getCycleEnd());
        assertEquals(0.0, service.monthTotal(owner.getId(), NOVEMBER),
                "the old 5 November bill belongs to the old cycle");

        // The new bill is entered against the moved cycle, whatever day it is given.
        service.saveBill(card.getId(), bill("2026-11-09", 1200));
        assertEquals(1200.0, service.monthTotal(owner.getId(), NOVEMBER));
    }

    @Test
    void aPurchaseIsBilledInTheCycleOfItsOwnDate() {
        CreditCard card = card("HDFC Millennia", 5);

        CreditCardPurchaseRequest request = new CreditCardPurchaseRequest();
        request.setPurchaseDate("2026-11-04");
        request.setAmount(750.0);
        request.setDescription("Fuel");
        service.addPurchase(card.getId(), request);

        assertEquals(750.0, only(service.month("2026-11")).getAmount());
        assertEquals("Fuel", only(service.month("2026-11")).getPurchases().get(0).getDescription());
    }

    @Test
    void aCardNeedsANameAndAValidCycleDay() {
        CreditCardRequest noName = new CreditCardRequest();
        noName.setName("  ");
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.saveCard(noName)).getStatusCode());

        CreditCardRequest badDay = new CreditCardRequest();
        badDay.setName("HDFC");
        badDay.setBillingCycleDay(32);
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.saveCard(badDay)).getStatusCode());
    }

    @Test
    void aDuplicateCardNameIsRejected() {
        card("HDFC Millennia", 5);

        CreditCardRequest duplicate = new CreditCardRequest();
        duplicate.setName("hdfc millennia");
        duplicate.setBillingCycleDay(5);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> service.saveCard(duplicate)).getStatusCode());
    }

    @Test
    void aCardOfAnotherClientIsNeverFound() {
        CreditCard foreign = new CreditCard();
        foreign.setId(42L);
        Client other = new Client();
        other.setId(99L);
        foreign.setClient(other);
        foreign.setName("Not mine");
        foreign.setBillingCycleDay(5);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveBill(42L, bill("2026-11-05", 100)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertTrue(bills.isEmpty());
    }

    @Test
    void aPurchaseMustBePositive() {
        CreditCard card = card("HDFC Millennia", 5);
        CreditCardPurchaseRequest request = new CreditCardPurchaseRequest();
        request.setPurchaseDate("2026-11-01");
        request.setAmount(0.0);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> service.addPurchase(card.getId(), request)).getStatusCode());
    }

    @Test
    void monthParsingAcceptsBothShapes() {
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11"));
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11-23"));
        assertEquals(LocalDate.of(2026, 11, 1), service.firstOfMonth((String) null));
        assertEquals(Optional.empty(), Optional.ofNullable(service.dateOr("", null)));
    }
}
