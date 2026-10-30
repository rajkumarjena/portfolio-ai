1. please display the 'Transactions' in a tabular way with vertical scroll bar in 'Mutual Fund Portfolio Details' -> 'Mutual Fund Portfolio Details' screen as and when there are huge records, the screen getting vertically long which causing issue to go to last end of the page.
2. its better not show 'STAMP_DUTY_TAX' transaction type row in Transactions report, its the stamp duty amount deducted from sip amount amount so better add the stamp duty amount to its 'PURCHASE_SIP' type transaction amount. Logic for this to identity which Transaction type 'STAMP_DUTY_TAX' record belog to which 'PURCHASE_SIP' transaction record as below
   a. The Transaction record with 'STAMP_DUTY_TAX' type and 'PURCHASE_SIP' should be on same date.
   b. The Transaction record with 'STAMP_DUTY_TAX' type and 'PURCHASE_SIP' should be on same scheme.
3. Example for above point 2 as below
   Date	Scheme	Type	Description	Units	NAV	Amount (₹)	Balance
   2026-10-07	166IBDGG-quant Small Cap Fund - Direct Plan	STAMP_DUTY_TAX	*** Stamp Duty ***	—	—	0.22	—
   2026-10-07	166IBDGG-quant Small Cap Fund - Direct Plan	PURCHASE_SIP	Systematic Investment (3/13)	14.504	310.25	4,499.78	144.077

both the record you see in above are of same date, same scheme, so in this case display only below record with adding amount field of both the record, so updated record which should be displayed as below
2026-10-07	166IBDGG-quant Small Cap Fund - Direct Plan	PURCHASE_SIP	Systematic Investment (3/13)	14.504	310.25	4,500.00	144.077

4. pleaes apply above rules for 'Mutual Fund Portfolio - All Transactions' page to show only 50 records at a time with pagination concept, also records to be in tabular form where user need to scroll the vertical bar of the table to see all records. also avoid showing 'STAMP_DUTY_TAX' transaction type and add its amount to 'PURCHASE_SIP' type transaction amount with rule mentioned in above point 2 and point 3.