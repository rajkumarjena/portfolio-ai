package com.artech.portfolio.creditcard.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.creditcard.dto.CreditCardBillRequest;
import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseView;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;
import com.artech.portfolio.creditcard.dto.CreditCardView;
import com.artech.portfolio.creditcard.entity.CreditCard;
import com.artech.portfolio.creditcard.entity.CreditCardBill;
import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import com.artech.portfolio.creditcard.repository.CreditCardBillRepository;
import com.artech.portfolio.creditcard.repository.CreditCardPurchaseRepository;
import com.artech.portfolio.creditcard.repository.CreditCardRepository;
import com.artech.portfolio.creditcard.service.CreditCardService;
import com.artech.portfolio.creditcard.util.CreditCardConstants;
import com.artech.portfolio.creditcard.util.CreditCardCycle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * MPC_V1 - credit cards, all rules server-side:
 *
 *  #1  A card is a name plus the day its billing cycle ends; the cycle of a
 *      month runs from the day after the previous cycle ended to that day.
 *  #2  The expense of a cycle is planned in the month the cycle ends, so a 5th
 *      cycle covering 6 October - 5 November is a November expense.
 *  #3  The client enters the bill of every cycle; until it is entered the
 *      purchases captured for the cycle are what the planner plans.
 *  #4  Ownership always comes from the session, never from the popup
 *      (CONSISTENCY.md 6), and no wording or limit is hard coded (MP_V1 #7).
 */
@Service
public class CreditCardServiceImpl implements CreditCardService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CreditCardRepository cardRepository;
    private final CreditCardBillRepository billRepository;
    private final CreditCardPurchaseRepository purchaseRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public CreditCardServiceImpl(CreditCardRepository cardRepository,
                                 CreditCardBillRepository billRepository,
                                 CreditCardPurchaseRepository purchaseRepository,
                                 CurrentUserService currentUserService,
                                 Clock clock) {
        this.cardRepository = cardRepository;
        this.billRepository = billRepository;
        this.purchaseRepository = purchaseRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- reading

    @Override
    @Transactional(readOnly = true)
    public CreditCardMonthView month(String rawMonth) {
        Client me = currentUserService.requireClient();
        LocalDate month = firstOfMonth(rawMonth);
        CreditCardMonthView view = new CreditCardMonthView();
        view.setMonth(month);
        view.setMonthLabel(month.format(MONTH_LABEL));
        double total = 0;
        for (CreditCard card : cardRepository.findByClientIdOrderByNameAsc(me.getId())) {
            CreditCardView cardView = viewOf(card, month);
            view.getCards().add(cardView);
            total += cardView.getAmount();
        }
        view.setTotal(round(total));
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public double monthTotal(Long clientId, LocalDate month) {
        LocalDate first = month == null ? LocalDate.now(clock).withDayOfMonth(1)
                : month.withDayOfMonth(1);
        double total = 0;
        for (CreditCard card : cardRepository.findByClientIdOrderByNameAsc(clientId)) {
            total += cycleAmount(card, CreditCardCycle.endOf(card.getBillingCycleDay(), first));
        }
        return round(total);
    }

    /** One card's side of the popup for the cycle that ends in the month. */
    private CreditCardView viewOf(CreditCard card, LocalDate month) {
        LocalDate cycleEnd = CreditCardCycle.endOf(card.getBillingCycleDay(), month);
        LocalDate cycleStart = CreditCardCycle.startOf(card.getBillingCycleDay(), cycleEnd);

        CreditCardView view = new CreditCardView();
        view.setId(card.getId());
        view.setName(card.getName());
        view.setBillingCycleDay(card.getBillingCycleDay());
        view.setNote(card.getNote());
        view.setCycleStart(cycleStart);
        view.setCycleEnd(cycleEnd);
        view.setCycleLabel(cycleStart.format(DATE_LABEL) + " - " + cycleEnd.format(DATE_LABEL));

        double purchaseTotal = 0;
        for (CreditCardPurchase purchase : purchasesOf(card, cycleStart, cycleEnd)) {
            purchaseTotal += purchase.getAmount();
            view.getPurchases().add(purchaseView(purchase));
        }
        view.setPurchaseTotal(round(purchaseTotal));
        view.setPurchaseCount(view.getPurchases().size());

        CreditCardBill bill = billRepository
                .findByCardIdAndCycleEnd(card.getId(), cycleEnd).orElse(null);
        view.setBillEntered(bill != null);
        view.setBillAmount(bill == null ? 0 : round(bill.getAmount()));
        if (bill != null) {
            view.setAmount(round(bill.getAmount()));
            view.setAmountSource(CreditCardConstants.AMOUNT_BILL);
        } else if (purchaseTotal > 0) {
            view.setAmount(round(purchaseTotal));
            view.setAmountSource(CreditCardConstants.AMOUNT_PURCHASES);
        } else {
            view.setAmount(0);
            view.setAmountSource(CreditCardConstants.AMOUNT_NONE);
        }
        return view;
    }

    /**
     * MPC_V1 #2/#3: what a card plans in the month its cycle ends. The entered
     * bill wins - a zero bill is a real statement - and the purchases of the
     * cycle fill in until then.
     */
    private double cycleAmount(CreditCard card, LocalDate cycleEnd) {
        CreditCardBill bill = billRepository
                .findByCardIdAndCycleEnd(card.getId(), cycleEnd).orElse(null);
        if (bill != null) {
            return bill.getAmount();
        }
        double purchases = 0;
        for (CreditCardPurchase purchase : purchasesOf(card,
                CreditCardCycle.startOf(card.getBillingCycleDay(), cycleEnd), cycleEnd)) {
            purchases += purchase.getAmount();
        }
        return purchases;
    }

    private List<CreditCardPurchase> purchasesOf(CreditCard card, LocalDate from, LocalDate to) {
        return purchaseRepository.findByCardIdAndPurchaseDateBetweenOrderByPurchaseDateAsc(
                card.getId(), from, to);
    }

    private static CreditCardPurchaseView purchaseView(CreditCardPurchase purchase) {
        CreditCardPurchaseView view = new CreditCardPurchaseView();
        view.setId(purchase.getId());
        view.setPurchaseDate(purchase.getPurchaseDate());
        view.setDescription(purchase.getDescription());
        view.setAmount(round(purchase.getAmount()));
        return view;
    }

    // ---------------------------------------------------------------- writing

    @Override
    @Transactional
    public void saveCard(CreditCardRequest request) {
        Client me = currentUserService.requireClient();
        if (request == null) {
            throw bad("Card details are required");
        }
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw bad("The card name is required");
        }
        if (name.length() > CreditCardConstants.MAX_NAME_LENGTH) {
            throw bad("The card name must be at most " + CreditCardConstants.MAX_NAME_LENGTH
                    + " characters");
        }
        int day = request.getBillingCycleDay() == null
                ? CreditCardConstants.DEFAULT_BILLING_DAY : request.getBillingCycleDay();
        if (day < CreditCardConstants.MIN_BILLING_DAY || day > CreditCardConstants.MAX_BILLING_DAY) {
            throw bad("The billing cycle day must be between "
                    + CreditCardConstants.MIN_BILLING_DAY + " and "
                    + CreditCardConstants.MAX_BILLING_DAY);
        }
        CreditCard editing = request.getId() == null ? null : requireCard(request.getId());
        Long editingId = editing == null ? null : editing.getId();
        cardRepository.findByClientIdAndNameIgnoreCase(me.getId(), name)
                .filter(other -> !other.getId().equals(editingId))
                .ifPresent(other -> {
                    throw bad("A card named \"" + name + "\" already exists");
                });
        CreditCard card = editing;
        if (card == null) {
            card = new CreditCard();
            card.setClient(me); // CONSISTENCY.md 6 - session, never body
        }
        card.setName(name);
        card.setBillingCycleDay(day);
        card.setNote(trim(request.getNote(), CreditCardConstants.MAX_NOTE_LENGTH, "The note"));
        cardRepository.save(card);
    }

    @Override
    @Transactional
    public void deleteCard(Long cardId) {
        CreditCard card = requireCard(cardId);
        billRepository.deleteByCardId(card.getId());
        purchaseRepository.deleteByCardId(card.getId());
        cardRepository.delete(card);
    }

    @Override
    @Transactional
    public void saveBill(Long cardId, CreditCardBillRequest request) {
        CreditCard card = requireCard(cardId);
        if (request == null) {
            throw bad("The bill amount is required");
        }
        double amount = request.getAmount() == null ? 0 : request.getAmount();
        if (amount < 0) {
            throw bad("The bill amount cannot be negative");
        }
        LocalDate asked = dateOr(request.getCycleEnd(), null);
        if (asked == null) {
            throw bad("The billing cycle's end date is required");
        }
        // The entered date only says which cycle; the card's own cycle day decides the day.
        LocalDate cycleEnd = CreditCardCycle.endOf(card.getBillingCycleDay(), asked);
        CreditCardBill bill = billRepository
                .findByCardIdAndCycleEnd(card.getId(), cycleEnd).orElse(null);
        if (bill == null) {
            bill = new CreditCardBill();
            bill.setCard(card);
            bill.setCycleEnd(cycleEnd);
        }
        bill.setAmount(round(amount));
        bill.setNote(trim(request.getNote(), CreditCardConstants.MAX_NOTE_LENGTH, "The note"));
        billRepository.save(bill);
    }

    @Override
    @Transactional
    public CreditCardPurchaseView addPurchase(Long cardId, CreditCardPurchaseRequest request) {
        CreditCard card = requireCard(cardId);
        if (request == null) {
            throw bad("The purchase details are required");
        }
        LocalDate purchaseDate = dateOr(request.getPurchaseDate(), LocalDate.now(clock));
        double amount = request.getAmount() == null ? 0 : request.getAmount();
        if (amount <= 0) {
            throw bad("The purchase amount must be more than zero");
        }
        CreditCardPurchase purchase = new CreditCardPurchase();
        purchase.setCard(card);
        purchase.setPurchaseDate(purchaseDate);
        purchase.setAmount(round(amount));
        purchase.setDescription(trim(request.getDescription(),
                CreditCardConstants.MAX_DESCRIPTION_LENGTH, "The description"));
        return purchaseView(purchaseRepository.save(purchase));
    }

    @Override
    @Transactional
    public void deletePurchase(Long purchaseId) {
        Client me = currentUserService.requireClient();
        CreditCardPurchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "The purchase was not found"));
        if (purchase.getCard() == null || purchase.getCard().getClient() == null
                || !me.getId().equals(purchase.getCard().getClient().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The purchase was not found");
        }
        purchaseRepository.delete(purchase);
    }

    // ---------------------------------------------------------------- helpers

    /** The card of the session's client, or 404 - a foreign id is never accepted. */
    private CreditCard requireCard(Long cardId) {
        Client me = currentUserService.requireClient();
        if (cardId == null) {
            throw bad("The credit card is required");
        }
        return cardRepository.findByIdAndClientId(cardId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "The credit card was not found"));
    }

    private static String trim(String raw, int max, String what) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > max) {
            throw bad(what + " must be at most " + max + " characters");
        }
        return value.isEmpty() ? null : value;
    }

    /** The first day of the requested month ("yyyy-MM"), the current month when blank. */
    LocalDate firstOfMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(clock).withDayOfMonth(1);
        }
        String value = raw.trim();
        try {
            if (value.length() == CreditCardConstants.MONTH_PATTERN_LENGTH) {
                return YearMonth.parse(value).atDay(1);
            }
            return LocalDate.parse(value).withDayOfMonth(1);
        } catch (DateTimeParseException e) {
            throw bad("The month must be in the yyyy-MM format");
        }
    }

    /** A "yyyy-MM-dd" date, a "yyyy-MM" month (its first day) or the fallback when blank. */
    LocalDate dateOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        try {
            if (value.length() == CreditCardConstants.MONTH_PATTERN_LENGTH) {
                return YearMonth.parse(value).atDay(1);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw bad("Dates must be in the yyyy-MM-dd (or yyyy-MM) format");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
