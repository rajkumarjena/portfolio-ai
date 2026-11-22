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
 * MPC_V1 / MP_V5 - credit cards, all rules server-side:
 *
 *  #1  A card is a name, the day its billing cycle ends and a grace period; the
 *      cycle of a month runs from the day after the previous cycle ended to
 *      that day.
 *  #2  Every purchase is billed in one month: the month of its cycle end plus
 *      the card's grace period (MP_V5 #3.ii). The client may move it between the
 *      current and the next month.
 *  #3  The Monthly Planner plans, for a month, the sum of every transaction
 *      billed in that month (MP_V5 #5/#6).
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
    private final CreditCardPurchaseRepository purchaseRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public CreditCardServiceImpl(CreditCardRepository cardRepository,
                                 CreditCardPurchaseRepository purchaseRepository,
                                 CurrentUserService currentUserService,
                                 Clock clock) {
        this.cardRepository = cardRepository;
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
        LocalDate current = currentMonth();
        LocalDate next = current.plusMonths(1);

        CreditCardMonthView view = new CreditCardMonthView();
        view.setMonth(month);
        view.setMonthLabel(month.format(MONTH_LABEL));
        view.setCurrentMonth(current);
        view.setCurrentMonthLabel(current.format(MONTH_LABEL));
        view.setNextMonth(next);
        view.setNextMonthLabel(next.format(MONTH_LABEL));

        double total = 0;
        double currentTotal = 0;
        double nextTotal = 0;
        for (CreditCard card : cardRepository.findByClientIdOrderByNameAsc(me.getId())) {
            CreditCardView cardView = viewOf(card, month, current, next);
            view.getCards().add(cardView);
            total += cardView.getAmount();
            currentTotal += cardView.getCurrentBillTotal();
            nextTotal += cardView.getNextBillTotal();
        }
        view.setTotal(round(total));
        view.setCurrentBillTotal(round(currentTotal));
        view.setNextBillTotal(round(nextTotal));
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public double monthTotal(Long clientId, LocalDate month) {
        LocalDate monthStart = month == null ? currentMonth() : month.withDayOfMonth(1);
        double total = 0;
        for (CreditCard card : cardRepository.findByClientIdOrderByNameAsc(clientId)) {
            for (CreditCardPurchase purchase : purchasesOf(card)) {
                if (monthStart.equals(effectiveBillMonth(card, purchase))) {
                    total += purchase.getAmount();
                }
            }
        }
        return round(total);
    }

    /** One card's side of the popup and what it plans in the planner month. */
    private CreditCardView viewOf(CreditCard card, LocalDate month,
                                  LocalDate current, LocalDate next) {
        LocalDate cycleEnd = CreditCardCycle.endOf(card.getBillingCycleDay(), month);
        LocalDate cycleStart = CreditCardCycle.startOf(card.getBillingCycleDay(), cycleEnd);

        CreditCardView view = new CreditCardView();
        view.setId(card.getId());
        view.setName(card.getName());
        view.setBillingCycleDay(card.getBillingCycleDay());
        view.setGracePeriodDays(graceOf(card));
        view.setNote(card.getNote());
        view.setCycleStart(cycleStart);
        view.setCycleEnd(cycleEnd);
        view.setCycleLabel(cycleStart.format(DATE_LABEL) + " - " + cycleEnd.format(DATE_LABEL));

        // MP_V5 #3: the screen lists the transactions that can be billed in the
        // current or the next month, plus the previous month's (still removable).
        LocalDate oldestListed = current.minusMonths(
                CreditCardConstants.MONTHS_BEFORE_CURRENT_REMOVABLE);

        double planTotal = 0;
        double currentTotal = 0;
        double nextTotal = 0;
        for (CreditCardPurchase purchase : purchasesOf(card)) {
            LocalDate billMonth = effectiveBillMonth(card, purchase);
            if (month.equals(billMonth)) {
                planTotal += purchase.getAmount();
            }
            if (current.equals(billMonth)) {
                currentTotal += purchase.getAmount();
            }
            if (next.equals(billMonth)) {
                nextTotal += purchase.getAmount();
            }
            if (!billMonth.isBefore(oldestListed) && !billMonth.isAfter(next)) {
                view.getPurchases().add(purchaseView(card, purchase, billMonth, current, next));
            }
        }
        view.setPurchaseCount(view.getPurchases().size());
        view.setAmount(round(planTotal));
        view.setCurrentBillTotal(round(currentTotal));
        view.setNextBillTotal(round(nextTotal));
        return view;
    }

    private CreditCardPurchaseView purchaseView(CreditCard card, CreditCardPurchase purchase,
                                                LocalDate billMonth, LocalDate current,
                                                LocalDate next) {
        CreditCardPurchaseView view = new CreditCardPurchaseView();
        view.setId(purchase.getId());
        view.setPurchaseDate(purchase.getPurchaseDate());
        view.setDescription(purchase.getDescription());
        view.setAmount(round(purchase.getAmount()));
        view.setBillMonth(billMonth);
        view.setCurrentSelected(current.equals(billMonth));
        view.setNextSelected(next.equals(billMonth));
        // MP_V5 #3.ii: only a cycle end (plus grace) that lands in the next
        // month may be pushed there; otherwise the current month is fixed.
        view.setNextDisabled(!derivedBillMonth(card, purchase).isAfter(current));
        view.setRemovable(!billMonth.isBefore(current.minusMonths(
                CreditCardConstants.MONTHS_BEFORE_CURRENT_REMOVABLE)));
        return view;
    }

    private List<CreditCardPurchase> purchasesOf(CreditCard card) {
        return purchaseRepository.findByCardIdOrderByPurchaseDateAsc(card.getId());
    }

    // ------------------------------------------------------------ bill months

    /** MP_V5 #3: the month stored on the transaction, else the derived one. */
    private LocalDate effectiveBillMonth(CreditCard card, CreditCardPurchase purchase) {
        LocalDate stored = purchase.getBillMonth();
        return stored != null ? stored.withDayOfMonth(1) : derivedBillMonth(card, purchase);
    }

    /** MP_V5 #3.ii: month of the cycle end plus the card's grace period. */
    private LocalDate derivedBillMonth(CreditCard card, CreditCardPurchase purchase) {
        LocalDate date = purchase.getPurchaseDate() == null
                ? LocalDate.now(clock) : purchase.getPurchaseDate();
        int grace = Math.max(CreditCardConstants.MIN_GRACE_DAYS, graceOf(card));
        return CreditCardCycle.endOfPurchase(card.getBillingCycleDay(), date)
                .plusDays(grace).withDayOfMonth(1);
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
        // MP_V5 #2: all three fields are required, not just the name.
        if (request.getBillingCycleDay() == null) {
            throw bad("The billing cycle is required");
        }
        if (request.getGracePeriodDays() == null) {
            throw bad("The grace period is required");
        }
        int day = request.getBillingCycleDay();
        if (day < CreditCardConstants.MIN_BILLING_DAY || day > CreditCardConstants.MAX_BILLING_DAY) {
            throw bad("The billing cycle day must be between "
                    + CreditCardConstants.MIN_BILLING_DAY + " and "
                    + CreditCardConstants.MAX_BILLING_DAY);
        }
        int grace = request.getGracePeriodDays();
        if (grace < CreditCardConstants.MIN_GRACE_DAYS
                || grace > CreditCardConstants.MAX_GRACE_DAYS) {
            throw bad("The grace period must be between "
                    + CreditCardConstants.MIN_GRACE_DAYS + " and "
                    + CreditCardConstants.MAX_GRACE_DAYS + " days");
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
        card.setGracePeriodDays(grace);
        card.setNote(trim(request.getNote(), CreditCardConstants.MAX_NOTE_LENGTH, "The note"));
        cardRepository.save(card);
    }

    @Override
    @Transactional
    public void deleteCard(Long cardId) {
        CreditCard card = requireCard(cardId);
        purchaseRepository.deleteByCardId(card.getId());
        cardRepository.delete(card);
    }

    /**
     * MP_V5 #3.iv: "Save Bill" persists the billed month of every changed
     * transaction on the whole screen. Only the current and the next month are
     * offered, so anything else is rejected.
     */
    @Override
    @Transactional
    public void saveTransactions(List<CreditCardTransactionRequest> transactions) {
        Client me = currentUserService.requireClient();
        if (transactions == null) {
            return;
        }
        LocalDate current = currentMonth();
        LocalDate next = current.plusMonths(1);
        for (CreditCardTransactionRequest item : transactions) {
            if (item == null || item.getPurchaseId() == null) {
                continue;
            }
            CreditCardPurchase purchase = purchaseRepository.findById(item.getPurchaseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "The purchase was not found"));
            if (purchase.getCard() == null || purchase.getCard().getClient() == null
                    || !me.getId().equals(purchase.getCard().getClient().getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "The purchase was not found");
            }
            LocalDate month = monthOrNull(item.getBillMonth());
            if (month == null || (!month.equals(current) && !month.equals(next))) {
                throw bad("A transaction can be billed only in "
                        + current.format(MONTH_LABEL) + " or " + next.format(MONTH_LABEL));
            }
            purchase.setBillMonth(month);
            purchaseRepository.save(purchase);
        }
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
        // MP_V5 #3.ii: the cycle end plus grace decides the billed month.
        purchase.setBillMonth(derivedBillMonth(card, purchase));
        CreditCardPurchase saved = purchaseRepository.save(purchase);
        return purchaseView(card, saved, effectiveBillMonth(card, saved),
                currentMonth(), currentMonth().plusMonths(1));
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
        // MP_V5 #3.vii: only the current and the previous month can be removed.
        LocalDate oldestRemovable = currentMonth().minusMonths(
                CreditCardConstants.MONTHS_BEFORE_CURRENT_REMOVABLE);
        if (effectiveBillMonth(purchase.getCard(), purchase).isBefore(oldestRemovable)) {
            throw bad("Transactions billed before " + oldestRemovable.format(MONTH_LABEL)
                    + " can no longer be removed");
        }
        purchaseRepository.delete(purchase);
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate currentMonth() {
        return LocalDate.now(clock).withDayOfMonth(1);
    }

    /** MP_V5: a card saved before the grace period existed has none (0 days). */
    private static int graceOf(CreditCard card) {
        return card.getGracePeriodDays() == null ? 0 : card.getGracePeriodDays();
    }

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
            return currentMonth();
        }
        LocalDate parsed = monthOrNull(raw);
        if (parsed == null) {
            throw bad("The month must be in the yyyy-MM format");
        }
        return parsed;
    }

    /** "yyyy-MM" or "yyyy-MM-dd" to the first day of its month; null when blank. */
    private LocalDate monthOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
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
