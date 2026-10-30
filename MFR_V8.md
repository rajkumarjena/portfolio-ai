1. re-design 'Mutual Fund Portfolios', display all the records from mf_portfolio_summary and at the end display its total.
2. Also, when user click any record (row) in 'Mutual Fund Summary' report, the details page of the mutual fund should be displayed, please design new page for it where the selected Mutual Fund's portfolio schemes should be displayed in one table and it's corresponding transactions should be in another table i.e, the details page should contain two table, one is for mf_folio_schemes and another is for mf_folio_scheme_transactions
3. basically below query's report i want, lets design the summary and details report page, then we will improvise it in next version.

select datetime(txn_date / 1000, 'unixepoch') AS txn_date_dt,
* from mf_folio_scheme_transactions where scheme_id in (
  select id from mf_folio_schemes where folio_id in (
  select id from mf_folios where amc_id in (
  select amc_id from mf_portfolio_summary where amc_id  in (select id from mf_amc where name = 'Quant MF')
  )
  )
  ) order by txn_date desc;