1. create a module like bond or mutual fund called "Monthly Planner"
2. the navigation panel should be covered for earnings and spending like as below 
    Monthly Planner
      Earnings
        Salary
        Freelance Payouts
        Side-Hustle Incomes
      Spending(Obligations)
        Buying Shares
        Buying Bonds
        PPF
        EPF, NPS
        Small-case
        Credit Card Bills
        LIC Insurance Premium
        Home Loan
        Fuel and Transportation
        Bond SIP
        Mutual Fund SIP
      Cash-Flow Report ( this is a report page where we can see all the earnings and expenses for the current month and next month for planning)
3. Please design screens for above earnings and spending input data screen judiciously so that with minimal UI design we can captures all the above monthly income and expense data in more organised way
4. Note Some earning like Salary comes every month, so user dont need to entry it every month unless there is change in it due to hike, so it should be auto populated to next month and so on in input screen however freelance payout or Side-Hustle income need to input in every month in order to count it as income, if user dont input it then it will be defaulted to zero. may be you can give a check-box against any field if its a repeating monthly/weekly/yearly etc, accordingly it will be autopopulated to the curresponding period of the monthly planner.
4. Similarlly Expenses like credit card bills, Fuel and Transportation, Buying Shares, bonds, PPF, EPF, NPS, Small Case are every month user need to  input it in order to count it for monthly expendature
5. But Home Loan EMI, Bond SIP should be auto populated to Monthly Planner from Bond SIP section or Mutual Fund SIP section, any adjustment required in this two item, user should go to its corresponding BOnd or Mutual fund sip screen and input it's data in order make it available in monthly planner screen
6. Please design above User Interfaces, Tree Navigations in order to nagigates to earning or spending UIs
7. Please follow existing architecture like module wise design, flow, entity or service class design or call to database. Write optimised code, no hard coding, properties should be maintain in resource file. constants should be maintained in modules's constants java class.




