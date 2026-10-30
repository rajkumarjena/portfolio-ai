1. The 'All Transactions' button is not working in 'Mutual Fund Portfolios' page, please fix this, when user click the button, it should navigate to All mutual Fund transactions on any scheme, i.e all records from table mf_folio_scheme_transactions
2. By default only show 50 records in a table, when user scrowl down, further records should be displayed in all transaction page, i.e. please apply pagination here.
3. Please add filter on 'Mutual Fund Portfolios', the filters are like date range, AMC., The intention of date range filter is to check between some date how much investment (cost value) and how much is the market value of mutual funds.
4. also in details page, please add date range filter, scheme name level filter so that client can check between dates how much was the investment (cost value) and how much is the market value of that date range.
5. between the date range, the total cost and market value can be derived from mf_folio_scheme_transactions table. The total cost is sum of all amounts during that date range and total market value is sum of units between that date range multipy with most recent nav value of that date range for that scheme or those schemes.
6. Ensure all the report put it in tabular form in those mutual fund report for easy to validate using pagination concept.
7. Please add date range and scheme filter on top of mutual fund details report in order to filter and validate the report.
8. got below application error i.e. header is missing. actually we did re-enginerred the code also removed old features and tables thats why its failing. looks the table is not required, please validate and fix it 

Client backfill skipped a table: StatementCallback; uncategorized SQLException for SQL [UPDATE mf_header SET client_id = (SELECT MIN(id) FROM client)
WHERE client_id IS NULL]; SQL state [null]; error code [1]; [SQLITE_ERROR] SQL error or missing database (no such table: mf_header)
2026-10-29T23:10:39.202+05:30  INFO 32936 --- [portfolio] [nio-8082-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]  