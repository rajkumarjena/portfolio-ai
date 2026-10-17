Bonds business process and layout modifications
A. view bond page
1.  add another 4 attribute called 'Total Investment Amount', 'Maturity Amount', creditRating with existing attributes both for Active and Expired/Mature bonds panel.
2. The 'Total Investment Amount' value will be calculated as  investment_Amount_Per_Unit multipy with units from bond table for that bond.
3. Similarlly Maturity Amount is Sum of all payout and amount from bond_cash_flow table for that bond id.
4. currently view page shows issuer id instead issuer name, hence map it with application param instead new database query. appParamController will have value of both issuer id and issuer name which is param_name and param_value from app_param_config table.
5. disable delete button for all user except admin. supervisor can edit the bond details from view bond page which goes to bond entry screen. Add another button called view, on click the view button the control will go to bond entry page but all fields will be disable there, it will be only view purpose only.
6. Any normal user can have view access, supervisor can have view and edit access and admin can have all three access like view, edit and delete from view bond page.

Bond Returns Page
1. In bond returns page, add another column called issuer
2. with existing filter criteria, add filter criteria for bond ref and new attribute issuer. Issuer id can be found from Bond table and its name can be derived from applicaiton param name and value.
3. Currently Bond Returns page shows all records irrespective of his own or others, hence display only logged in client/users bond's report only.
