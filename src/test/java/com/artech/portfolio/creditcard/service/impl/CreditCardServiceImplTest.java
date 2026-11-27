package com.artech.portfolio.creditcard.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseView;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;
import com.artech.portfolio.creditcard.dto.CreditCardTransactionRequest;
import com.artech.portfolio.creditcard.dto.CreditCardView;
import com.artech.portfolio.creditcard.entity.CreditCard;
import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import com.artech.portfolio.creditcard.repository.CreditCardPurchaseRepository;
import com.artech.portfolio.creditcard.repository.CreditCardRepository;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MPC_V1 + MP_V5 credit cards:
 *   #1  a cycle ends on the card's billing day and starts the day after the
 *       previous cycle ended (month end when the day is shorter),
 *   #2  a transaction is billed in the month of its cycle end plus the grace
 *       period, movable only when that lands in the next month,
 *   #3  the planner month's figure is the sum of the transactions billed in it,
 *   #4  a card/purchase of another client is never found,
 *   #5  only the current and the previous month's transactions can be removed.
 */
class CreditCardServiceImplTest {

    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
    private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 1);

    private CreditCardRepository cardRepository;
    private CreditCardPurchaseRepository purchaseRepository;
    private CurrentUserService currentUserService;
    private CreditCardServiceImpl service;

    private Client owner;
    private final List<CreditCard> cards = new ArrayList<>();
    private final List<CreditCardPurchase> purchases = new ArrayList<>();
    private long nextCardId = 1;
    private long nextPurchaseId = 1;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CreditCardRepository.class);
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

        when(purchaseRepository.findByCardIdOrderByPurchaseDateAsc(anyLong()))
                .thenAnswer(inv -> purchases.stream()
                        .filter(purchase -> purchase.getCard().getId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(CreditCardPurchase::getPurchaseDate))
                        .toList());
        when(purchaseRepository.findByCard_Client_IdOrderByPurchaseDateAsc(anyLong()))
                .thenAnswer(inv -> purchases.stream()
                        .filter(purchase -> purchase.getCard() != null
                                && purchase.getCard().getClient() != null
                                && purchase.getCard().getClient().getId()
                                .equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(CreditCardPurchase::getPurchaseDate))
                        .toList());
        when(purchaseRepository.findById(anyLong())).thenAnswer(inv -> purchases.stream()
                .filter(purchase -> purchase.getId().equals(inv.getArgument(0)))
                .findFirst());
        when(purchaseRepository.save(any(CreditCardPurchase.class))).thenAnswer(inv -> {
            CreditCardPurchase purchase = inv.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId(nextPurchaseId++);
            }
            if (!purchases.contains(purchase)) {
                purchases.add(purchase);
            }
            return purchase;
        });
        doAnswer(inv -> purchases.remove(inv.getArgument(0)))
                .when(purchaseRepository).delete(any(CreditCardPurchase.class));

        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new CreditCardServiceImpl(cardRepository, purchaseRepository,
                currentUserService, clock);
    }

    // ------------------------------------------------------------- helpers

    private CreditCard card(String name, int billingCycleDay, int gracePeriodDays) {
        CreditCard card = new CreditCard();
        card.setId(nextCardId++);
        card.setClient(owner);
        card.setName(name);
        card.setBillingCycleDay(billingCycleDay);
        card.setGracePeriodDays(gracePeriodDays);
        cards.add(card);
        return card;
    }

    private CreditCardPurchase purchase(CreditCard card, String date, double amount) {
        CreditCardPurchase purchase = new CreditCardPurchase();
        purchase.setId(nextPurchaseId++);
        purchase.setCard(card);
        purchase.setPurchaseDate(LocalDate.parse(date));
        purchase.setAmount(amount);
        purchases.add(purchase);
        return purchase;
    }

    private static CreditCardPurchaseRequest purchaseRequest(String date, double amount) {
        CreditCardPurchaseRequest request = new CreditCardPurchaseRequest();
        request.setPurchaseDate(date);
        request.setAmount(amount);
        request.setDescription("Fuel");
        return request;
    }

    private static CreditCardView only(CreditCardMonthView view) {
        assertEquals(1, view.getCards().size());
        return view.getCards().get(0);
    }

    /** A service whose "today" is another day, for the clock-sensitive rules. */
    private CreditCardServiceImpl serviceAt(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new CreditCardServiceImpl(cardRepository, purchaseRepository,
                currentUserService, clock);
    }

    private static CreditCardTransactionRequest move(Long id, String month) {
        CreditCardTransactionRequest request = new CreditCardTransactionRequest();
        request.setPurchaseId(id);
        request.setBillMonth(month);
        return request;
    }

    // ------------------------------------------------------- billing cycles

    @Test
    void aCycleEndsOnTheBillingDayAndStartsTheDayAfterThePreviousOne() {
        card("HDFC Millennia", 5, 0);

        CreditCardView view = only(service.month("2026-11"));

        assertEquals(LocalDate.of(2026, 11, 5), view.getCycleEnd());
        assertEquals(LocalDate.of(2026, 10, 6), view.getCycleStart());
        assertEquals("6 Oct 2026 - 5 Nov 2026", view.getCycleLabel());
    }

    @Test
    void aCycleDayPastTheMonthEndEndsOnTheLastDay() {
        card("ICICI", 31, 0);

        CreditCardView view = only(service.month("2026-02"));

        assertEquals(LocalDate.of(2026, 2, 28), view.getCycleEnd());
        assertEquals(LocalDate.of(2026, 2, 1), view.getCycleStart());
    }

    // ------------------------------------------------- MP_V5 the billed month

    @Test
    void aPurchaseIsBilledInTheMonthOfItsCycleEndPlusTheGracePeriod() {
        CreditCard card = card("HDFC Millennia", 5, 20);
        purchase(card, "2026-10-20", 3000);

        CreditCardMonthView view = service.month("2026-11");
        CreditCardView cardView = only(view);

        // 20 October is in the cycle ending 5 November; 5 November + 20 days = 25 November.
        CreditCardPurchaseView row = cardView.getPurchases().get(0);
        assertEquals(LocalDate.of(2026, 11, 1), row.getBillMonth());
        assertTrue(row.isInSelectedMonth());
        assertFalse(row.isInNextMonth());
        assertTrue(row.isNextDisabled(), "the current month is the only option");

        assertEquals(3000.0, cardView.getAmount());
        assertEquals(3000.0, cardView.getSelectedBillTotal());
        assertEquals(0.0, cardView.getNextBillTotal());
        assertEquals(3000.0, view.getTotal());
        assertEquals(3000.0, view.getSelectedBillTotal());

        assertEquals(3000.0, service.monthTotal(owner.getId(), NOVEMBER));
        assertEquals(0.0, service.monthTotal(owner.getId(), DECEMBER));
    }

    @Test
    void aLongerGracePeriodMovesTheBillToTheNextMonth() {
        CreditCard card = card("HDFC Millennia", 5, 40);
        purchase(card, "2026-10-20", 3000);

        CreditCardMonthView view = service.month("2026-11");
        CreditCardPurchaseView row = only(view).getPurchases().get(0);

        // 5 November + 40 days = 15 December.
        assertEquals(DECEMBER, row.getBillMonth());
        assertFalse(row.isInSelectedMonth());
        assertTrue(row.isInNextMonth());
        assertFalse(row.isNextDisabled(), "it may be pulled back to November");

        assertEquals(3000.0, view.getNextBillTotal());
        assertEquals(0.0, view.getSelectedBillTotal());
        assertEquals(0.0, only(view).getAmount(), "nothing is billed in November");
        assertEquals(0.0, service.monthTotal(owner.getId(), NOVEMBER));
        assertEquals(3000.0, service.monthTotal(owner.getId(), DECEMBER));
    }

    @Test
    void onlyTheCurrentAndNextMonthsTransactionsAreListed() {
        CreditCard card = card("HDFC Millennia", 5, 10);
        purchase(card, "2026-08-01", 100);  // billed in August - outside the window
        purchase(card, "2026-09-20", 200);  // billed in October - still removable
        purchase(card, "2026-10-20", 300);  // billed in November
        purchase(card, "2026-11-14", 400);  // billed in December

        CreditCardMonthView view = service.month("2026-11");

        List<CreditCardPurchaseView> rows = only(view).getPurchases();
        assertEquals(3, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.getBillMonth().equals(NOVEMBER)));
        assertTrue(rows.stream().anyMatch(row -> row.getBillMonth().equals(DECEMBER)));

        // The October row is listed (deletable) but not in either month total.
        assertFalse(rows.stream().anyMatch(row -> row.getBillMonth()
                .equals(LocalDate.of(2026, 10, 1)) && row.isInSelectedMonth()));

        assertEquals(300.0, only(view).getSelectedBillTotal());
        assertEquals(400.0, only(view).getNextBillTotal());
        assertEquals(300.0, service.monthTotal(owner.getId(), NOVEMBER));
        assertEquals(400.0, service.monthTotal(owner.getId(), DECEMBER));
        assertEquals(200.0, service.monthTotal(owner.getId(), LocalDate.of(2026, 10, 1)));
    }

    // --------------------------------------------------- MP_V5 save the bills

    @Test
    void savingTransactionsMovesTheBillToTheChosenMonth() {
        CreditCard card = card("HDFC Millennia", 5, 40);
        CreditCardPurchase purchase = purchase(card, "2026-10-20", 3000);
        service.saveTransactions(null, request(move(purchase.getId(), "2026-11")));

        assertEquals(3000.0, service.monthTotal(owner.getId(), NOVEMBER));
        assertEquals(0.0, service.monthTotal(owner.getId(), DECEMBER));

        CreditCardPurchaseView row = only(service.month("2026-11")).getPurchases().get(0);
        assertTrue(row.isInSelectedMonth());
        assertFalse(row.isInNextMonth());
    }

    @Test
    void savingTransactionsRejectsAMonthOtherThanTheCurrentOrNextOne() {
        CreditCard card = card("HDFC Millennia", 5, 15);
        CreditCardPurchase purchase = purchase(card, "2026-10-20", 3000);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveTransactions(null, request(move(purchase.getId(), "2026-09"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(null, purchase.getBillMonth(), "the rejected change is not stored");
    }

    @Test
    void aPurchaseOfAnotherClientIsNeverSaved() {
        CreditCard foreignCard = new CreditCard();
        foreignCard.setId(42L);
        Client other = new Client();
        other.setId(99L);
        foreignCard.setClient(other);
        foreignCard.setBillingCycleDay(5);

        CreditCardPurchase foreign = new CreditCardPurchase();
        foreign.setId(77L);
        foreign.setCard(foreignCard);
        foreign.setPurchaseDate(LocalDate.of(2026, 10, 20));
        foreign.setAmount(100);
        purchases.add(foreign);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveTransactions(null, request(move(77L, "2026-11"))));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    // ------------------------------------------------ MP_V5 removal window

    @Test
    void onlyTheCurrentAndThePreviousMonthCanBeRemoved() {
        CreditCard card = card("HDFC Millennia", 5, 0);
        CreditCardPurchase september = purchase(card, "2026-09-10", 100);
        september.setBillMonth(LocalDate.of(2026, 9, 1));
        CreditCardPurchase october = purchase(card, "2026-10-10", 200);
        october.setBillMonth(LocalDate.of(2026, 10, 1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deletePurchase(september.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(2, purchases.size());

        service.deletePurchase(october.getId());
        assertEquals(1, purchases.size());
    }

    // ------------------------------------------------------------- writing

    @Test
    void aPurchaseIsStoredWithTheMonthItsCycleAndGraceDecide() {
        CreditCard card = card("HDFC Millennia", 5, 40);

        CreditCardPurchaseView row = service.addPurchase(card.getId(),
                purchaseRequest("2026-10-20", 750));

        assertEquals(LocalDate.of(2026, 12, 1), row.getBillMonth());
        assertEquals(750.0, service.monthTotal(owner.getId(), DECEMBER));
        assertEquals(0.0, service.monthTotal(owner.getId(), NOVEMBER));
    }

    @Test
    void aCardNeedsANameACycleDayAndAGracePeriod() {
        CreditCardRequest noName = new CreditCardRequest();
        noName.setName("  ");
        noName.setBillingCycleDay(5);
        noName.setGracePeriodDays(20);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(noName)).getStatusCode());

        CreditCardRequest noCycle = new CreditCardRequest();
        noCycle.setName("HDFC");
        noCycle.setGracePeriodDays(20);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(noCycle)).getStatusCode());

        CreditCardRequest noGrace = new CreditCardRequest();
        noGrace.setName("HDFC");
        noGrace.setBillingCycleDay(5);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(noGrace)).getStatusCode());

        CreditCardRequest badGrace = new CreditCardRequest();
        badGrace.setName("HDFC");
        badGrace.setBillingCycleDay(5);
        badGrace.setGracePeriodDays(61);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(badGrace)).getStatusCode());

        CreditCardRequest badDay = new CreditCardRequest();
        badDay.setName("HDFC");
        badDay.setBillingCycleDay(32);
        badDay.setGracePeriodDays(20);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(badDay)).getStatusCode());
    }

    @Test
    void aSavedCardKeepsItsGracePeriod() {
        CreditCardRequest request = new CreditCardRequest();
        request.setName("HDFC Millennia");
        request.setBillingCycleDay(5);
        request.setGracePeriodDays(25);
        service.saveCard(request);

        CreditCardView view = only(service.month("2026-11"));
        assertEquals("HDFC Millennia", view.getName());
        assertEquals(25, view.getGracePeriodDays());
    }

    @Test
    void aDuplicateCardNameIsRejected() {
        card("HDFC Millennia", 5, 20);

        CreditCardRequest duplicate = new CreditCardRequest();
        duplicate.setName("hdfc millennia");
        duplicate.setBillingCycleDay(5);
        duplicate.setGracePeriodDays(20);

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.saveCard(duplicate)).getStatusCode());
    }

    @Test
    void aPurchaseMustBePositive() {
        CreditCard card = card("HDFC Millennia", 5, 20);

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.addPurchase(card.getId(),
                        purchaseRequest("2026-11-01", 0.0))).getStatusCode());
    }

    @Test
    void monthParsingAcceptsBothShapes() {
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11"));
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11-23"));
        assertEquals(NOVEMBER, service.firstOfMonth((String) null));
        assertEquals(Optional.empty(), Optional.ofNullable(service.dateOr("", null)));
    }

    // ------------------------------------------------ MP_V6 the shown month

    @Test
    void theDefaultMonthStaysCurrentWhileACardsGraceDateIsStillAhead() {
        // Cycle day 25 without grace bills on 25 November; the clock is on the
        // 15th, so November is still being billed.
        card("HDFC Millennia", 25, 0);

        CreditCardMonthView view = service.month(null);

        assertEquals(NOVEMBER, view.getDefaultMonth());
        assertEquals(NOVEMBER, view.getSelectedMonth());
        assertEquals(DECEMBER, view.getNextMonth());
    }

    @Test
    void theDefaultMonthBecomesTheNextOneOnceEveryGraceDateHasPassed() {
        // 5 November plus three days is past on the 15th, so December is next.
        card("HDFC Millennia", 5, 0);
        card("ICICI", 5, 3);

        CreditCardMonthView view = service.month(null);

        assertEquals(DECEMBER, view.getDefaultMonth());
        assertEquals(DECEMBER, view.getSelectedMonth());
        assertEquals(LocalDate.of(2027, 1, 1), view.getNextMonth());
    }

    @Test
    void aClientWithoutCardsOpensOnTheCurrentMonth() {
        CreditCardMonthView view = service.month(null);

        assertEquals(NOVEMBER, view.getDefaultMonth());
        assertTrue(view.getCards().isEmpty());
    }

    @Test
    void aFutureMonthShowsItsOwnTwoMonths() {
        CreditCard card = card("HDFC Millennia", 5, 20);
        purchase(card, "2026-12-20", 500);   // cycle ends 5 January, plus 20 days

        CreditCardMonthView view = service.month("2027-01");
        CreditCardPurchaseView row = only(view).getPurchases().get(0);

        assertEquals(LocalDate.of(2027, 1, 1), view.getSelectedMonth());
        assertEquals(LocalDate.of(2027, 2, 1), view.getNextMonth());
        assertTrue(view.isEditable());
        assertEquals(LocalDate.of(2027, 1, 1), row.getBillMonth());
        assertTrue(row.isInSelectedMonth());
        assertEquals(500.0, only(view).getSelectedBillTotal());
        assertEquals(500.0, view.getSelectedBillTotal());
        assertEquals(500.0, service.monthTotal(owner.getId(), LocalDate.of(2027, 1, 1)));
    }

    @Test
    void aFutureMonthMovesATransactionOnlyWithinItsOwnPair() {
        CreditCard card = card("HDFC Millennia", 5, 20);
        CreditCardPurchase purchase = purchase(card, "2026-12-20", 500);

        service.saveTransactions("2027-01", request(move(purchase.getId(), "2027-02")));
        assertEquals(500.0, service.monthTotal(owner.getId(), LocalDate.of(2027, 2, 1)));
        assertEquals(0.0, service.monthTotal(owner.getId(), LocalDate.of(2027, 1, 1)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveTransactions("2027-01",
                        request(move(purchase.getId(), "2027-03"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void aPastMonthIsReadOnlyAndKeepsItsOwnFigures() {
        CreditCard card = card("HDFC Millennia", 5, 0);
        purchase(card, "2026-09-20", 300);   // billed in October

        CreditCardMonthView view = service.month("2026-10");
        CreditCardView cardView = only(view);

        assertFalse(view.isEditable());
        assertEquals(LocalDate.of(2026, 10, 1), view.getSelectedMonth());
        assertEquals(300.0, cardView.getSelectedBillTotal());
        assertEquals(300.0, view.getSelectedBillTotal());
        assertTrue(cardView.getPurchases().get(0).isInSelectedMonth());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveTransactions("2026-10", request(move(1L, "2026-11"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    // ------------------------------- MP_V7 grace starts after the statement

    @Test
    void aPurchaseWhoseGraceRunsPastTheMonthEndIsBilledInTheMonthAfter() {
        // HDFC Millennia: the cycle ends on the 16th with 15 grace days. A
        // purchase on 25 November is in the cycle 17 November - 16 December, and
        // the grace runs from 17 December, so the amount is due on 1 January.
        CreditCard card = card("HDFC Millennia", 16, 15);

        CreditCardPurchaseView row = service.addPurchase(card.getId(),
                purchaseRequest("2026-11-25", 1200));

        assertEquals(LocalDate.of(2027, 1, 1), row.getBillMonth());
        assertEquals(1200.0, service.monthTotal(owner.getId(), LocalDate.of(2027, 1, 1)));
        assertEquals(0.0, service.monthTotal(owner.getId(), DECEMBER));
        assertEquals(0.0, service.monthTotal(owner.getId(), NOVEMBER));
    }

    @Test
    void onceNovembersBillingIsOverTheScreenOpensOnDecember() {
        // 25 November: the 16 November cycle of the HDFC card has ended, the
        // 5 November cycle of the other one is past its due date.
        CreditCard hdfc = card("HDFC Millennia", 16, 15);
        card("ICICI", 5, 0);
        purchase(hdfc, "2026-11-25", 1200);

        CreditCardMonthView view = serviceAt(LocalDate.of(2026, 11, 25)).month(null);

        assertEquals(DECEMBER, view.getDefaultMonth());
        assertEquals(DECEMBER, view.getSelectedMonth());
        assertEquals(LocalDate.of(2027, 1, 1), view.getNextMonth());

        // The purchase is billed in January, but the screen still opens on
        // December and lists it as the next month's row.
        CreditCardView hdfcView = view.getCards().stream()
                .filter(c -> "HDFC Millennia".equals(c.getName()))
                .findFirst().orElseThrow();
        CreditCardPurchaseView row = hdfcView.getPurchases().get(0);
        assertEquals(LocalDate.of(2027, 1, 1), row.getBillMonth());
        assertFalse(row.isInSelectedMonth());
        assertTrue(row.isInNextMonth());
        assertFalse(row.isNextDisabled(), "December can still be chosen by hand");
        assertEquals(1200.0, view.getNextBillTotal());
        assertEquals(0.0, view.getSelectedBillTotal());
    }

    @Test
    void theDefaultMonthNeverJumpsTwoMonthsAhead() {
        // A 60 day grace period would put a purchase today into February, but the
        // entry screen still opens on December.
        card("HDFC Millennia", 16, 60);

        CreditCardMonthView view = serviceAt(LocalDate.of(2026, 11, 25)).month(null);

        assertEquals(DECEMBER, view.getDefaultMonth());
        assertEquals(DECEMBER, view.getSelectedMonth());
    }

    @Test
    void novemberIsStillTheDefaultWhileItsCycleAndDueDateAreAhead() {
        // 10 November: the cycle ends on the 16th, so the November bill is open.
        card("HDFC Millennia", 16, 15);

        CreditCardMonthView view = serviceAt(LocalDate.of(2026, 11, 10)).month(null);

        assertEquals(NOVEMBER, view.getDefaultMonth());
        assertEquals(NOVEMBER, view.getSelectedMonth());
    }

    private static List<CreditCardTransactionRequest> request(CreditCardTransactionRequest... rows) {
        return new ArrayList<>(List.of(rows));
    }
}
