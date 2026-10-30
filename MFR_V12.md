1. The below data found in mutual fund widget is incorrect, please find the calculation and query as below

Mutual Funds
Portfolio Summary
Investment History	Self	Family
Investment	Market Value	Investment	Market Value
Current Month Investment
1 Oct 2026 - 31 Oct 2026
₹17,56,280.23	₹19,74,241.59	₹17,56,280.23	₹19,74,241.59
Current Quarter Investment
1 Oct 2026 - 31 Dec 2026
₹17,56,280.23	₹19,74,241.59	₹17,56,280.23	₹19,74,241.59
Current Year Investment
1 Apr 2026 - 31 Mar 2027
₹20,31,070.72	₹33,54,575.2	₹20,31,070.72	₹33,54,575.2
Last Year Investment
1 Apr 2025 - 31 Mar 2026
₹3,94,458.29	₹13,51,908	₹3,94,458.29	₹13,51,908
Total Capital Invested and Return
₹32,94,237.3	₹53,00,593.42	₹32,94,237.3	₹53,00,593.42

2. for monthly data calcualtion as below, the investment value should be 40500.0, below is it query.
   --For October Investment is
   SELECT sum(amount) as investment
   FROM mf_folio_scheme_transactions
   WHERE txn_date >= unixepoch('2026-10-01') * 1000
   and  txn_date <= unixepoch('2026-10-31') * 1000
   and owner_pan ='AIGPR6090R'
   and "type" != 'ADJUSTED';  -- => 40500.0 as investment

For monthly data, the market value should be : 39416.51 (rounding to 2 decimal), below is its query.

SELECT SUM(units * nav)
FROM (
SELECT
COALESCE(SUM(tran.units), 0) AS units,
tran.scheme_id,
MAX(val.nav) AS nav
FROM mf_folio_scheme_transactions tran
JOIN mf_folio_schemes_valuation val
ON tran.scheme_id = val.scheme_id
WHERE tran.txn_date >= unixepoch('2026-10-01') * 1000
AND tran.txn_date <= unixepoch('2026-10-31') * 1000
AND tran.type != 'ADJUSTED'
GROUP BY tran.scheme_id
) t;

3.  aboave point 2 explanation is for self calculation, please do similar calculation for family.

4. As i explained in above do the similar calculation for quarterly, yearly, last year for self and family.

5. For 'Total Capital Invested and Return' in widget, get the data form mf_portfolio_summary where is_total is 1, you can get cost_value as investment  and same way market value as market_value.
6. for any row, if investment value is greater than market value then turn market value into red font.
7. dont count ''ADJUSTED'' record in transaction table for monthly, quarterly, current year or last year record as per explain and query provided in above point 2.
