modification to the View Bond and Bond Returns page
1. in BONDBP_v1.md only point 1 is implemented correctly.
2. regarding point 2 in BONDBP_v1.md, you have added client's pan in table however its header is missing, can you add its corresponding header column name as  'Client Name' in tables below. instead of just PAN for client name, can you combine name and pan under 'client name' like 'Raj Kumar Jena [AIGPR6090R]' i.e name[pan] from client table.
3. Point 3 is completely missed, similar suggesstion given in below link for multiselct as example. can you add multiselect filter in 'View Bonds' page at top right corner and add multiselect dropdown in the filter section of 'Bond Returns' page.
4. Multi Select dropdown not available for client with 'USER' role else its available for other roles like admin and supervisor role.
5. Purpose of multiselect dropdown is , when logged in client with supervisor or admin role, he or she can select more than one client (the multiselect option value should be look like name[pan]) from the dropdown.
6. As many number of client selected in multi-select-dropdown then that many client's 'View Bonds' or 'Bond Returns' records should displayed in it's table below. Other behaviour of the page should be same as before except changes suggested in above point 2 and 3.

https://stackoverflow.com/questions/54168972/how-to-implement-multi-select-drop-down-with-checkbox-in-thymeleaf-and-display-s
