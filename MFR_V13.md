1. i am passing below delete query under 'Database Query' -> 'Database query' under 'Administration', its not deleting records from the table, i think same works for drop as well. 
2. Queries are DELETE FROM mf_amc where 1 = 1;
   DELETE FROM mf_folio_scheme_transactions where 1 = 1;
   DELETE FROM mf_folio_schemes where 1 = 1;
   DELETE FROM mf_folio_schemes_nominees where 1 = 1;
   DELETE FROM mf_folio_schemes_valuation where 1 = 1;
   DELETE FROM mf_folios where 1 = 1;
   DELETE FROM mf_import_batch where 1 = 1;
   DELETE FROM mf_portfolio_summary where 1 = 1;
   DELETE FROM mf_summary_reconciliation where 1 = 1;
   DELETE FROM mf_tmp_folio_scheme_transactions where 1 = 1;
   DELETE FROM mf_tmp_folio_schemes where 1 = 1;
   DELETE FROM mf_tmp_folio_schemes_nominees where 1 = 1;
   DELETE FROM mf_tmp_folio_schemes_valuation where 1 = 1;
   DELETE FROM mf_tmp_folios where 1 = 1;
   DELETE FROM mf_tmp_portfolio_summary where 1 = 1;
3. i am passing all above delete queries (with option DML) at a time in the given multi text box, when clicking it, it gives me answer as "Status: Success - 0 row(s) affected." also records in the table are not deleting.
4. please ensure the queries are executed else if any error it shold show in screen.