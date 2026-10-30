1. We need to redesign Upload Mutual funds report section('Mutual funds' -> 'Import Mutual Fund From MF Central') to import mutual fund report from KFINTECH of the logged in client.
2. Currently we are uploading pdf file which does not need password to open the PDF file, please add a text box where user can provide password to open the protected PDF file which he or she is uploading her mutual fund report.
3. If user not supplied the password but the mutual fund pdf file needs password to open it then give an error in screen that 'Mutual Fund report is password protected, please fill the password then submit the 'Upload and Reconcile' button'
4. Please study the sample KFINKART Mutual Fund report provided here, its name is '102735162510202607363428594863322076.pdf' which converts to JSON as 'response_json.json' both the file provided you for your reference.
5. Create the entity class matching to the sample json provided for the uploaded pdf file mentioned in above poirnt-4.
6. Apply all the database normalization rules to manage the data integrity in order to capture the PDF FILE data in accurately which is represented in provided JSON file in form of entity.
7. The tables to be created for managing mutual funds as below also you can create if any new table requirement there too. Table names as below
   a. mf_portfolio_summary
   b. mf_folios
   c. mf_folio_schemes
   d. mf_folio_schemes_nominees
   e. mf_folio_schemes_valuation
   g. mf_folio_scheme_transactions
8. Please study the attribute names for tables mentioned in above point-7 from the json provided (response_json.json) here also for summary, kfintech mutual fund has a summary section, so summary tables attribute can be taken from there.
9. Create two set of tables for point-7, one is temporary tables which will hold the data for every successful uploaded client's mutual fund PDF file. if the kfintech pdf file not uploaded properlly then last successful upload datafile should not be flushed from temporary table. so ensure if system able to read the client's kfintech mutual fund report successfully then only purge all data from temporary tables and insert fresh data into it from the pdf file.
10. Currently KFINTECH Mutual Fund pdf file can have transaction report upto last 5 years data, hence the summary amount might not matched to the total of all transaction amout for each scheme as well as at each amc level. So First time when user uploaded the kfintech mutual fund pdf file, calculate the difference amount on each scheme and amc level, make an entry into the transaction table  as adjusted amount ("type": "ADJUSTED") to match the summary table's cost and market value at each AMC level or Scheme Level. example of adjusted record for your transaction table as below, replace with actual calculated values where i embeded in <>

            {
              "date": "2022-11-25",
              "description": "ADJUSTED UNIT, AMOUNT TRANSACTION",
              "amount": <ADJUSTED AMOUNT>,
              "units": <ADJUSTED UNITS>,
              "nav": "72.13",
              "balance": <ADJUSTED UNIT BALANCE>,,
              "type": "ADJUSTED",
              "dividend_rate": null,
              "gift_folio": null
            }

11. Note- the mutual fund summary in the pdf Report always holds current total of the users investment which is cost value and market value.
12. For reconcilation process, delete all the transactions records which are present in permanent table from temporary table then Compare the temporary table values against the permanent table for reconcilation, prepare a reconcilation report for below areas to validate, please follow an universal best and easy and user friendly way to show the difference in reconcilation report before user accept the reconcilation to make changes into permanent tables. The reconcilation reports as
    a. Report 1: how may new transactions came in in new upload compared to last upload. For new transaction records on each scheme compared to permanent table which in new report should be in green color grid for easy identification in reconcilation report.
    b. Report 2 : Summary records on each amc in temporary table should match with sum of cost and market value from both temporary and permanent transaction table along with adjusted amount. adjusted amount explained in next point.
    c. Report 3: There might be scenarion user first time upload last 1 year data but on next upload he uploaded last 3 year data so in this scenario when user gets older transactions then the adjustment amount should be recalculated also should be highliged in reconcilation report in different color.
    d. Report 4: The reconcilation report should check the date range in both permanent and temporary table for missing transactions for scenarion like  lets in permanent tables, we had earlier uploaded mutual fund report where last transaction date recorded in permanent table is 25th Sept 2026. again we are uploading new report where a new transaction came in for past date lets say 24th August 2026 which is not there in permanent table, then this report should show this record in red color in order to identify it and reason to know why it was missing.
    e. total units, cost and market value from transaction table should match with total cost in summary table. Sometime due to decimal values the total units, cost and market values  might not match at 100%, so set an tollerance % in properties file for total units, total cost and total market value in order to match it to 100% and see the reconcilation green. ent report.
13. Re-design existing 'Validate & Sync Mutual Fund (Reconciliation Report)' page for above reconcilation.
14. Since we are redesigning the Mutual Fund Section, so keep all MUTUAL FUNDS UIs and remove all mutual fund backend process, java classes and entities which are not required, we can build backend process for other UI which might fail now but we will only focus on upload mutual fund report and reconcilation report for validation and acceptance.
    write effective code on understing the both json and mutual fund pdf file.
15. dont hard code any values.
16. Keep an provision in Upload PDF file page to download the data for uploaded pdf in JSON format so as to check all records are went well there on high level before going into reconcilation report to verify and accept.

17. keep the 'Previous imports' section to show up users mutual fund file import history.